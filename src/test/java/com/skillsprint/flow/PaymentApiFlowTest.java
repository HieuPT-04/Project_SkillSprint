package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.payment.CreateSepayPaymentRequest;
import com.skillsprint.dto.request.payment.SepayWebhookRequest;
import com.skillsprint.dto.response.payment.SepayPaymentResponse;
import com.skillsprint.dto.response.payment.UserPaymentResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.payment.SepayPaymentService;
import com.skillsprint.service.ratelimit.RateLimitService;
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
class PaymentApiFlowTest {

    private static final String USER_ID = "payment-flow-user";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    SepayPaymentService sepayPaymentService;

    @MockBean
    RateLimitService rateLimitService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID paymentId;
    UUID planId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        planId = UUID.randomUUID();
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void anonymousUserCannotCreateSepayPayment() throws Exception {
        mockMvc.perform(post("/api/payments/sepay/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPaymentBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/payments/sepay/create"));

        verify(rateLimitService, never()).checkPaymentCreate(any());
        verify(sepayPaymentService, never()).createPayment(any(), any());
    }

    @Test
    void authenticatedUserCanCreateSepayPayment() throws Exception {
        when(sepayPaymentService.createPayment(eq(USER_ID), any(CreateSepayPaymentRequest.class)))
                .thenReturn(SepayPaymentResponse.builder()
                        .paymentId(paymentId)
                        .status(PaymentStatus.PENDING)
                        .planId(planId)
                        .plan(ServicePlanType.SKILL_BUILDER)
                        .planName("Skill Builder")
                        .amount(new BigDecimal("100000"))
                        .currency("VND")
                        .paymentCode("DH123")
                        .qrUrl("https://qr.example/DH123")
                        .expiredAt(Instant.parse("2026-06-23T10:20:00Z"))
                        .build());

        mockMvc.perform(post("/api/payments/sepay/create")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPaymentBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo thanh toán thành công"))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.plan").value("SKILL_BUILDER"))
                .andExpect(jsonPath("$.data.amount").value(100000))
                .andExpect(jsonPath("$.data.paymentCode").value("DH123"))
                .andExpect(jsonPath("$.data.qrUrl").value("https://qr.example/DH123"));

        verify(rateLimitService).checkPaymentCreate(USER_ID);
        verify(sepayPaymentService).createPayment(eq(USER_ID), any(CreateSepayPaymentRequest.class));
    }

    @Test
    void paymentCreateRateLimitReturnsTooManyRequestsWithoutCallingPaymentService() throws Exception {
        doThrow(new AppException(ErrorCode.RATE_LIMIT_EXCEEDED))
                .when(rateLimitService)
                .checkPaymentCreate(USER_ID);

        mockMvc.perform(post("/api/payments/sepay/create")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPaymentBody()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(429));

        verify(sepayPaymentService, never()).createPayment(any(), any());
    }

    @Test
    void webhookIsPublicAndReturnsProviderFriendlySuccessShape() throws Exception {
        mockMvc.perform(post("/api/payments/sepay/webhook")
                        .header("Authorization", "Bearer webhook-secret")
                        .header("X-API-KEY", "webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sepayPaymentService).handleWebhook(
                any(SepayWebhookRequest.class),
                eq("Bearer webhook-secret"),
                eq("webhook-secret")
        );
    }

    @Test
    void webhookServiceErrorsUseGlobalErrorShape() throws Exception {
        doThrow(new AppException(ErrorCode.PAYMENT_INVALID_SIGNATURE))
                .when(sepayPaymentService)
                .handleWebhook(any(SepayWebhookRequest.class), any(), any());

        mockMvc.perform(post("/api/payments/sepay/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.path").value("/api/payments/sepay/webhook"));
    }

    @Test
    void authenticatedUserCanReadOwnPaymentHistoryAndDetail() throws Exception {
        when(sepayPaymentService.getMyPayments(USER_ID))
                .thenReturn(List.of(UserPaymentResponse.builder()
                        .paymentId(paymentId)
                        .status(PaymentStatus.PAID)
                        .plan(ServicePlanType.SKILL_BUILDER)
                        .amount(new BigDecimal("100000"))
                        .currency("VND")
                        .paymentCode("DH123")
                        .paidAt(Instant.parse("2026-06-23T10:00:00Z"))
                        .build()));
        when(sepayPaymentService.getMyPayment(USER_ID, paymentId))
                .thenReturn(UserPaymentResponse.builder()
                        .paymentId(paymentId)
                        .status(PaymentStatus.PAID)
                        .plan(ServicePlanType.SKILL_BUILDER)
                        .amount(new BigDecimal("100000"))
                        .currency("VND")
                        .paymentCode("DH123")
                        .build());

        mockMvc.perform(get("/api/payments/me").with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId).with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.paymentCode").value("DH123"));
    }

    @Test
    void paymentDetailMapsNotFoundError() throws Exception {
        when(sepayPaymentService.getMyPayment(USER_ID, paymentId))
                .thenThrow(new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId).with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private String createPaymentBody() {
        return """
                {
                  "planType": "SKILL_BUILDER"
                }
                """;
    }

    private String webhookBody() {
        return """
                {
                  "id": 9001,
                  "accountNumber": "123456789",
                  "code": "DH123",
                  "content": "Thanh toan DH123",
                  "transferType": "in",
                  "transferAmount": 100000,
                  "referenceCode": "BANK-REF-1",
                  "description": "Payment DH123"
                }
                """;
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("payment-flow@example.com");
        user.setFullName("Payment Flow");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
