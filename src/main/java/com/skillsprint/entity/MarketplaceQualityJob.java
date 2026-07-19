package com.skillsprint.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketplace_quality_jobs")
public class MarketplaceQualityJob extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "job_id")
    private UUID jobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_version_id", nullable = false)
    private MarketplacePackVersion packVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketplaceQualityJobStatus status = MarketplaceQualityJobStatus.QUEUED;

    @Column(name = "snapshot_fingerprint", nullable = false, length = 64)
    private String snapshotFingerprint;

    @Column
    private Integer score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_json", columnDefinition = "jsonb")
    private JsonNode report;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 2;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
