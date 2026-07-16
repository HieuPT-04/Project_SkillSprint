package com.skillsprint.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Independently reviewable and purchasable content release of a {@link MarketplacePack}.
 *
 * <p>At most one version per pack may be {@link #saleable}. A partial unique index on
 * {@code (pack_id) WHERE saleable} enforces that in PostgreSQL.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "marketplace_pack_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_marketplace_pack_version_no",
                columnNames = {"pack_id", "version_no"}
        )
)
public class MarketplacePackVersion extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "version_id")
    private UUID versionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_id", nullable = false)
    private MarketplacePack pack;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MarketplacePackVersionStatus status = MarketplacePackVersionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "update_type", nullable = false, length = 10)
    private MarketplacePackUpdateType updateType = MarketplacePackUpdateType.MAJOR;

    /** See {@link MarketplacePack#getLegacyItemId()}. */
    @Column(name = "legacy_item_id", unique = true)
    private UUID legacyItemId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String subject;

    @Column(name = "price_coins", nullable = false)
    private Integer priceCoins;

    @Column(name = "creator_validation_score")
    private Integer creatorValidationScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "chapter_count", nullable = false)
    private Integer chapterCount;

    @Column(name = "quiz_count", nullable = false)
    private Integer quizCount;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false)
    private JsonNode content;

    @Column(nullable = false)
    private boolean saleable;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;
}
