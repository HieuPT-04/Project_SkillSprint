package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceQualityJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MarketplaceQualityJobRepository extends JpaRepository<MarketplaceQualityJob, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from MarketplaceQualityJob job where job.jobId = :jobId")
    Optional<MarketplaceQualityJob> findByJobIdForUpdate(@Param("jobId") UUID jobId);

    Optional<MarketplaceQualityJob> findTopByPackVersionVersionIdOrderByCreatedAtDesc(UUID versionId);

    List<MarketplaceQualityJob> findTop10ByPackVersionVersionIdOrderByCreatedAtDesc(UUID versionId);

    List<MarketplaceQualityJob> findByPackVersionVersionIdAndStatusIn(
            UUID versionId,
            List<MarketplaceQualityJobStatus> statuses
    );

    Optional<MarketplaceQualityJob> findTopByPackVersionVersionIdAndSnapshotFingerprintOrderByCreatedAtDesc(
            UUID versionId,
            String snapshotFingerprint
    );

    @Query(value = """
            SELECT *
            FROM marketplace_quality_jobs
            WHERE status = 'QUEUED'
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<MarketplaceQualityJob> findNextQueuedForUpdate(@Param("now") Instant now);

}
