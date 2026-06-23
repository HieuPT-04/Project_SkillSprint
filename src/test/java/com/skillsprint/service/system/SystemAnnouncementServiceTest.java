package com.skillsprint.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.admin.UpdateAnnouncementRequest;
import com.skillsprint.dto.response.admin.AnnouncementResponse;
import com.skillsprint.dto.response.common.PublicAnnouncementResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.SystemAnnouncement;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.system.AnnouncementType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.SystemAnnouncementRepository;
import com.skillsprint.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemAnnouncementServiceTest {

    @Mock
    SystemAnnouncementRepository announcementRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    BusinessActivityLogRepository activityLogRepository;

    SystemAnnouncementService announcementService;

    @BeforeEach
    void setUp() {
        announcementService = new SystemAnnouncementService(
                announcementRepository,
                userRepository,
                activityLogRepository,
                new ObjectMapper()
        );
    }

    @Test
    void getAnnouncementReturnsSafeDefaultsWhenNoConfigurationExists() {
        when(announcementRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        AnnouncementResponse response = announcementService.getAnnouncement();

        assertFalse(response.isEnabled());
        assertFalse(response.isActive());
        assertEquals(SystemAnnouncementService.DEFAULT_TITLE, response.getTitle());
        assertEquals(AnnouncementType.INFO, response.getType());
        assertNull(response.getMessage());
    }

    @Test
    void getActivePublicAnnouncementHidesContentWhenDisabled() {
        SystemAnnouncement announcement = announcement(
                false,
                "Private title",
                "Private message",
                AnnouncementType.WARNING,
                null,
                null
        );
        when(announcementRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(announcement));

        PublicAnnouncementResponse response = announcementService.getActivePublicAnnouncement();

        assertFalse(response.isEnabled());
        assertFalse(response.isActive());
        assertEquals(AnnouncementType.WARNING, response.getType());
        assertNull(response.getTitle());
        assertNull(response.getMessage());
    }

    @Test
    void getActivePublicAnnouncementReturnsActiveContent() {
        SystemAnnouncement announcement = announcement(
                true,
                null,
                "Active message",
                null,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60)
        );
        when(announcementRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(announcement));

        PublicAnnouncementResponse response = announcementService.getActivePublicAnnouncement();

        assertTrue(response.isEnabled());
        assertTrue(response.isActive());
        assertEquals(SystemAnnouncementService.DEFAULT_TITLE, response.getTitle());
        assertEquals("Active message", response.getMessage());
        assertEquals(AnnouncementType.INFO, response.getType());
    }

    @Test
    void getActivePublicAnnouncementHidesFutureAndExpiredContent() {
        SystemAnnouncement future = announcement(
                true,
                "Future",
                "Future message",
                AnnouncementType.INFO,
                Instant.now().plusSeconds(300),
                Instant.now().plusSeconds(600)
        );
        SystemAnnouncement expired = announcement(
                true,
                "Expired",
                "Expired message",
                AnnouncementType.INFO,
                Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(300)
        );
        when(announcementRepository.findTopByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(future))
                .thenReturn(Optional.of(expired));

        PublicAnnouncementResponse futureResponse = announcementService.getActivePublicAnnouncement();
        PublicAnnouncementResponse expiredResponse = announcementService.getActivePublicAnnouncement();

        assertFalse(futureResponse.isActive());
        assertNull(futureResponse.getMessage());
        assertFalse(expiredResponse.isActive());
        assertNull(expiredResponse.getMessage());
    }

    @Test
    void updateAnnouncementNormalizesContentClearsScheduleAndUsesRequestedType() {
        SystemAnnouncement existing = announcement(
                false,
                "Old title",
                "Old message",
                AnnouncementType.INFO,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60)
        );
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest();
        request.setEnabled(true);
        request.setTitle("  New title  ");
        request.setMessage("  New message  ");
        request.setType(AnnouncementType.MAINTENANCE);
        request.setClearSchedule(true);

        when(announcementRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(announcementRepository.save(any(SystemAnnouncement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnnouncementResponse response = announcementService.updateAnnouncement(null, request);

        assertTrue(response.isEnabled());
        assertTrue(response.isActive());
        assertEquals("New title", response.getTitle());
        assertEquals("New message", response.getMessage());
        assertEquals(AnnouncementType.MAINTENANCE, response.getType());
        assertNull(response.getStartAt());
        assertNull(response.getEndAt());
    }

    @Test
    void updateAnnouncementConvertsBlankOptionalTitleToDefault() {
        SystemAnnouncement existing = announcement(
                false,
                "Old title",
                null,
                AnnouncementType.INFO,
                null,
                null
        );
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest();
        request.setTitle("   ");

        when(announcementRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(announcementRepository.save(any(SystemAnnouncement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnnouncementResponse response = announcementService.updateAnnouncement(null, request);

        assertNull(existing.getTitle());
        assertEquals(SystemAnnouncementService.DEFAULT_TITLE, response.getTitle());
    }

    @Test
    void updateAnnouncementRejectsEnabledAnnouncementWithoutMessage() {
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest();
        request.setEnabled(true);
        request.setMessage("   ");
        when(announcementRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> announcementService.updateAnnouncement("admin-1", request)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(announcementRepository, never()).save(any());
        verify(activityLogRepository, never()).save(any());
    }

    @Test
    void updateAnnouncementRejectsEndAtBeforeStartAt() {
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest();
        request.setStartAt(Instant.parse("2026-06-23T10:00:00Z"));
        request.setEndAt(Instant.parse("2026-06-23T09:59:59Z"));
        when(announcementRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> announcementService.updateAnnouncement("admin-1", request)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void updateAnnouncementAssignsAdminAndWritesEnabledAuditLog() {
        UUID announcementId = UUID.randomUUID();
        SystemAnnouncement existing = announcement(
                false,
                null,
                null,
                AnnouncementType.INFO,
                null,
                null
        );
        existing.setAnnouncementId(announcementId);
        User admin = new User();
        admin.setUserId("admin-1");
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest();
        request.setEnabled(true);
        request.setMessage("Announcement");

        when(announcementRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(announcementRepository.save(any(SystemAnnouncement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        announcementService.updateAnnouncement("admin-1", request);

        assertEquals(admin, existing.getUpdatedBy());
        ArgumentCaptor<BusinessActivityLog> captor = ArgumentCaptor.forClass(BusinessActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertEquals(BusinessActionType.ANNOUNCEMENT_ENABLED, captor.getValue().getActionType());
        assertEquals(announcementId, captor.getValue().getEntityId());
        assertTrue(captor.getValue().getNewValue().contains("\"enabled\":true"));
    }

    @Test
    void updateAnnouncementWritesDisabledAndUpdatedAuditLogs() {
        SystemAnnouncement enabled = announcement(
                true,
                "Title",
                "Message",
                AnnouncementType.INFO,
                null,
                null
        );
        UpdateAnnouncementRequest disableRequest = new UpdateAnnouncementRequest();
        disableRequest.setEnabled(false);
        UpdateAnnouncementRequest updateRequest = new UpdateAnnouncementRequest();
        updateRequest.setTitle("Updated title");

        when(announcementRepository.findTopByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(enabled))
                .thenReturn(Optional.of(enabled));
        when(announcementRepository.save(any(SystemAnnouncement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        announcementService.updateAnnouncement(null, disableRequest);
        announcementService.updateAnnouncement(null, updateRequest);

        ArgumentCaptor<BusinessActivityLog> captor = ArgumentCaptor.forClass(BusinessActivityLog.class);
        verify(activityLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(BusinessActionType.ANNOUNCEMENT_DISABLED, captor.getAllValues().get(0).getActionType());
        assertEquals(BusinessActionType.ANNOUNCEMENT_UPDATED, captor.getAllValues().get(1).getActionType());
    }

    private SystemAnnouncement announcement(
            boolean enabled,
            String title,
            String message,
            AnnouncementType type,
            Instant startAt,
            Instant endAt
    ) {
        SystemAnnouncement announcement = new SystemAnnouncement();
        announcement.setEnabled(enabled);
        announcement.setTitle(title);
        announcement.setMessage(message);
        announcement.setType(type);
        announcement.setStartAt(startAt);
        announcement.setEndAt(endAt);
        return announcement;
    }
}
