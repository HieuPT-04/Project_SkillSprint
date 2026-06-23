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
import com.skillsprint.dto.request.admin.UpdateMaintenanceRequest;
import com.skillsprint.dto.response.admin.MaintenanceResponse;
import com.skillsprint.dto.response.common.SystemStatusResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.SystemMaintenance;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.SystemMaintenanceRepository;
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
class SystemMaintenanceServiceTest {

    @Mock
    SystemMaintenanceRepository maintenanceRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    BusinessActivityLogRepository activityLogRepository;

    @Mock
    MaintenanceStateHolder maintenanceStateHolder;

    SystemMaintenanceService maintenanceService;

    @BeforeEach
    void setUp() {
        maintenanceService = new SystemMaintenanceService(
                maintenanceRepository,
                userRepository,
                activityLogRepository,
                maintenanceStateHolder,
                new ObjectMapper()
        );
    }

    @Test
    void getMaintenanceReturnsSafeDefaultsWhenNoConfigurationExists() {
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        MaintenanceResponse response = maintenanceService.getMaintenance();

        assertFalse(response.isEnabled());
        assertFalse(response.isActive());
        assertEquals(SystemMaintenanceService.DEFAULT_MESSAGE, response.getMessage());
        assertNull(response.getStartAt());
        assertNull(response.getEndAt());
    }

    @Test
    void getSystemStatusMarksFutureWindowAsScheduled() {
        SystemMaintenance maintenance = maintenance(
                true,
                "Future maintenance",
                Instant.now().plusSeconds(300),
                Instant.now().plusSeconds(600)
        );
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(maintenance));

        SystemStatusResponse response = maintenanceService.getSystemStatus();

        assertTrue(response.isEnabled());
        assertFalse(response.isMaintenance());
        assertTrue(response.isScheduled());
    }

    @Test
    void getSystemStatusMarksCurrentWindowAsActive() {
        SystemMaintenance maintenance = maintenance(
                true,
                "Current maintenance",
                Instant.now().minusSeconds(300),
                Instant.now().plusSeconds(600)
        );
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(maintenance));

        SystemStatusResponse response = maintenanceService.getSystemStatus();

        assertTrue(response.isMaintenance());
        assertFalse(response.isScheduled());
    }

    @Test
    void updateMaintenanceNormalizesMessageClearsScheduleAndInvalidatesCache() {
        SystemMaintenance existing = maintenance(
                false,
                "Old message",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60)
        );
        existing.setMaintenanceId(UUID.randomUUID());
        UpdateMaintenanceRequest request = new UpdateMaintenanceRequest();
        request.setEnabled(true);
        request.setMessage("  New message  ");
        request.setClearSchedule(true);

        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(maintenanceRepository.save(any(SystemMaintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceResponse response = maintenanceService.updateMaintenance(null, request);

        assertTrue(response.isEnabled());
        assertTrue(response.isActive());
        assertEquals("New message", response.getMessage());
        assertNull(response.getStartAt());
        assertNull(response.getEndAt());
        verify(maintenanceStateHolder).invalidate();
    }

    @Test
    void updateMaintenanceConvertsBlankMessageToNull() {
        SystemMaintenance existing = maintenance(false, "Old message", null, null);
        UpdateMaintenanceRequest request = new UpdateMaintenanceRequest();
        request.setMessage("   ");

        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(maintenanceRepository.save(any(SystemMaintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceResponse response = maintenanceService.updateMaintenance(null, request);

        assertEquals(SystemMaintenanceService.DEFAULT_MESSAGE, response.getMessage());
        assertNull(existing.getMessage());
    }

    @Test
    void updateMaintenanceRejectsEndAtEqualToStartAt() {
        Instant boundary = Instant.parse("2026-06-23T08:00:00Z");
        UpdateMaintenanceRequest request = new UpdateMaintenanceRequest();
        request.setStartAt(boundary);
        request.setEndAt(boundary);
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> maintenanceService.updateMaintenance("admin-1", request)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(maintenanceRepository, never()).save(any());
        verify(maintenanceStateHolder, never()).invalidate();
    }

    @Test
    void updateMaintenanceAssignsAdminAndWritesEnabledAuditLog() {
        UUID maintenanceId = UUID.randomUUID();
        SystemMaintenance existing = maintenance(false, null, null, null);
        existing.setMaintenanceId(maintenanceId);
        User admin = new User();
        admin.setUserId("admin-1");
        UpdateMaintenanceRequest request = new UpdateMaintenanceRequest();
        request.setEnabled(true);
        request.setMessage("Maintenance");

        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(maintenanceRepository.save(any(SystemMaintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        maintenanceService.updateMaintenance("admin-1", request);

        assertEquals(admin, existing.getUpdatedBy());
        ArgumentCaptor<BusinessActivityLog> captor = ArgumentCaptor.forClass(BusinessActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertEquals(BusinessActionType.MAINTENANCE_ENABLED, captor.getValue().getActionType());
        assertEquals(maintenanceId, captor.getValue().getEntityId());
        assertTrue(captor.getValue().getNewValue().contains("\"enabled\":true"));
    }

    @Test
    void updateMaintenanceWritesDisabledAuditLog() {
        SystemMaintenance existing = maintenance(true, "Maintenance", null, null);
        UpdateMaintenanceRequest request = new UpdateMaintenanceRequest();
        request.setEnabled(false);

        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(maintenanceRepository.save(any(SystemMaintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        maintenanceService.updateMaintenance(null, request);

        ArgumentCaptor<BusinessActivityLog> captor = ArgumentCaptor.forClass(BusinessActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertEquals(BusinessActionType.MAINTENANCE_DISABLED, captor.getValue().getActionType());
    }

    private SystemMaintenance maintenance(
            boolean enabled,
            String message,
            Instant startAt,
            Instant endAt
    ) {
        SystemMaintenance maintenance = new SystemMaintenance();
        maintenance.setEnabled(enabled);
        maintenance.setMessage(message);
        maintenance.setStartAt(startAt);
        maintenance.setEndAt(endAt);
        return maintenance;
    }
}
