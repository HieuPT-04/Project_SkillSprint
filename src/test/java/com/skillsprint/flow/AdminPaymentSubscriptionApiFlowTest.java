package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.admin.ReconcilePaymentRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.dto.response.subscription.UserServicePlanResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.plan.SubscriptionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.payment.AdminPaymentService;
import com.skillsprint.service.subscription.SubscriptionService;
import java.math.BigDecimal;
import java.time.Instant;
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
class AdminPaymentSubscriptionApiFlowTest {

    private static final String ADMIN_ID = "admin-payment-flow";
    private static final String LEARNER_ID = "learner-payment-flow";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    AdminPaymentService adminPaymentService;

    @MockBean
    SubscriptionService subscriptionService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID paymentId;
    UUID planId;
    UUID subscriptionId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        planId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.save(user(ADMIN_ID, "admin-payment-flow@example.com", "Payment Admin"));
        userRepository.save(user(LEARNER_ID, "learner-payment-flow@example.com", "Payment Learner"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
    }

    @Test
    void anonymousUserCannotReadAdminPayments() throws Exception {
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/admin/payments"));

        verify(adminPaymentService, never()).getPayments(any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void learnerCannotReadAdminPayments() throws Exception {
        mockMvc.perform(get("/api/admin/payments").with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(adminPaymentService, never()).getPayments(any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void adminCanReadPaymentPageWithFilters() throws Exception {
        when(adminPaymentService.getPayments(PaymentStatus.PENDING, "learner", 1, 20))
                .thenReturn(PageResponse.<PaymentTransactionResponse>builder()
                        .items(List.of(PaymentTransactionResponse.builder()
                                .paymentId(paymentId)
                                .status(PaymentStatus.PENDING)
                                .plan(ServicePlanType.SKILL_BUILDER)
                                .planName("Skill Builder")
                                .amount(new BigDecimal("100000"))
                                .currency("VND")
                                .paymentCode("DH123")
                                .build()))
                        .page(1)
                        .size(20)
                        .totalItems(1)
                        .totalPages(1)
                        .first(false)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/admin/payments")
                        .with(adminJwt())
                        .param("status", "PENDING")
                        .param("search", "learner")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Thành công"))
                .andExpect(jsonPath("$.data.items[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].plan").value("SKILL_BUILDER"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalItems").value(1));

        verify(adminPaymentService).getPayments(PaymentStatus.PENDING, "learner", 1, 20);
    }

    @Test
    void adminCanReconcilePayment() throws Exception {
        Instant paidAt = Instant.parse("2026-06-23T10:00:00Z");
        when(adminPaymentService.reconcilePayment(eq(paymentId), any(ReconcilePaymentRequest.class)))
                .thenReturn(PaymentTransactionResponse.builder()
                        .paymentId(paymentId)
                        .status(PaymentStatus.PAID)
                        .plan(ServicePlanType.PREMIUM)
                        .amount(new BigDecimal("200000"))
                        .currency("VND")
                        .paymentCode("DH456")
                        .providerTransactionId("BANK-9001")
                        .providerReferenceCode("REF-1")
                        .paidAt(paidAt)
                        .build());

        mockMvc.perform(post("/api/admin/payments/{paymentId}/reconcile", paymentId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerTransactionId": "BANK-9001",
                                  "providerReferenceCode": "REF-1",
                                  "paidAt": "2026-06-23T10:00:00Z",
                                  "note": "Checked bank statement"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đối soát thanh toán thành công"))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.providerTransactionId").value("BANK-9001"))
                .andExpect(jsonPath("$.data.providerReferenceCode").value("REF-1"));

        verify(adminPaymentService).reconcilePayment(eq(paymentId), any(ReconcilePaymentRequest.class));
    }

    @Test
    void reconcileValidationErrorDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/admin/payments/{paymentId}/reconcile", paymentId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerReferenceCode": "REF-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        verify(adminPaymentService, never()).reconcilePayment(any(), any());
    }

    @Test
    void reconcileAlreadyConfirmedMapsToConflict() throws Exception {
        when(adminPaymentService.reconcilePayment(eq(paymentId), any(ReconcilePaymentRequest.class)))
                .thenThrow(new AppException(ErrorCode.PAYMENT_ALREADY_CONFIRMED));

        mockMvc.perform(post("/api/admin/payments/{paymentId}/reconcile", paymentId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerTransactionId": "BANK-9001"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void learnerCannotUpdateAnotherUsersSubscription() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/subscription", LEARNER_ID)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planType": "PREMIUM"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(subscriptionService, never()).activatePlan(any(), any());
    }

    @Test
    void adminCanUpdateUserSubscription() throws Exception {
        when(subscriptionService.activatePlan(LEARNER_ID, ServicePlanType.PREMIUM))
                .thenReturn(CurrentSubscriptionResponse.builder()
                        .subscriptionId(subscriptionId)
                        .plan(UserServicePlanResponse.builder()
                                .planId(planId)
                                .planName("Premium")
                                .monthlyPrice(new BigDecimal("200000"))
                                .currency("VND")
                                .build())
                        .status(SubscriptionStatus.ACTIVE)
                        .startAt(Instant.parse("2026-06-23T10:00:00Z"))
                        .endAt(Instant.parse("2026-07-23T10:00:00Z"))
                        .build());

        mockMvc.perform(patch("/api/admin/users/{userId}/subscription", LEARNER_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planType": "PREMIUM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật gói sử dụng thành công"))
                .andExpect(jsonPath("$.data.subscriptionId").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan.planName").value("Premium"))
                .andExpect(jsonPath("$.data.plan.monthlyPrice").value(200000));

        verify(subscriptionService).activatePlan(LEARNER_ID, ServicePlanType.PREMIUM);
    }

    @Test
    void updateSubscriptionValidationErrorDoesNotCallService() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/subscription", LEARNER_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        verify(subscriptionService, never()).activatePlan(any(), any());
    }

    @Test
    void updateSubscriptionMapsMissingPlanToNotFound() throws Exception {
        when(subscriptionService.activatePlan(LEARNER_ID, ServicePlanType.PREMIUM))
                .thenThrow(new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND));

        mockMvc.perform(patch("/api/admin/users/{userId}/subscription", LEARNER_ID)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planType": "PREMIUM"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
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
}
