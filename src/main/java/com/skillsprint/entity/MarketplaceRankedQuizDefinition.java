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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "marketplace_ranked_quiz_definitions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_marketplace_ranked_definition_version",
                columnNames = "pack_version_id"
        )
)
public class MarketplaceRankedQuizDefinition extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "definition_id")
    private UUID definitionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_version_id", nullable = false)
    private MarketplacePackVersion packVersion;

    @Column(name = "questions_per_step", nullable = false)
    private Integer questionsPerStep;

    @Column(name = "total_question_count", nullable = false)
    private Integer totalQuestionCount;

    @Column(name = "daily_attempt_limit", nullable = false)
    private Integer dailyAttemptLimit;
}
