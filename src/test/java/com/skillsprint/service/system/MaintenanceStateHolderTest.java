package com.skillsprint.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.SystemMaintenance;
import com.skillsprint.repository.SystemMaintenanceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MaintenanceStateHolderTest {

    @Mock
    SystemMaintenanceRepository maintenanceRepository;

    MaintenanceStateHolder stateHolder;

    @BeforeEach
    void setUp() {
        stateHolder = new MaintenanceStateHolder(maintenanceRepository);
    }

    @Test
    void snapshotIsInactiveWhenDisabled() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        MaintenanceStateHolder.Snapshot snapshot = new MaintenanceStateHolder.Snapshot(
                false,
                "Maintenance",
                now.minusSeconds(60),
                now.plusSeconds(60),
                0
        );

        assertFalse(snapshot.isActive(now));
    }

    @Test
    void snapshotUsesInclusiveStartAndEndBoundaries() {
        Instant startAt = Instant.parse("2026-06-23T08:00:00Z");
        Instant endAt = startAt.plusSeconds(60);
        MaintenanceStateHolder.Snapshot snapshot = new MaintenanceStateHolder.Snapshot(
                true,
                "Maintenance",
                startAt,
                endAt,
                0
        );

        assertTrue(snapshot.isActive(startAt));
        assertTrue(snapshot.isActive(endAt));
        assertFalse(snapshot.isActive(startAt.minusMillis(1)));
        assertFalse(snapshot.isActive(endAt.plusMillis(1)));
    }

    @Test
    void readsRepositoryOnlyOnceWhileSnapshotIsWarm() {
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(maintenance(true, "Scheduled maintenance", null, null)));

        assertTrue(stateHolder.isActive());
        assertEquals("Scheduled maintenance", stateHolder.getMessage());
        assertNull(stateHolder.getEndAt());

        verify(maintenanceRepository, times(1)).findTopByOrderByUpdatedAtDesc();
    }

    @Test
    void invalidateForcesNextReadToReloadRepository() {
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(maintenance(false, null, null, null)))
                .thenReturn(Optional.of(maintenance(true, "Enabled", null, null)));

        assertFalse(stateHolder.isActive());

        stateHolder.invalidate();

        assertTrue(stateHolder.isActive());
        assertEquals("Enabled", stateHolder.getMessage());
        verify(maintenanceRepository, times(2)).findTopByOrderByUpdatedAtDesc();
    }

    @Test
    void usesDefaultMessageWhenPersistedMessageIsBlank() {
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(maintenance(true, "   ", null, null)));

        assertEquals(MaintenanceStateHolder.DEFAULT_MESSAGE, stateHolder.getMessage());
    }

    @Test
    void failsOpenWhenInitialRepositoryReadThrows() {
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc())
                .thenThrow(new IllegalStateException("database unavailable"));

        assertFalse(stateHolder.isActive());
        assertEquals(MaintenanceStateHolder.DEFAULT_MESSAGE, stateHolder.getMessage());
    }

    @Test
    void keepsLastKnownSnapshotWhenExpiredReloadThrows() {
        when(maintenanceRepository.findTopByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(maintenance(true, "Last known message", null, null)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertTrue(stateHolder.isActive());

        @SuppressWarnings("unchecked")
        AtomicReference<MaintenanceStateHolder.Snapshot> cache =
                (AtomicReference<MaintenanceStateHolder.Snapshot>) ReflectionTestUtils.getField(stateHolder, "cache");
        MaintenanceStateHolder.Snapshot warmSnapshot = cache.get();
        cache.set(new MaintenanceStateHolder.Snapshot(
                warmSnapshot.enabled(),
                warmSnapshot.message(),
                warmSnapshot.startAt(),
                warmSnapshot.endAt(),
                System.currentTimeMillis() - MaintenanceStateHolder.TTL_MILLIS - 1
        ));

        assertTrue(stateHolder.isActive());
        assertEquals("Last known message", stateHolder.getMessage());
        verify(maintenanceRepository, times(2)).findTopByOrderByUpdatedAtDesc();
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
