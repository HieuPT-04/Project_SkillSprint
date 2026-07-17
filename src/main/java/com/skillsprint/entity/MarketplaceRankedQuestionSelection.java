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
        name = "marketplace_ranked_question_selections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_marketplace_ranked_selection_question",
                        columnNames = {"definition_id", "source_step_key", "question_id"}
                ),
                @UniqueConstraint(
                        name = "uq_marketplace_ranked_selection_order",
                        columnNames = {"definition_id", "source_step_key", "selection_order"}
                )
        }
)
public class MarketplaceRankedQuestionSelection extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "selection_id")
    private UUID selectionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private MarketplaceRankedQuizDefinition definition;

    @Column(name = "source_step_key", nullable = false, length = 100)
    private String sourceStepKey;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "selection_order", nullable = false)
    private Integer selectionOrder;
}
