package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.MarketplaceReportCategory;
import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportTargetType;
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

/**
 * A buyer-submitted report about a marketplace pack version, chapter, question, or creator.
 *
 * <p>Reports are scoped to an immutable {@link MarketplacePackVersion} snapshot. Creating a report
 * never mutates the reported content; moderation is an explicit admin action.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketplace_content_reports")
public class MarketplaceContentReport extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id")
    private UUID reportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_version_id", nullable = false)
    private MarketplacePackVersion packVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private MarketplaceReportTargetType targetType;

    /** The chapterId/questionId within the snapshot for CHAPTER/QUESTION targets; null otherwise. */
    @Column(name = "target_ref", length = 200)
    private String targetRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private MarketplaceReportCategory category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Owner-scoped S3 object key for optional screenshot evidence; never a presigned URL. */
    @Column(name = "evidence_object_key", length = 512)
    private String evidenceObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MarketplaceReportStatus status = MarketplaceReportStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;
}
