package com.skillsprint.flow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.entity.SystemMaintenance;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.SystemMaintenanceRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.system.MaintenanceStateHolder;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemMaintenanceFlowTest {

    private static final String ADMIN_ID = "maintenance-admin";
    private static final String LEARNER_ID = "maintenance-learner";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SystemMaintenanceRepository maintenanceRepository;

    @Autowired
    BusinessActivityLogRepository activityLogRepository;

    @Autowired
    MaintenanceStateHolder maintenanceStateHolder;

    @BeforeEach
    void setUp() {
        activityLogRepository.deleteAll();
        maintenanceRepository.deleteAll();
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.save(user(ADMIN_ID, "maintenance-admin@example.com", "Maintenance Admin"));
        userRepository.save(user(LEARNER_ID, "maintenance-learner@example.com", "Maintenance Learner"));
        maintenanceStateHolder.invalidate();
    }

    @AfterEach
    void tearDown() {
        activityLogRepository.deleteAll();
        maintenanceRepository.deleteAll();
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        maintenanceStateHolder.invalidate();
    }

    @Test
    void anonymousUserCannotReadAdminMaintenanceEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/system/maintenance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/admin/system/maintenance"));
    }

    @Test
    void learnerCannotUpdateMaintenance() throws Exception {
        mockMvc.perform(patch("/api/admin/system/maintenance")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "message": "Maintenance"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void authenticatedUserGuardStillRejectsUnknownUserInSecurityChain() throws Exception {
        mockMvc.perform(get("/api/me")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("missing-user"))
                                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/me"));
    }

    @Test
    void invalidMaintenanceWindowReturnsValidationErrorWithoutPersistence() throws Exception {
        mockMvc.perform(patch("/api/admin/system/maintenance")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "message": "Maintenance",
                                  "startAt": "2026-06-23T10:00:00Z",
                                  "endAt": "2026-06-23T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.path").value("/api/admin/system/maintenance"));

        org.junit.jupiter.api.Assertions.assertEquals(0, maintenanceRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, activityLogRepository.count());
    }

    @Test
    void adminCanEnableAndDisableMaintenanceAcrossTheFullRequestFlow() throws Exception {
        Instant endAt = Instant.now().plusSeconds(600);

        mockMvc.perform(patch("/api/admin/system/maintenance")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "message": "  Hệ thống đang nâng cấp  ",
                                  "endAt": "%s"
                                }
                                """.formatted(endAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.message").value("Hệ thống đang nâng cấp"))
                .andExpect(jsonPath("$.data.updatedBy").value(ADMIN_ID));

        SystemMaintenance saved = maintenanceRepository.findTopByOrderByUpdatedAtDesc().orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(saved.isEnabled());
        org.junit.jupiter.api.Assertions.assertEquals("Hệ thống đang nâng cấp", saved.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals(ADMIN_ID, saved.getUpdatedBy().getUserId());
        org.junit.jupiter.api.Assertions.assertEquals(
                BusinessActionType.MAINTENANCE_ENABLED,
                activityLogRepository.findAll().get(0).getActionType()
        );

        mockMvc.perform(get("/api/me").with(learnerJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("Hệ thống đang nâng cấp"))
                .andExpect(jsonPath("$.path").value("/api/me"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maintenance").value(true))
                .andExpect(jsonPath("$.data.message").value("Hệ thống đang nâng cấp"));

        mockMvc.perform(get("/api/admin/system/maintenance").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(patch("/api/admin/system/maintenance")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.active").value(false));

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(
                        BusinessActionType.MAINTENANCE_ENABLED,
                        BusinessActionType.MAINTENANCE_DISABLED
                ),
                activityLogRepository.findAll().stream()
                        .map(log -> log.getActionType())
                        .toList()
        );

        mockMvc.perform(get("/api/me").with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(LEARNER_ID));
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
}
