package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.admin.UpdateUserRoleRequest;
import com.skillsprint.dto.request.admin.UpdateUserStatusRequest;
import com.skillsprint.dto.response.admin.AdminDashboardResponse;
import com.skillsprint.dto.response.admin.AdminUserResponse;
import com.skillsprint.dto.response.admin.AdminUserSummaryResponse;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.admin.AdminDashboardService;
import com.skillsprint.service.user.AdminUserService;
import java.math.BigDecimal;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserDashboardApiFlowTest {

    private static final String ADMIN_ID = "admin-user-dashboard-flow";
    private static final String LEARNER_ID = "learner-user-dashboard-flow";
    private static final String TARGET_ID = "target-user-dashboard-flow";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    AdminUserService adminUserService;

    @MockBean
    AdminDashboardService adminDashboardService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID paymentId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(TARGET_ID);
        userRepository.save(user(ADMIN_ID, "admin-user-dashboard-flow@example.com", "User Admin"));
        userRepository.save(user(LEARNER_ID, "learner-user-dashboard-flow@example.com", "User Learner"));
        userRepository.save(user(TARGET_ID, "target-user-dashboard-flow@example.com", "Target Learner"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(TARGET_ID);
    }

    @Test
    void anonymousUserCannotUseAdminUserOrDashboardEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/admin/users"));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/admin/dashboard"));

        verify(adminUserService, never()).getUsers(any(), any(Integer.class), any(Integer.class), any());
        verify(adminDashboardService, never()).getDashboard(any(), any());
    }

    @Test
    void learnerCannotUseAdminUserOrDashboardEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(patch("/api/admin/users/{userId}/status", TARGET_ID)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/admin/dashboard").with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(adminUserService, never()).getUsers(any(), any(Integer.class), any(Integer.class), any());
        verify(adminUserService, never()).updateUserStatus(any(), any());
        verify(adminDashboardService, never()).getDashboard(any(), any());
    }

    @Test
    void adminCanListAndReadUsers() throws Exception {
        when(adminUserService.getUsers("learner", 1, 20, null))
                .thenReturn(PageResponse.<AdminUserResponse>builder()
                        .items(List.of(adminUserResponse(TARGET_ID, "target-user-dashboard-flow@example.com",
                                "Target Learner", UserStatus.ACTIVE, List.of("LEARNER"))))
                        .page(1)
                        .size(20)
                        .totalItems(1)
                        .totalPages(1)
                        .first(false)
                        .last(true)
                        .build());
        when(adminUserService.getUser(TARGET_ID))
                .thenReturn(adminUserResponse(TARGET_ID, "target-user-dashboard-flow@example.com",
                        "Target Learner", UserStatus.ACTIVE, List.of("LEARNER")));
        when(adminUserService.getUserSummary(null))
                .thenReturn(AdminUserSummaryResponse.builder()
                        .totalUsers(108)
                        .activeUsers(100)
                        .learnerUsers(104)
                        .adminUsers(4)
                        .build());

        mockMvc.perform(get("/api/admin/users")
                        .with(adminJwt())
                        .param("search", "learner")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Thành công"))
                .andExpect(jsonPath("$.data.items[0].userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.items[0].email").value("target-user-dashboard-flow@example.com"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].roles[0]").value("LEARNER"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalItems").value(1));

        mockMvc.perform(get("/api/admin/users/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(108))
                .andExpect(jsonPath("$.data.activeUsers").value(100))
                .andExpect(jsonPath("$.data.learnerUsers").value(104))
                .andExpect(jsonPath("$.data.adminUsers").value(4));

        mockMvc.perform(get("/api/admin/users/{userId}", TARGET_ID).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.fullName").value("Target Learner"))
                .andExpect(jsonPath("$.data.currentSubscription.planName").value("Premium"))
                .andExpect(jsonPath("$.data.currentSubscription.status").value("ACTIVE"));

        verify(adminUserService).getUsers("learner", 1, 20, null);
        verify(adminUserService).getUserSummary(null);
        verify(adminUserService).getUser(TARGET_ID);
    }

    @Test
    void adminCanUpdateUserStatusAndRole() throws Exception {
        when(adminUserService.updateUserStatus(eq(TARGET_ID), any(UpdateUserStatusRequest.class)))
                .thenReturn(adminUserResponse(TARGET_ID, "target-user-dashboard-flow@example.com",
                        "Target Learner", UserStatus.DISABLED, List.of("LEARNER")));
        when(adminUserService.updateUserRole(eq(TARGET_ID), any(UpdateUserRoleRequest.class)))
                .thenReturn(adminUserResponse(TARGET_ID, "target-user-dashboard-flow@example.com",
                        "Target Learner", UserStatus.ACTIVE, List.of("ADMIN")));

        mockMvc.perform(patch("/api/admin/users/{userId}/status", TARGET_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái người dùng thành công"))
                .andExpect(jsonPath("$.data.userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(patch("/api/admin/users/{userId}/roles", TARGET_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật vai trò người dùng thành công"))
                .andExpect(jsonPath("$.data.userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));

        verify(adminUserService).updateUserStatus(eq(TARGET_ID), any(UpdateUserStatusRequest.class));
        verify(adminUserService).updateUserRole(eq(TARGET_ID), any(UpdateUserRoleRequest.class));
    }

    @Test
    void userValidationErrorsDoNotCallService() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/status", TARGET_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        mockMvc.perform(patch("/api/admin/users/{userId}/roles", TARGET_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        verify(adminUserService, never()).updateUserStatus(any(), any());
        verify(adminUserService, never()).updateUserRole(any(), any());
    }

    @Test
    void userBusinessErrorsAreMapped() throws Exception {
        when(adminUserService.getUser(TARGET_ID)).thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));
        when(adminUserService.updateUserRole(eq(TARGET_ID), any(UpdateUserRoleRequest.class)))
                .thenThrow(new AppException(ErrorCode.ROLE_NOT_FOUND));

        mockMvc.perform(get("/api/admin/users/{userId}", TARGET_ID).with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(patch("/api/admin/users/{userId}/roles", TARGET_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void adminCanReadDashboardWithDateRange() throws Exception {
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-23");
        when(adminDashboardService.getDashboard(from, to)).thenReturn(dashboardResponse(from, to));

        mockMvc.perform(get("/api/admin/dashboard")
                        .with(adminJwt())
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy dashboard admin thành công"))
                .andExpect(jsonPath("$.data.range.from").value("2026-06-01"))
                .andExpect(jsonPath("$.data.range.to").value("2026-06-23"))
                .andExpect(jsonPath("$.data.overview.totalUsers").value(120))
                .andExpect(jsonPath("$.data.overview.totalRevenue").value(3500000))
                .andExpect(jsonPath("$.data.users.newInRange").value(9))
                .andExpect(jsonPath("$.data.payments.pending").value(3))
                .andExpect(jsonPath("$.data.learning.materials.completed").value(40))
                .andExpect(jsonPath("$.data.charts.revenueByDay[0].amount").value(150000))
                .andExpect(jsonPath("$.data.charts.newUsersByDay[0].count").value(2))
                .andExpect(jsonPath("$.data.alerts.unverifiedUsers").value(4))
                .andExpect(jsonPath("$.data.recentUsers[0].userId").value(TARGET_ID))
                .andExpect(jsonPath("$.data.recentPayments[0].paymentId").value(paymentId.toString()));

        verify(adminDashboardService).getDashboard(from, to);
    }

    @Test
    void adminCanReadDashboardWithoutDateRange() throws Exception {
        LocalDate from = LocalDate.parse("2026-06-16");
        LocalDate to = LocalDate.parse("2026-06-23");
        when(adminDashboardService.getDashboard(null, null)).thenReturn(dashboardResponse(from, to));

        mockMvc.perform(get("/api/admin/dashboard").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy dashboard admin thành công"))
                .andExpect(jsonPath("$.data.range.from").value("2026-06-16"))
                .andExpect(jsonPath("$.data.range.to").value("2026-06-23"))
                .andExpect(jsonPath("$.data.overview.totalUsers").value(120));

        verify(adminDashboardService).getDashboard(null, null);
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
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private AdminUserResponse adminUserResponse(
            String userId,
            String email,
            String fullName,
            UserStatus status,
            List<String> roles
    ) {
        return AdminUserResponse.builder()
                .userId(userId)
                .email(email)
                .emailVerified(true)
                .fullName(fullName)
                .avatarUrl("https://example.com/avatar.png")
                .timeZone("Asia/Ho_Chi_Minh")
                .status(status)
                .roles(roles)
                .lastLoginAt(Instant.parse("2026-06-22T10:00:00Z"))
                .createdAt(Instant.parse("2026-06-01T10:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T10:00:00Z"))
                .currentSubscription(AdminUserResponse.SubscriptionAdminResponse.builder()
                        .subscriptionId(UUID.randomUUID().toString())
                        .planName("Premium")
                        .startDate(LocalDate.parse("2026-06-01"))
                        .endDate(LocalDate.parse("2026-07-01"))
                        .status("ACTIVE")
                        .build())
                .build();
    }

    private AdminDashboardResponse dashboardResponse(LocalDate from, LocalDate to) {
        return AdminDashboardResponse.builder()
                .generatedAt(Instant.parse("2026-06-23T10:00:00Z"))
                .range(AdminDashboardResponse.DateRange.builder()
                        .from(from)
                        .to(to)
                        .build())
                .overview(AdminDashboardResponse.OverviewStats.builder()
                        .totalUsers(120)
                        .activeUsers(100)
                        .paidUsers(35)
                        .activeSubscriptions(40)
                        .pendingPayments(3)
                        .failedPayments(2)
                        .totalRevenue(new BigDecimal("3500000"))
                        .todayRevenue(new BigDecimal("150000"))
                        .build())
                .users(AdminDashboardResponse.UserStats.builder()
                        .total(120)
                        .active(100)
                        .disabled(20)
                        .newToday(2)
                        .newInRange(9)
                        .emailVerified(116)
                        .unverified(4)
                        .build())
                .workspaces(AdminDashboardResponse.WorkspaceStats.builder()
                        .total(80)
                        .active(75)
                        .archived(5)
                        .build())
                .subscriptions(AdminDashboardResponse.SubscriptionStats.builder()
                        .trial(0)
                        .active(40)
                        .expired(8)
                        .canceled(4)
                        .pastDue(1)
                        .free(72)
                        .skillBuilder(30)
                        .premium(18)
                        .build())
                .payments(AdminDashboardResponse.PaymentStats.builder()
                        .total(50)
                        .pending(3)
                        .paid(42)
                        .failed(2)
                        .canceled(1)
                        .expired(2)
                        .revenueTotal(new BigDecimal("3500000"))
                        .revenueToday(new BigDecimal("150000"))
                        .revenueThisMonth(new BigDecimal("1800000"))
                        .build())
                .learning(AdminDashboardResponse.LearningStats.builder()
                        .materials(AdminDashboardResponse.MaterialStats.builder()
                                .total(45)
                                .pending(1)
                                .processing(2)
                                .completed(40)
                                .failed(2)
                                .build())
                        .roadmaps(AdminDashboardResponse.RoadmapStats.builder()
                                .total(32)
                                .draft(4)
                                .active(20)
                                .completed(6)
                                .archived(2)
                                .build())
                        .calendarTasks(AdminDashboardResponse.CalendarTaskStats.builder()
                                .total(120)
                                .todo(80)
                                .completed(35)
                                .missed(3)
                                .cancelled(2)
                                .build())
                        .studySessions(AdminDashboardResponse.StudySessionStats.builder()
                                .total(60)
                                .inProgress(5)
                                .completed(55)
                                .build())
                        .build())
                .charts(AdminDashboardResponse.ChartStats.builder()
                        .revenueByDay(List.of(AdminDashboardResponse.RevenuePoint.builder()
                                .date(from)
                                .amount(new BigDecimal("150000"))
                                .build()))
                        .newUsersByDay(List.of(AdminDashboardResponse.CountPoint.builder()
                                .date(from)
                                .count(2)
                                .build()))
                        .build())
                .alerts(AdminDashboardResponse.AlertStats.builder()
                        .pendingPaymentsOverdue(1)
                        .failedPayments(2)
                        .failedMaterialProcessing(2)
                        .unverifiedUsers(4)
                        .build())
                .recentUsers(List.of(AdminDashboardResponse.RecentUser.builder()
                        .userId(TARGET_ID)
                        .email("target-user-dashboard-flow@example.com")
                        .fullName("Target Learner")
                        .emailVerified(true)
                        .status(UserStatus.ACTIVE)
                        .createdAt(Instant.parse("2026-06-01T10:00:00Z"))
                        .lastLoginAt(Instant.parse("2026-06-22T10:00:00Z"))
                        .build()))
                .recentPayments(List.of(PaymentTransactionResponse.builder()
                        .paymentId(paymentId)
                        .status(PaymentStatus.PAID)
                        .plan(ServicePlanType.PREMIUM)
                        .planName("Premium")
                        .amount(new BigDecimal("200000"))
                        .currency("VND")
                        .paymentCode("DH-ADMIN-1")
                        .paidAt(Instant.parse("2026-06-23T10:00:00Z"))
                        .build()))
                .build();
    }
}
