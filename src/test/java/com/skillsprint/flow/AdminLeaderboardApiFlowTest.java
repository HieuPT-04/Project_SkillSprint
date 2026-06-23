package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.response.admin.AdminLeaderboardEntryResponse;
import com.skillsprint.dto.response.admin.AdminLeaderboardResponse;
import com.skillsprint.dto.response.admin.AdminPointEventResponse;
import com.skillsprint.dto.response.admin.AdminUserPointSummaryResponse;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.points.LeaderboardPeriod;
import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.enums.points.PointSourceType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.points.AdminLeaderboardService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLeaderboardApiFlowTest {

    private static final String ADMIN_ID = "admin-leaderboard-flow";
    private static final String LEARNER_ID = "learner-leaderboard-flow";
    private static final String TARGET_ID = "target-leaderboard-flow";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    AdminLeaderboardService adminLeaderboardService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID workspaceId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(TARGET_ID);
        userRepository.save(user(ADMIN_ID, "admin-leaderboard-flow@example.com", "Leaderboard Admin"));
        userRepository.save(user(LEARNER_ID, "learner-leaderboard-flow@example.com", "Leaderboard Learner"));
        userRepository.save(user(TARGET_ID, "target-leaderboard-flow@example.com", "Target Learner"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(TARGET_ID);
    }

    @Test
    void anonymousUserCannotUseAdminLeaderboardEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/leaderboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/admin/leaderboard"));

        mockMvc.perform(get("/api/admin/users/{userId}/points", TARGET_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(adminLeaderboardService, never()).getLeaderboard(any(), any(), any(Integer.class), any(Integer.class));
        verify(adminLeaderboardService, never()).getUserPointSummary(any());
    }

    @Test
    void learnerCannotUseAdminLeaderboardEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/leaderboard").with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/admin/users/{userId}/point-events", TARGET_ID).with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(adminLeaderboardService, never()).getLeaderboard(any(), any(), any(Integer.class), any(Integer.class));
        verify(adminLeaderboardService, never()).getUserPointEvents(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void adminCanReadLeaderboardSummaryAndEvents() throws Exception {
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-23");
        when(adminLeaderboardService.getLeaderboard(LeaderboardPeriod.MONTHLY, "target", 1, 10))
                .thenReturn(adminLeaderboardResponse(from, to));
        when(adminLeaderboardService.getUserPointSummary(TARGET_ID))
                .thenReturn(userPointSummary());
        when(adminLeaderboardService.getUserPointEvents(
                TARGET_ID,
                PointEventType.QUIZ_PASSED,
                from,
                to,
                workspaceId,
                1,
                10
        )).thenReturn(pointEventsPage(from));

        mockMvc.perform(get("/api/admin/leaderboard")
                        .with(adminJwt())
                        .param("period", "MONTHLY")
                        .param("search", "target")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy bảng xếp hạng admin thành công"))
                .andExpect(jsonPath("$.data.period").value("MONTHLY"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-06-01"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-06-23"))
                .andExpect(jsonPath("$.data.entries.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.entries.items[0].userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.entries.items[0].points").value(250))
                .andExpect(jsonPath("$.data.entries.page").value(1))
                .andExpect(jsonPath("$.data.entries.size").value(10));

        mockMvc.perform(get("/api/admin/users/{userId}/points", TARGET_ID).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy tổng quan điểm người dùng thành công"))
                .andExpect(jsonPath("$.data.userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.totalPoints").value(900))
                .andExpect(jsonPath("$.data.weeklyRank").value(3))
                .andExpect(jsonPath("$.data.allTimeRank").value(7));

        mockMvc.perform(get("/api/admin/users/{userId}/point-events", TARGET_ID)
                        .with(adminJwt())
                        .param("type", "QUIZ_PASSED")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-23")
                        .param("workspaceId", workspaceId.toString())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy lịch sử điểm người dùng thành công"))
                .andExpect(jsonPath("$.data.items[0].eventType").value("QUIZ_PASSED"))
                .andExpect(jsonPath("$.data.items[0].sourceType").value("QUIZ"))
                .andExpect(jsonPath("$.data.items[0].workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.items[0].points").value(30))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalItems").value(1));

        verify(adminLeaderboardService).getLeaderboard(LeaderboardPeriod.MONTHLY, "target", 1, 10);
        verify(adminLeaderboardService).getUserPointSummary(TARGET_ID);
        verify(adminLeaderboardService).getUserPointEvents(
                TARGET_ID,
                PointEventType.QUIZ_PASSED,
                from,
                to,
                workspaceId,
                1,
                10
        );
    }

    @Test
    void adminLeaderboardBusinessErrorsAreMapped() throws Exception {
        when(adminLeaderboardService.getUserPointSummary(TARGET_ID))
                .thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/admin/users/{userId}/points", TARGET_ID).with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.path").value("/api/admin/users/" + TARGET_ID + "/points"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(ADMIN_ID).claim("cognito:groups", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(LEARNER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private User user(String userId, String email, String fullName) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private AdminLeaderboardResponse adminLeaderboardResponse(LocalDate from, LocalDate to) {
        return AdminLeaderboardResponse.builder()
                .period(LeaderboardPeriod.MONTHLY)
                .periodStart(from)
                .periodEnd(to)
                .entries(PageResponse.<AdminLeaderboardEntryResponse>builder()
                        .items(List.of(AdminLeaderboardEntryResponse.builder()
                                .rank(1)
                                .userId(TARGET_ID)
                                .fullName("Target Learner")
                                .email("target-leaderboard-flow@example.com")
                                .avatarObjectKey("avatars/target.png")
                                .points(250)
                                .streakDays(5)
                                .lastPointDate(to)
                                .build()))
                        .page(1)
                        .size(10)
                        .totalItems(1)
                        .totalPages(1)
                        .first(false)
                        .last(true)
                        .build())
                .build();
    }

    private AdminUserPointSummaryResponse userPointSummary() {
        return AdminUserPointSummaryResponse.builder()
                .userId(TARGET_ID)
                .fullName("Target Learner")
                .email("target-leaderboard-flow@example.com")
                .avatarObjectKey("avatars/target.png")
                .totalPoints(900)
                .weeklyPoints(120)
                .monthlyPoints(250)
                .streakDays(5)
                .lastPointDate(LocalDate.parse("2026-06-23"))
                .weeklyRank(3)
                .monthlyRank(2)
                .allTimeRank(7)
                .build();
    }

    private PageResponse<AdminPointEventResponse> pointEventsPage(LocalDate eventDate) {
        return PageResponse.<AdminPointEventResponse>builder()
                .items(List.of(AdminPointEventResponse.builder()
                        .eventType(PointEventType.QUIZ_PASSED)
                        .sourceType(PointSourceType.QUIZ)
                        .sourceId("quiz-1")
                        .points(30)
                        .description("Passed quiz")
                        .workspaceId(workspaceId)
                        .workspaceName("Java Workspace")
                        .eventDate(eventDate)
                        .createdAt(Instant.parse("2026-06-23T10:00:00Z"))
                        .build()))
                .page(1)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(false)
                .last(true)
                .build();
    }
}
