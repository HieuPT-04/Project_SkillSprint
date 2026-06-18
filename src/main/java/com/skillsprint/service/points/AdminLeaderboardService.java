package com.skillsprint.service.points;

import com.skillsprint.dto.response.admin.AdminLeaderboardEntryResponse;
import com.skillsprint.dto.response.admin.AdminLeaderboardResponse;
import com.skillsprint.dto.response.admin.AdminPointEventResponse;
import com.skillsprint.dto.response.admin.AdminUserPointSummaryResponse;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.entity.PointEvent;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserPointSummary;
import com.skillsprint.enums.points.LeaderboardPeriod;
import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.PointEventRepository;
import com.skillsprint.repository.UserPointSummaryRepository;
import com.skillsprint.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import com.skillsprint.service.storage.S3PresignedUrlService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminLeaderboardService {

    static int MAX_PAGE_SIZE = 100;
    static ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    PointEventRepository pointEventRepository;
    UserPointSummaryRepository userPointSummaryRepository;
    UserRepository userRepository;
    S3PresignedUrlService s3PresignedUrlService;

    @Transactional(readOnly = true)
    public AdminLeaderboardResponse getLeaderboard(
            LeaderboardPeriod period,
            String search,
            int page,
            int size
    ) {
        LeaderboardPeriod normalizedPeriod = period == null ? LeaderboardPeriod.WEEKLY : period;
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        String normalizedSearch = normalizeSearch(search);

        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        LocalDate weekStart = weekStart(today);
        LocalDate monthStart = today.withDayOfMonth(1);

        Page<AdminLeaderboardEntryResponse> entries = switch (normalizedPeriod) {
            case WEEKLY -> toAdminLeaderboardPage(
                    pointEventRepository.searchWeeklyAdminLeaderboard(weekStart, normalizedSearch, pageable),
                    pageable
            );
            case MONTHLY -> toAdminLeaderboardPage(
                    pointEventRepository.searchMonthlyAdminLeaderboard(monthStart, normalizedSearch, pageable),
                    pageable
            );
            case ALL_TIME -> toAllTimeLeaderboardPage(
                    userPointSummaryRepository.searchAllTimeAdminLeaderboard(normalizedSearch, pageable),
                    pageable
            );
        };

        return AdminLeaderboardResponse.builder()
                .period(normalizedPeriod)
                .periodStart(resolvePeriodStart(normalizedPeriod, weekStart, monthStart))
                .periodEnd(resolvePeriodEnd(normalizedPeriod, weekStart, monthStart))
                .entries(PageResponse.from(entries))
                .build();
    }

    @Transactional(readOnly = true)
    public AdminUserPointSummaryResponse getUserPointSummary(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserPointSummary summary = userPointSummaryRepository.findById(userId)
                .orElseGet(() -> emptySummary(user));

        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        LocalDate weekStart = weekStart(today);
        LocalDate monthStart = today.withDayOfMonth(1);
        int weeklyPoints = safe(pointEventRepository.sumWeeklyPoints(userId, weekStart));
        int monthlyPoints = safe(pointEventRepository.sumMonthlyPoints(userId, monthStart));
        int totalPoints = safe(summary.getTotalPoints());

        return AdminUserPointSummaryResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarObjectKey(s3PresignedUrlService.createViewUrl(user.getAvatarObjectKey()))
                .totalPoints(totalPoints)
                .weeklyPoints(weeklyPoints)
                .monthlyPoints(monthlyPoints)
                .streakDays(safe(summary.getStreakDays()))
                .lastPointDate(summary.getLastPointDate())
                .weeklyRank(weeklyPoints > 0 ? safeRank(pointEventRepository.calculateWeeklyRank(weekStart, weeklyPoints)) : null)
                .monthlyRank(monthlyPoints > 0 ? safeRank(pointEventRepository.calculateMonthlyRank(monthStart, monthlyPoints)) : null)
                .allTimeRank(totalPoints > 0 ? safeRank(userPointSummaryRepository.calculateAllTimeRank(totalPoints)) : null)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPointEventResponse> getUserPointEvents(
            String userId,
            PointEventType eventType,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<AdminPointEventResponse> events = pointEventRepository
                .searchUserPointEvents(userId, eventType, from, to, pageable)
                .map(this::toAdminPointEventResponse);
        return PageResponse.from(events);
    }

    private Page<AdminLeaderboardEntryResponse> toAdminLeaderboardPage(
            Page<PointEventRepository.AdminLeaderboardRow> rows,
            Pageable pageable
    ) {
        List<PointEventRepository.AdminLeaderboardRow> content = rows.getContent();
        List<AdminLeaderboardEntryResponse> entries = IntStream.range(0, content.size())
                .mapToObj(index -> toAdminLeaderboardEntry(content.get(index), rank(pageable, index)))
                .toList();
        return new PageImpl<>(entries, pageable, rows.getTotalElements());
    }

    private Page<AdminLeaderboardEntryResponse> toAllTimeLeaderboardPage(
            Page<UserPointSummary> summaries,
            Pageable pageable
    ) {
        List<UserPointSummary> content = summaries.getContent();
        List<AdminLeaderboardEntryResponse> entries = IntStream.range(0, content.size())
                .mapToObj(index -> toAllTimeLeaderboardEntry(content.get(index), rank(pageable, index)))
                .toList();
        return new PageImpl<>(entries, pageable, summaries.getTotalElements());
    }

    private AdminLeaderboardEntryResponse toAdminLeaderboardEntry(
            PointEventRepository.AdminLeaderboardRow row,
            int rank
    ) {
        return AdminLeaderboardEntryResponse.builder()
                .rank(rank)
                .userId(row.getUserId())
                .fullName(row.getFullName())
                .email(row.getEmail())
                .avatarObjectKey(s3PresignedUrlService.createViewUrl(row.getAvatarObjectKey()))
                .points(safe(row.getPoints()))
                .streakDays(safe(row.getStreakDays()))
                .lastPointDate(row.getLastPointDate())
                .build();
    }

    private AdminLeaderboardEntryResponse toAllTimeLeaderboardEntry(UserPointSummary summary, int rank) {
        User user = summary.getUser();
        return AdminLeaderboardEntryResponse.builder()
                .rank(rank)
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarObjectKey(s3PresignedUrlService.createViewUrl(user.getAvatarObjectKey()))
                .points(safe(summary.getTotalPoints()))
                .streakDays(safe(summary.getStreakDays()))
                .lastPointDate(summary.getLastPointDate())
                .build();
    }

    private AdminPointEventResponse toAdminPointEventResponse(PointEvent event) {
        StudyWorkspace workspace = event.getWorkspace();
        return AdminPointEventResponse.builder()
                .eventType(event.getEventType())
                .sourceType(event.getSourceType())
                .sourceId(event.getSourceId())
                .points(safe(event.getPoints()))
                .description(event.getDescription())
                .workspaceId(workspace == null ? null : workspace.getWorkspaceId())
                .workspaceName(workspace == null ? null : workspace.getName())
                .eventDate(event.getEventDate())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private UserPointSummary emptySummary(User user) {
        UserPointSummary summary = new UserPointSummary();
        summary.setUser(user);
        summary.setUserId(user.getUserId());
        return summary;
    }

    private int rank(Pageable pageable, int index) {
        return (int) pageable.getOffset() + index + 1;
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return "";
        }
        return search.trim();
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate resolvePeriodStart(LeaderboardPeriod period, LocalDate weekStart, LocalDate monthStart) {
        return switch (period) {
            case WEEKLY -> weekStart;
            case MONTHLY -> monthStart;
            case ALL_TIME -> null;
        };
    }

    private LocalDate resolvePeriodEnd(LeaderboardPeriod period, LocalDate weekStart, LocalDate monthStart) {
        return switch (period) {
            case WEEKLY -> weekStart.plusDays(6);
            case MONTHLY -> monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            case ALL_TIME -> null;
        };
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private int safe(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private Integer safeRank(Long rank) {
        return rank == null ? null : rank.intValue();
    }
}
