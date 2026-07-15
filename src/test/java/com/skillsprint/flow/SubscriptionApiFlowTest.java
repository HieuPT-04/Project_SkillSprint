package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.dto.response.subscription.QuotaStatusResponse;
import com.skillsprint.dto.response.subscription.UserServicePlanResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.plan.SubscriptionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.subscription.QuotaService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionApiFlowTest {

    private static final String USER_ID = "subscription-flow-user";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    SubscriptionService subscriptionService;

    @MockBean
    QuotaService quotaService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID planId;
    UUID subscriptionId;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void plansEndpointIsPublic() throws Exception {
        when(subscriptionService.getActivePlans())
                .thenReturn(List.of(UserServicePlanResponse.builder()
                        .planId(planId)
                        .planName("Skill Builder")
                        .monthlyPrice(new BigDecimal("100000"))
                        .currency("VND")
                        .build()));

        mockMvc.perform(get("/api/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy danh sách gói thành công"))
                .andExpect(jsonPath("$.data[0].planId").value(planId.toString()))
                .andExpect(jsonPath("$.data[0].planName").value("Skill Builder"))
                .andExpect(jsonPath("$.data[0].monthlyPrice").value(100000));

        verify(subscriptionService).getActivePlans();
    }

    @Test
    void anonymousUserCannotReadCurrentSubscriptionOrQuota() throws Exception {
        mockMvc.perform(get("/api/subscriptions/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/subscriptions/me/quota"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(subscriptionService, never()).getCurrentSubscription(any());
        verify(quotaService, never()).getQuotaStatus(any());
    }

    @Test
    void authenticatedUserCanReadCurrentSubscription() throws Exception {
        when(subscriptionService.getCurrentSubscription(USER_ID))
                .thenReturn(CurrentSubscriptionResponse.builder()
                        .subscriptionId(subscriptionId)
                        .plan(UserServicePlanResponse.builder()
                                .planId(planId)
                                .planName("Premium")
                                .planType(ServicePlanType.ADMIN_DEFAULT)
                                .monthlyPrice(new BigDecimal("200000"))
                                .currency("VND")
                                .build())
                        .status(SubscriptionStatus.ACTIVE)
                        .startAt(Instant.parse("2026-06-23T10:00:00Z"))
                        .endAt(Instant.parse("2026-07-23T10:00:00Z"))
                        .build());

        mockMvc.perform(get("/api/subscriptions/me").with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy gói hiện tại thành công"))
                .andExpect(jsonPath("$.data.subscriptionId").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan.planName").value("Premium"))
                .andExpect(jsonPath("$.data.plan.planType").value("ADMIN_DEFAULT"));

        verify(subscriptionService).getCurrentSubscription(USER_ID);
    }

    @Test
    void authenticatedUserCanReadQuotaStatus() throws Exception {
        when(quotaService.getQuotaStatus(USER_ID))
                .thenReturn(QuotaStatusResponse.builder()
                        .plan(ServicePlanType.SKILL_BUILDER)
                        .maxWorkspaces(3)
                        .usedWorkspaces(2L)
                        .remainingWorkspaces(1L)
                        .maxUploads(20)
                        .usedUploads(7L)
                        .remainingUploads(13L)
                        .maxCommunityRooms(2)
                        .usedCommunityRooms(1L)
                        .remainingCommunityRooms(1L)
                        .aiGenerateLimit(25)
                        .usedAiGenerate(12L)
                        .remainingAiGenerate(13L)
                        .maxFileMb(50)
                        .maxWorkspaceMb(500)
                        .usedStorageBytes(1572864L)
                        .usedStorageMb(1.5)
                        .remainingStorageMb(498.5)
                        .build());

        mockMvc.perform(get("/api/subscriptions/me/quota").with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy giới hạn sử dụng thành công"))
                .andExpect(jsonPath("$.data.plan").value("SKILL_BUILDER"))
                .andExpect(jsonPath("$.data.maxWorkspaces").value(3))
                .andExpect(jsonPath("$.data.usedWorkspaces").value(2))
                .andExpect(jsonPath("$.data.remainingWorkspaces").value(1))
                .andExpect(jsonPath("$.data.usedStorageMb").value(1.5))
                .andExpect(jsonPath("$.data.remainingStorageMb").value(498.5));

        verify(quotaService).getQuotaStatus(USER_ID);
    }

    @Test
    void quotaServiceErrorsUseGlobalErrorShape() throws Exception {
        when(quotaService.getQuotaStatus(USER_ID))
                .thenThrow(new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        mockMvc.perform(get("/api/subscriptions/me/quota").with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.path").value("/api/subscriptions/me/quota"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("subscription-flow@example.com");
        user.setFullName("Subscription Flow");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
