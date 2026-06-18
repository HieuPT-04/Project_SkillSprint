package com.skillsprint.service.points;

import com.skillsprint.dto.response.points.LeaderboardEntryResponse;
import com.skillsprint.dto.response.points.LeaderboardResponse;
import com.skillsprint.dto.response.points.MyPointsResponse;
import com.skillsprint.dto.response.points.PointEventResponse;
import com.skillsprint.entity.PointEvent;
import com.skillsprint.entity.Quiz;
import com.skillsprint.entity.QuizAttempt;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserPointSummary;
import com.skillsprint.entity.UserQuizScore;
import com.skillsprint.enums.points.LeaderboardPeriod;
import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.enums.points.PointSourceType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.PointEventRepository;
import com.skillsprint.repository.UserPointSummaryRepository;
import com.skillsprint.repository.UserQuizScoreRepository;
import com.skillsprint.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.skillsprint.service.storage.S3PresignedUrlService;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PointService {

    static int ROADMAP_STEP_COMPLETED_POINTS = 120;
    static int ROADMAP_COMPLETED_POINTS = 700;
    static int QUIZ_PASSED_POINTS = 80;
    static int QUIZ_EXCELLENT_POINTS = 120;
    static ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    PointEventRepository pointEventRepository;
    UserPointSummaryRepository userPointSummaryRepository;
    UserQuizScoreRepository userQuizScoreRepository;
    UserRepository userRepository;
    S3PresignedUrlService s3PresignedUrlService;

    @Transactional
    public void awardRoadmapStepCompleted(User user, StudyWorkspace workspace, UUID stepId) {
        awardUnique(
                user,
                workspace,
                PointEventType.ROADMAP_STEP_COMPLETED,
                PointSourceType.ROADMAP_STEP,
                stepId.toString(),
                ROADMAP_STEP_COMPLETED_POINTS,
                "Hoàn thành roadmap step"
        );
    }

    @Transactional
    public void awardRoadmapCompleted(User user, StudyWorkspace workspace, UUID roadmapId) {
        awardUnique(
                user,
                workspace,
                PointEventType.ROADMAP_COMPLETED,
                PointSourceType.ROADMAP,
                roadmapId.toString(),
                ROADMAP_COMPLETED_POINTS,
                "Hoàn thành toàn bộ roadmap"
        );
    }

    @Transactional(readOnly = true)
    public boolean hasRoadmapStepCompletedPoints(String userId, UUID stepId) {
        return pointEventRepository.existsByUserUserIdAndEventTypeAndSourceTypeAndSourceId(
                userId,
                PointEventType.ROADMAP_STEP_COMPLETED,
                PointSourceType.ROADMAP_STEP,
                stepId.toString()
        );
    }

    @Transactional
    public void awardQuizScore(Quiz quiz, QuizAttempt attempt) {
        int targetPoints = resolveQuizPoints(attempt.getScore());
        if (targetPoints <= 0) {
            log.info(
                    "[POINTS] Skip quiz XP because score is below threshold user={} quiz={} attempt={} score={}",
                    quiz.getUser().getUserId(),
                    quiz.getQuizId(),
                    attempt.getAttemptId(),
                    attempt.getScore()
            );
            upsertQuizScore(quiz, attempt, 0);
            return;
        }

        UserQuizScore score = userQuizScoreRepository
                .findByUserUserIdAndQuizQuizId(quiz.getUser().getUserId(), quiz.getQuizId())
                .orElseGet(() -> newQuizScore(quiz));

        if (targetPoints <= safe(score.getEarnedPoints())) {
            log.info(
                    "[POINTS] Skip quiz XP because best earned points did not increase user={} quiz={} attempt={} score={} earnedPoints={}",
                    quiz.getUser().getUserId(),
                    quiz.getQuizId(),
                    attempt.getAttemptId(),
                    attempt.getScore(),
                    safe(score.getEarnedPoints())
            );
            updateBestAttemptIfNeeded(score, attempt, targetPoints);
            return;
        }

        int delta = targetPoints - safe(score.getEarnedPoints());
        PointEventType eventType = resolveQuizEventType(score.getEarnedPoints(), targetPoints);
        awardUnique(
                quiz.getUser(),
                quiz.getWorkspace(),
                eventType,
                PointSourceType.QUIZ,
                quiz.getQuizId().toString(),
                delta,
                "Đạt điểm quiz " + attempt.getScore() + "%"
        );

        score.setBestAttempt(attempt);
        score.setBestScorePercent(Math.max(safe(score.getBestScorePercent()), attempt.getScore()));
        score.setEarnedPoints(targetPoints);
        userQuizScoreRepository.saveAndFlush(score);
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(LeaderboardPeriod period, int size) {
        int normalizedSize = Math.max(1, Math.min(size, 100));
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        LocalDate weekStart = weekStart(today);
        LocalDate monthStart = today.withDayOfMonth(1);

        List<LeaderboardEntryResponse> entries = switch (period) {
            case WEEKLY -> toLeaderboardEntries(pointEventRepository.findWeeklyLeaderboard(
                    weekStart,
                    PageRequest.of(0, normalizedSize)
            ));
            case MONTHLY -> toLeaderboardEntries(pointEventRepository.findMonthlyLeaderboard(
                    monthStart,
                    PageRequest.of(0, normalizedSize)
            ));
            case ALL_TIME -> toSummaryLeaderboardEntries(userPointSummaryRepository.findAllTimeLeaderboard(
                    PageRequest.of(0, normalizedSize)
            ));
        };

        return LeaderboardResponse.builder()
                .period(period)
                .periodStart(resolvePeriodStart(period, weekStart, monthStart))
                .periodEnd(resolvePeriodEnd(period, weekStart, monthStart))
                .entries(entries)
                .build();
    }

    @Transactional(readOnly = true)
    public MyPointsResponse getMyPoints(String userId) {
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

        return MyPointsResponse.builder()
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
    public List<PointEventResponse> getMyPointEvents(String userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return pointEventRepository.findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toPointEventResponse)
                .toList();
    }

    private void awardUnique(
            User user,
            StudyWorkspace workspace,
            PointEventType eventType,
            PointSourceType sourceType,
            String sourceId,
            int points,
            String description
    ) {
        if (points <= 0) {
            log.info(
                    "[POINTS] Skip XP because points is not positive user={} eventType={} sourceType={} sourceId={} points={}",
                    user.getUserId(),
                    eventType,
                    sourceType,
                    sourceId,
                    points
            );
            return;
        }

        if (pointEventRepository.existsByUserUserIdAndEventTypeAndSourceTypeAndSourceId(
                user.getUserId(),
                eventType,
                sourceType,
                sourceId
        )) {
            log.info(
                    "[POINTS] Skip duplicate XP user={} eventType={} sourceType={} sourceId={}",
                    user.getUserId(),
                    eventType,
                    sourceType,
                    sourceId
            );
            return;
        }

        LocalDate today = LocalDate.now(DEFAULT_ZONE);

        PointEvent event = new PointEvent();
        event.setUser(user);
        event.setWorkspace(workspace);
        event.setEventType(eventType);
        event.setSourceType(sourceType);
        event.setSourceId(sourceId);
        event.setPoints(points);
        event.setDescription(description);
        event.setEventDate(today);
        event.setWeekStartDate(weekStart(today));
        event.setMonthStartDate(today.withDayOfMonth(1));

        try {
            pointEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ignored) {
            log.info(
                    "[POINTS] Skip duplicate XP after database constraint user={} eventType={} sourceType={} sourceId={}",
                    user.getUserId(),
                    eventType,
                    sourceType,
                    sourceId
            );
            return;
        }

        updateSummary(user, points, today);
        log.info(
                "[POINTS] Awarded XP user={} workspace={} eventType={} sourceType={} sourceId={} points={}",
                user.getUserId(),
                workspace == null ? null : workspace.getWorkspaceId(),
                eventType,
                sourceType,
                sourceId,
                points
        );
    }

    private void updateSummary(User user, int points, LocalDate eventDate) {
        UserPointSummary summary = userPointSummaryRepository.findById(user.getUserId())
                .orElseGet(() -> emptySummary(user));
        LocalDate weekStart = weekStart(eventDate);
        LocalDate monthStart = eventDate.withDayOfMonth(1);

        summary.setTotalPoints(safe(summary.getTotalPoints()) + points);
        if (!weekStart.equals(summary.getCurrentWeekStartDate())) {
            summary.setCurrentWeekStartDate(weekStart);
            summary.setCurrentWeekPoints(0);
        }
        summary.setCurrentWeekPoints(safe(summary.getCurrentWeekPoints()) + points);

        if (!monthStart.equals(summary.getCurrentMonthStartDate())) {
            summary.setCurrentMonthStartDate(monthStart);
            summary.setCurrentMonthPoints(0);
        }
        summary.setCurrentMonthPoints(safe(summary.getCurrentMonthPoints()) + points);
        updateStreak(summary, eventDate);

        userPointSummaryRepository.save(summary);
    }

    private void updateStreak(UserPointSummary summary, LocalDate eventDate) {
        LocalDate lastPointDate = summary.getLastPointDate();
        if (lastPointDate == null) {
            summary.setStreakDays(1);
        } else if (lastPointDate.equals(eventDate)) {
            return;
        } else if (lastPointDate.plusDays(1).equals(eventDate)) {
            summary.setStreakDays(safe(summary.getStreakDays()) + 1);
        } else {
            summary.setStreakDays(1);
        }
        summary.setLastPointDate(eventDate);
    }

    private void upsertQuizScore(Quiz quiz, QuizAttempt attempt, int earnedPoints) {
        UserQuizScore score = userQuizScoreRepository
                .findByUserUserIdAndQuizQuizId(quiz.getUser().getUserId(), quiz.getQuizId())
                .orElseGet(() -> newQuizScore(quiz));
        updateBestAttemptIfNeeded(score, attempt, earnedPoints);
    }

    private void updateBestAttemptIfNeeded(UserQuizScore score, QuizAttempt attempt, int earnedPoints) {
        if (attempt.getScore() > safe(score.getBestScorePercent())) {
            score.setBestAttempt(attempt);
            score.setBestScorePercent(attempt.getScore());
        }
        score.setEarnedPoints(Math.max(safe(score.getEarnedPoints()), earnedPoints));
        userQuizScoreRepository.save(score);
    }

    private UserQuizScore newQuizScore(Quiz quiz) {
        UserQuizScore score = new UserQuizScore();
        score.setUser(quiz.getUser());
        score.setQuiz(quiz);
        return score;
    }

    private int resolveQuizPoints(Integer score) {
        int safeScore = safe(score);
        if (safeScore >= 90) {
            return QUIZ_EXCELLENT_POINTS;
        }
        if (safeScore >= 70) {
            return QUIZ_PASSED_POINTS;
        }
        return 0;
    }

    private PointEventType resolveQuizEventType(Integer earnedPoints, int targetPoints) {
        if (safe(earnedPoints) > 0) {
            return PointEventType.QUIZ_UPGRADE_BONUS;
        }
        return targetPoints >= QUIZ_EXCELLENT_POINTS ? PointEventType.QUIZ_EXCELLENT : PointEventType.QUIZ_PASSED;
    }

    private UserPointSummary emptySummary(User user) {
        // `user` may be an uninitialized Hibernate proxy (e.g. from quiz.getUser()).
        // Re-fetch the concrete, managed instance so @MapsId can derive the shared
        // primary key from a real identifier at persist time. Reading the id off the
        // proxy via getUserId() is safe — it does not force proxy initialization.
        User managedUser = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserPointSummary summary = new UserPointSummary();
        summary.setUser(managedUser);
        // Intentionally DO NOT call summary.setUserId(...). Leaving the @Id null lets
        // Spring Data evaluate isNew() == true and route to persist() instead of
        // merge(); @MapsId then copies the id from managedUser during the insert.
        return summary;
    }

    private List<LeaderboardEntryResponse> toLeaderboardEntries(List<PointEventRepository.LeaderboardRow> rows) {
        final int[] rank = {1};
        return rows.stream()
                .map(row -> LeaderboardEntryResponse.builder()
                        .rank(rank[0]++)
                        .userId(row.getUserId())
                        .fullName(row.getFullName())
                        .avatarObjectKey(s3PresignedUrlService.createViewUrl(row.getAvatarObjectKey()))
                        .points(safe(row.getPoints()))
                        .streakDays(safe(row.getStreakDays()))
                        .build())
                .toList();
    }

    private List<LeaderboardEntryResponse> toSummaryLeaderboardEntries(List<UserPointSummary> summaries) {
        final int[] rank = {1};
        return summaries.stream()
                .map(summary -> LeaderboardEntryResponse.builder()
                        .rank(rank[0]++)
                        .userId(summary.getUserId())
                        .fullName(summary.getUser().getFullName())
                        .avatarObjectKey(s3PresignedUrlService.createViewUrl(summary.getUser().getAvatarObjectKey()))
                        .points(safe(summary.getTotalPoints()))
                        .streakDays(safe(summary.getStreakDays()))
                        .build())
                .toList();
    }

    private PointEventResponse toPointEventResponse(PointEvent event) {
        return PointEventResponse.builder()
                .eventType(event.getEventType())
                .sourceType(event.getSourceType())
                .sourceId(event.getSourceId())
                .points(safe(event.getPoints()))
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .createdAt(event.getCreatedAt())
                .build();
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
            case MONTHLY -> monthStart.plusMonths(1).minusDays(1);
            case ALL_TIME -> null;
        };
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private int safe(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    private Integer safeRank(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }
}
