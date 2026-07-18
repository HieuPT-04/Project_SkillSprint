package com.skillsprint.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
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
@Table(name = "marketplace_practice_attempts")
public class MarketplacePracticeAttempt extends BaseAuditEntity {

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

    @Column(name = "chapter_sequence_no", nullable = false)
    private Integer chapterSequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketplacePracticeAttemptStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_snapshot_json", nullable = false)
    private JsonNode questionSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_snapshot_json", nullable = false)
    private JsonNode answerSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_answers_json")
    private JsonNode submittedAnswers;

    @Column
    private Integer score;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;
}
