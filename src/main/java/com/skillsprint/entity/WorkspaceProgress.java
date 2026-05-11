package com.skillsprint.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workspace_progress")
public class WorkspaceProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "progress_id")
    private UUID progressId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, unique = true)
    private StudyWorkspace workspace;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps = 0;

    @Column(name = "completed_steps", nullable = false)
    private Integer completedSteps = 0;

    @Column(name = "total_tasks", nullable = false)
    private Integer totalTasks = 0;

    @Column(name = "completed_tasks", nullable = false)
    private Integer completedTasks = 0;

    @Column(name = "completion_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercent = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "last_calculated_at", nullable = false)
    private Instant lastCalculatedAt;
}
