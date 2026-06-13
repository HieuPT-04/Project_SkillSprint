package com.skillsprint.service.system;

import com.skillsprint.repository.SystemMaintenanceRepository;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In-memory, TTL-bounded cache for the maintenance configuration.
 *
 * <p>The {@link MaintenanceModeFilter} runs on every non-public request, so it must not touch the
 * database per-call. This holder caches the raw config for {@link #TTL_MILLIS} and recomputes the
 * active flag against {@code now} on each read — that way a <em>scheduled</em> window flips on/off
 * by time alone without waiting for a DB change, while the DB is hit at most once per TTL.</p>
 *
 * <p><b>Fail-open:</b> maintenance mode is often enabled <em>during</em> DB migrations. If the
 * reload query throws (DB unreachable), we never propagate the exception — we reuse the last
 * known-good snapshot, or fall back to "not in maintenance", so the filter can never turn a DB
 * blip into a global HTTP 500.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaintenanceStateHolder {

    static final long TTL_MILLIS = 15_000L;
    static final String DEFAULT_MESSAGE = "SkillSprint đang bảo trì. Vui lòng quay lại sau.";

    SystemMaintenanceRepository maintenanceRepository;

    AtomicReference<Snapshot> cache = new AtomicReference<>();

    /** Immutable point-in-time copy of the persisted config. {@code active} is derived per-read. */
    public record Snapshot(
            boolean enabled,
            String message,
            Instant startAt,
            Instant endAt,
            long loadedAtMillis
    ) {
        boolean isActive(Instant now) {
            if (!enabled) {
                return false;
            }
            if (startAt != null && now.isBefore(startAt)) {
                return false;
            }
            return endAt == null || !now.isAfter(endAt);
        }
    }

    /** Hot-path check used by the filter. Pure in-memory once the snapshot is warm. */
    public boolean isActive() {
        return current().isActive(Instant.now());
    }

    /** Broadcast message for the 503 body, with a safe default when unset. */
    public String getMessage() {
        String message = current().message();
        return (message == null || message.isBlank()) ? DEFAULT_MESSAGE : message;
    }

    /** Expected end of the window (nullable) — used to compute the {@code Retry-After} header. */
    public Instant getEndAt() {
        return current().endAt();
    }

    /** Eager eviction: forces the next read to reload from the DB. Call right after a config save. */
    public void invalidate() {
        cache.set(null);
    }

    private Snapshot current() {
        Snapshot snapshot = cache.get();
        if (snapshot == null || System.currentTimeMillis() - snapshot.loadedAtMillis() > TTL_MILLIS) {
            snapshot = reload(snapshot);
            cache.set(snapshot);
        }
        return snapshot;
    }

    private Snapshot reload(Snapshot previous) {
        try {
            return maintenanceRepository.findTopByOrderByUpdatedAtDesc()
                    .map(m -> new Snapshot(
                            m.isEnabled(),
                            m.getMessage(),
                            m.getStartAt(),
                            m.getEndAt(),
                            System.currentTimeMillis()))
                    .orElseGet(this::empty);
        } catch (Exception ex) {
            // FAIL-OPEN. Refresh the timestamp so we retry after the next TTL instead of
            // hammering a struggling DB on every request while it is unreachable.
            log.warn("Maintenance state reload failed; failing open with last-known snapshot", ex);
            if (previous != null) {
                return new Snapshot(
                        previous.enabled(),
                        previous.message(),
                        previous.startAt(),
                        previous.endAt(),
                        System.currentTimeMillis());
            }
            return empty();
        }
    }

    private Snapshot empty() {
        return new Snapshot(false, null, null, null, System.currentTimeMillis());
    }
}
