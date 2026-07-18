package com.skillsprint.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "marketplace_version_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_marketplace_version_progress_buyer_version",
                columnNames = {"buyer_id", "pack_version_id"}
        )
)
public class MarketplaceVersionProgress extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "progress_id")
    private UUID progressId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_version_id", nullable = false)
    private MarketplacePackVersion packVersion;

    @Column(name = "completed_quiz_count", nullable = false)
    private Integer completedQuizCount = 0;

    @Column(name = "completed_chapter_count", nullable = false)
    private Integer completedChapterCount = 0;

    @Column(name = "completion_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercent = BigDecimal.ZERO;

    @Column(name = "first_activity_at")
    private Instant firstActivityAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;
}
