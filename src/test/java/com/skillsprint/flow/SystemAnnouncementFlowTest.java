package com.skillsprint.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.entity.SystemAnnouncement;
import com.skillsprint.entity.SystemMaintenance;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.system.AnnouncementType;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.SystemAnnouncementRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemAnnouncementFlowTest {

    private static final String ADMIN_ID = "announcement-admin";
    private static final String LEARNER_ID = "announcement-learner";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SystemAnnouncementRepository announcementRepository;

    @Autowired
    SystemMaintenanceRepository maintenanceRepository;

    @Autowired
    BusinessActivityLogRepository activityLogRepository;

    @Autowired
    MaintenanceStateHolder maintenanceStateHolder;

    @BeforeEach
    void setUp() {
        activityLogRepository.deleteAll();
        announcementRepository.deleteAll();
        maintenanceRepository.deleteAll();
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.save(user(ADMIN_ID, "announcement-admin@example.com", "Announcement Admin"));
        userRepository.save(user(LEARNER_ID, "announcement-learner@example.com", "Announcement Learner"));
        maintenanceStateHolder.invalidate();
    }

    @AfterEach
    void tearDown() {
        activityLogRepository.deleteAll();
        announcementRepository.deleteAll();
        maintenanceRepository.deleteAll();
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        maintenanceStateHolder.invalidate();
    }

    @Test
    void publicAnnouncementEndpointDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/public/announcements/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.type").value("INFO"));
    }

    @Test
    void anonymousAndLearnerCannotUpdateAnnouncement() throws Exception {
        String body = """
                {
                  "enabled": true,
                  "message": "Announcement"
                }
                """;

        mockMvc.perform(patch("/api/admin/system/announcement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/system/announcement")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidAnnouncementPayloadDoesNotPersistDataOrAuditLog() throws Exception {
        mockMvc.perform(patch("/api/admin/system/announcement")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "message": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(0, announcementRepository.count());
        assertEquals(0, activityLogRepository.count());
    }

    @Test
    void adminCanPublishAndDisableAnnouncementAcrossTheFullRequestFlow() throws Exception {
        Instant endAt = Instant.now().plusSeconds(600);

        mockMvc.perform(patch("/api/admin/system/announcement")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "title": "  Cập nhật hệ thống  ",
                                  "message": "  Phiên bản mới đã sẵn sàng  ",
                                  "type": "MAINTENANCE",
                                  "endAt": "%s"
                                }
                                """.formatted(endAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.title").value("Cập nhật hệ thống"))
                .andExpect(jsonPath("$.data.message").value("Phiên bản mới đã sẵn sàng"))
                .andExpect(jsonPath("$.data.type").value("MAINTENANCE"))
                .andExpect(jsonPath("$.data.updatedBy").value(ADMIN_ID));

        SystemAnnouncement saved = announcementRepository.findTopByOrderByUpdatedAtDesc().orElseThrow();
        assertTrue(saved.isEnabled());
        assertEquals("Cập nhật hệ thống", saved.getTitle());
        assertEquals(AnnouncementType.MAINTENANCE, saved.getType());
        assertEquals(ADMIN_ID, saved.getUpdatedBy().getUserId());
        assertEquals(
                BusinessActionType.ANNOUNCEMENT_ENABLED,
                activityLogRepository.findAll().get(0).getActionType()
        );

        mockMvc.perform(get("/api/public/announcements/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.title").value("Cập nhật hệ thống"))
                .andExpect(jsonPath("$.data.message").value("Phiên bản mới đã sẵn sàng"))
                .andExpect(jsonPath("$.data.type").value("MAINTENANCE"));

        mockMvc.perform(get("/api/admin/system/announcement").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy thông báo công khai thành công"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.title").value("Cập nhật hệ thống"));

        mockMvc.perform(patch("/api/admin/system/announcement")
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

        mockMvc.perform(get("/api/public/announcements/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.title").doesNotExist())
                .andExpect(jsonPath("$.data.message").doesNotExist());

        assertEquals(
                List.of(
                        BusinessActionType.ANNOUNCEMENT_ENABLED,
                        BusinessActionType.ANNOUNCEMENT_DISABLED
                ),
                activityLogRepository.findAll().stream()
                        .map(log -> log.getActionType())
                        .toList()
        );
    }

    @Test
    void publicAnnouncementRemainsAvailableDuringMaintenance() throws Exception {
        SystemAnnouncement announcement = new SystemAnnouncement();
        announcement.setEnabled(true);
        announcement.setTitle("Public announcement");
        announcement.setMessage("Still visible");
        announcement.setType(AnnouncementType.INFO);
        announcementRepository.save(announcement);

        SystemMaintenance maintenance = new SystemMaintenance();
        maintenance.setEnabled(true);
        maintenance.setMessage("Maintenance");
        maintenanceRepository.save(maintenance);
        maintenanceStateHolder.invalidate();

        mockMvc.perform(get("/api/public/announcements/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.message").value("Still visible"));

        mockMvc.perform(get("/api/me").with(learnerJwt()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void scheduledAnnouncementDoesNotExposeContentPublicly() throws Exception {
        SystemAnnouncement announcement = new SystemAnnouncement();
        announcement.setEnabled(true);
        announcement.setTitle("Future title");
        announcement.setMessage("Future message");
        announcement.setType(AnnouncementType.WARNING);
        announcement.setStartAt(Instant.now().plusSeconds(300));
        announcement.setEndAt(Instant.now().plusSeconds(600));
        announcementRepository.save(announcement);

        mockMvc.perform(get("/api/public/announcements/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.type").value("WARNING"))
                .andExpect(jsonPath("$.data.title").doesNotExist())
                .andExpect(jsonPath("$.data.message").doesNotExist());
    }

    private RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(ADMIN_ID).claim("cognito:groups", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor learnerJwt() {
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
