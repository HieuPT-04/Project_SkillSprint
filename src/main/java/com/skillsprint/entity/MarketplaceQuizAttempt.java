package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.MarketplaceQuizAttemptType;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketplace_quiz_attempts")
public class MarketplaceQuizAttempt extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id")
    private UUID attemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private MarketplaceItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_type", nullable = false, length = 30)
    private MarketplaceQuizAttemptType attemptType;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "duration_seconds", nullable = false)
    private Long durationSeconds;

    @Column(nullable = false)
    private boolean suspicious;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
