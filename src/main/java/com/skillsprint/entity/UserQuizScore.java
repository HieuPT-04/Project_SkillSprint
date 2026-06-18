package com.skillsprint.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
        name = "user_quiz_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_quiz_scores_user_quiz",
                columnNames = {"user_id", "quiz_id"}
        )
)
public class UserQuizScore extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_quiz_score_id")
    private UUID userQuizScoreId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "best_attempt_id")
    private QuizAttempt bestAttempt;

    @Column(name = "best_score_percent", nullable = false)
    private Integer bestScorePercent = 0;

    @Column(name = "earned_points", nullable = false)
    private Integer earnedPoints = 0;
}
