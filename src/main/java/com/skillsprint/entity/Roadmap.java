package com.skillsprint.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.skillsprint.enums.roadmap.RoadmapStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roadmaps")
public class Roadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "roadmap_id")
    private UUID roadmapId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private StudyWorkspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "structure_version_id", nullable = false)
    private LearningStructureVersion structureVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description")
    private String description;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_step_id")
    private RoadmapStep currentStep;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps = 0;

    @Column(name = "completed_steps", nullable = false)
    private Integer completedSteps = 0;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RoadmapStatus status = RoadmapStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
