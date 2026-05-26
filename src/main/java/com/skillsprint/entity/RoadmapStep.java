package com.skillsprint.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roadmap_steps")
public class RoadmapStep extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "step_id")
    private UUID stepId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private StudyWorkspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "subtitle", length = 500)
    private String subtitle;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "what_to_learn")
    private List<String> whatToLearn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_concepts")
    private List<String> keyConcepts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "learning_outcomes")
    private List<String> learningOutcomes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_focus")
    private List<String> recommendedFocus;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 20)
    private DifficultyLevel difficulty;

    @Column(name = "estimated_study_time", length = 100)
    private String estimatedStudyTime;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RoadmapStepStatus status = RoadmapStepStatus.UPCOMING;

    @Column(name = "completed_at")
    private Instant completedAt;
}
