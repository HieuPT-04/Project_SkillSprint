package com.skillsprint.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
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
import java.time.LocalDate;
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
@Table(
        name = "marketplace_ranked_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_marketplace_ranked_attempt_sequence",
                columnNames = {"buyer_id", "pack_version_id", "attempt_date", "attempt_number"}
        )
)
public class MarketplaceRankedAttempt extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id")
    private UUID attemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_version_id", nullable = false)
    private MarketplacePackVersion packVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private MarketplaceRankedQuizDefinition definition;

    @Column(name = "attempt_date", nullable = false)
    private LocalDate attemptDate;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketplaceRankedAttemptStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode questionSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode answerSnapshot;

    @Column
    private Integer score;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(nullable = false)
    private boolean suspicious;

    @Column(name = "leaderboard_eligible", nullable = false)
    private boolean leaderboardEligible;
}
