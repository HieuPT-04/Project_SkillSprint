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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.admin.CreateServicePlanRequest;
import com.skillsprint.dto.request.admin.UpdatePlanFeaturesRequest;
import com.skillsprint.dto.request.admin.UpdateServicePlanRequest;
import com.skillsprint.dto.request.admin.UpdateServicePlanStatusRequest;
import com.skillsprint.dto.response.admin.AdminAuditLogResponse;
import com.skillsprint.dto.response.subscription.FeatureCatalogResponse;
import com.skillsprint.dto.response.subscription.ServicePlanFeatureResponse;
import com.skillsprint.dto.response.subscription.ServicePlanQuotaResponse;
import com.skillsprint.dto.response.subscription.ServicePlanResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.subscription.AdminServicePlanService;
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
class AdminServicePlanApiFlowTest {

    private static final String ADMIN_ID = "admin-service-plan-flow";
    private static final String LEARNER_ID = "learner-service-plan-flow";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    AdminServicePlanService adminServicePlanService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID planId;
    UUID featureId;
    UUID auditLogId;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
        featureId = UUID.randomUUID();
        auditLogId = UUID.randomUUID();
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.save(user(ADMIN_ID, "admin-service-plan-flow@example.com", "Service Plan Admin"));
        userRepository.save(user(LEARNER_ID, "learner-service-plan-flow@example.com", "Service Plan Learner"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
    }

    @Test
    void anonymousUserCannotUseAdminServicePlanEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/subscription-plans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/admin/subscription-plans"));

        mockMvc.perform(post("/api/admin/subscription-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePlanJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(adminServicePlanService, never()).getPlans();
        verify(adminServicePlanService, never()).createPlan(any(), any());
    }

    @Test
    void learnerCannotUseAdminServicePlanEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/subscription-plans").with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(patch("/api/admin/subscription-plans/{planId}/status", planId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "publicVisible": false
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(put("/api/admin/subscription-plans/{planId}/features", planId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFeaturesJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(adminServicePlanService, never()).getPlans();
        verify(adminServicePlanService, never()).updateStatus(any(), any(), any());
        verify(adminServicePlanService, never()).updateFeatures(any(), any(), any());
    }

    @Test
    void adminCanReadPlansFeaturesAndAuditLogs() throws Exception {
        when(adminServicePlanService.getPlans()).thenReturn(List.of(servicePlan("Premium", ServicePlanType.PREMIUM)));
        when(adminServicePlanService.getFeatures()).thenReturn(List.of(featureCatalog()));
        when(adminServicePlanService.getAuditLogs()).thenReturn(List.of(auditLog()));

        mockMvc.perform(get("/api/admin/subscription-plans").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy danh sách gói thành công"))
                .andExpect(jsonPath("$.data[0].planId").value(planId.toString()))
                .andExpect(jsonPath("$.data[0].planName").value("Premium"))
                .andExpect(jsonPath("$.data[0].planType").value("PREMIUM"))
                .andExpect(jsonPath("$.data[0].monthlyPrice").value(200000))
                .andExpect(jsonPath("$.data[0].quotas.maxWorkspaces").value(10))
                .andExpect(jsonPath("$.data[0].features[0].featureKey").value("AI_TUTOR"))
                .andExpect(jsonPath("$.data[0].features[0].enabled").value(true));

        mockMvc.perform(get("/api/admin/subscription-plans/features").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy danh sách tính năng thành công"))
                .andExpect(jsonPath("$.data[0].featureId").value(featureId.toString()))
                .andExpect(jsonPath("$.data[0].featureKey").value("AI_TUTOR"))
                .andExpect(jsonPath("$.data[0].active").value(true));

        mockMvc.perform(get("/api/admin/subscription-plans/audit-logs").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy lịch sử chỉnh sửa gói thành công"))
                .andExpect(jsonPath("$.data[0].logId").value(auditLogId.toString()))
                .andExpect(jsonPath("$.data[0].adminUserId").value(ADMIN_ID))
                .andExpect(jsonPath("$.data[0].entityType").value("SERVICE_PLAN"))
                .andExpect(jsonPath("$.data[0].actionType").value("SERVICE_PLAN_UPDATED"));

        verify(adminServicePlanService).getPlans();
        verify(adminServicePlanService).getFeatures();
        verify(adminServicePlanService).getAuditLogs();
    }

    @Test
    void adminCanCreateReadUpdateStatusAndFeatures() throws Exception {
        when(adminServicePlanService.createPlan(eq(ADMIN_ID), any(CreateServicePlanRequest.class)))
                .thenReturn(servicePlan("Premium", ServicePlanType.PREMIUM));
        when(adminServicePlanService.getPlan(planId))
                .thenReturn(servicePlan("Premium", ServicePlanType.PREMIUM));
        when(adminServicePlanService.updatePlan(eq(ADMIN_ID), eq(planId), any(UpdateServicePlanRequest.class)))
                .thenReturn(servicePlan("Premium Plus", ServicePlanType.PREMIUM));
        when(adminServicePlanService.updateStatus(eq(ADMIN_ID), eq(planId), any(UpdateServicePlanStatusRequest.class)))
                .thenReturn(servicePlan("Premium", ServicePlanType.PREMIUM, false, false));
        when(adminServicePlanService.updateFeatures(eq(ADMIN_ID), eq(planId), any(UpdatePlanFeaturesRequest.class)))
                .thenReturn(servicePlan("Premium", ServicePlanType.PREMIUM));

        mockMvc.perform(post("/api/admin/subscription-plans")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePlanJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo gói thành công"))
                .andExpect(jsonPath("$.data.planId").value(planId.toString()))
                .andExpect(jsonPath("$.data.planName").value("Premium"));

        mockMvc.perform(get("/api/admin/subscription-plans/{planId}", planId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy thông tin gói thành công"))
                .andExpect(jsonPath("$.data.planId").value(planId.toString()))
                .andExpect(jsonPath("$.data.planType").value("PREMIUM"));

        mockMvc.perform(patch("/api/admin/subscription-plans/{planId}", planId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planName": "Premium Plus",
                                  "monthlyPrice": 250000,
                                  "maxWorkspaces": 12
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật gói thành công"))
                .andExpect(jsonPath("$.data.planName").value("Premium Plus"));

        mockMvc.perform(patch("/api/admin/subscription-plans/{planId}/status", planId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "publicVisible": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái gói thành công"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.publicVisible").value(false));

        mockMvc.perform(put("/api/admin/subscription-plans/{planId}/features", planId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFeaturesJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật tính năng của gói thành công"))
                .andExpect(jsonPath("$.data.features[0].featureKey").value("AI_TUTOR"))
                .andExpect(jsonPath("$.data.features[0].enabled").value(true));

        verify(adminServicePlanService).createPlan(eq(ADMIN_ID), any(CreateServicePlanRequest.class));
        verify(adminServicePlanService).getPlan(planId);
        verify(adminServicePlanService).updatePlan(eq(ADMIN_ID), eq(planId), any(UpdateServicePlanRequest.class));
        verify(adminServicePlanService).updateStatus(eq(ADMIN_ID), eq(planId), any(UpdateServicePlanStatusRequest.class));
        verify(adminServicePlanService).updateFeatures(eq(ADMIN_ID), eq(planId), any(UpdatePlanFeaturesRequest.class));
    }

    @Test
    void validationErrorsDoNotCallService() throws Exception {
        mockMvc.perform(post("/api/admin/subscription-plans")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Missing required fields"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        mockMvc.perform(put("/api/admin/subscription-plans/{planId}/features", planId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "features": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        verify(adminServicePlanService, never()).createPlan(any(), any());
        verify(adminServicePlanService, never()).updateFeatures(any(), any(), any());
    }

    @Test
    void businessErrorsAreMapped() throws Exception {
        when(adminServicePlanService.getPlan(planId))
                .thenThrow(new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND));
        when(adminServicePlanService.createPlan(eq(ADMIN_ID), any(CreateServicePlanRequest.class)))
                .thenThrow(new AppException(ErrorCode.VALIDATION_ERROR, "Loại gói đã được sử dụng"));

        mockMvc.perform(get("/api/admin/subscription-plans/{planId}", planId).with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.path").value("/api/admin/subscription-plans/" + planId));

        mockMvc.perform(post("/api/admin/subscription-plans")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePlanJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Loại gói đã được sử dụng"));
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

    private String validCreatePlanJson() {
        return """
                {
                  "planName": "Premium",
                  "description": "Best plan",
                  "benefits": ["AI tutor", "Community rooms"],
                  "planType": "PREMIUM",
                  "badgeColor": "#7C3AED",
                  "badgeIcon": "crown",
                  "animationType": "sparkle",
                  "monthlyPrice": 200000,
                  "currency": "VND",
                  "maxWorkspaces": 10,
                  "maxUploads": 100,
                  "maxCommunityRooms": 5,
                  "aiGenerateLimit": 100,
                  "maxFileMb": 100,
                  "maxWorkspaceMb": 1000,
                  "active": true,
                  "publicVisible": true,
                  "sortOrder": 3,
                  "features": [
                    {
                      "featureKey": "AI_TUTOR",
                      "enabled": true
                    }
                  ]
                }
                """;
    }

    private String validFeaturesJson() {
        return """
                {
                  "features": [
                    {
                      "featureKey": "AI_TUTOR",
                      "enabled": true
                    }
                  ]
                }
                """;
    }

    private ServicePlanResponse servicePlan(String planName, ServicePlanType planType) {
        return servicePlan(planName, planType, true, true);
    }

    private ServicePlanResponse servicePlan(
            String planName,
            ServicePlanType planType,
            boolean active,
            boolean publicVisible
    ) {
        return ServicePlanResponse.builder()
                .planId(planId)
                .planName(planName)
                .description("Best plan")
                .benefits(List.of("AI tutor", "Community rooms"))
                .planType(planType)
                .badgeColor("#7C3AED")
                .badgeIcon("crown")
                .animationType("sparkle")
                .monthlyPrice(new BigDecimal("200000"))
                .currency("VND")
                .quotas(ServicePlanQuotaResponse.builder()
                        .maxWorkspaces(10)
                        .maxUploads(100)
                        .maxCommunityRooms(5)
                        .aiGenerateLimit(100)
                        .maxFileMb(100)
                        .maxWorkspaceMb(1000)
                        .build())
                .active(active)
                .publicVisible(publicVisible)
                .sortOrder(3)
                .features(List.of(ServicePlanFeatureResponse.builder()
                        .featureId(featureId)
                        .featureKey("AI_TUTOR")
                        .featureName("AI Tutor")
                        .description("Ask tutor questions")
                        .active(true)
                        .enabled(true)
                        .build()))
                .build();
    }

    private FeatureCatalogResponse featureCatalog() {
        return FeatureCatalogResponse.builder()
                .featureId(featureId)
                .featureKey("AI_TUTOR")
                .featureName("AI Tutor")
                .description("Ask tutor questions")
                .active(true)
                .build();
    }

    private AdminAuditLogResponse auditLog() {
        return AdminAuditLogResponse.builder()
                .logId(auditLogId)
                .adminUserId(ADMIN_ID)
                .adminEmail("admin-service-plan-flow@example.com")
                .entityType(BusinessEntityType.SERVICE_PLAN)
                .entityId(planId)
                .actionType(BusinessActionType.SERVICE_PLAN_UPDATED)
                .title("Cập nhật gói dịch vụ")
                .description("Admin cập nhật thông tin gói Premium")
                .oldValue("{\"planName\":\"Premium\"}")
                .newValue("{\"planName\":\"Premium Plus\"}")
                .metadata("{\"planType\":\"PREMIUM\"}")
                .createdAt(Instant.parse("2026-06-23T10:00:00Z"))
                .build();
    }
}
