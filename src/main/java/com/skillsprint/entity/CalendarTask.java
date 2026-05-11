package com.skillsprint.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.skillsprint.enums.CalendarTaskCategory;
import com.skillsprint.enums.CalendarTaskPriority;
import com.skillsprint.enums.CalendarTaskSource;
import com.skillsprint.enums.CalendarTaskStatus;
import com.skillsprint.enums.ClassifiedBy;
import com.skillsprint.enums.EisenhowerQuadrant;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "calendar_tasks")
public class CalendarTask extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "task_id")
    private UUID taskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private StudyWorkspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_step_id")
    private RoadmapStep roadmapStep;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private CalendarTaskCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private CalendarTaskPriority priority = CalendarTaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CalendarTaskStatus status = CalendarTaskStatus.TODO;

    @Column(name = "importance_score", precision = 5, scale = 2)
    private BigDecimal importanceScore;

    @Column(name = "urgency_score", precision = 5, scale = 2)
    private BigDecimal urgencyScore;

    @Column(name = "is_important")
    private Boolean important;

    @Column(name = "is_urgent")
    private Boolean urgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "eisenhower_quadrant", length = 30)
    private EisenhowerQuadrant eisenhowerQuadrant;

    @Column(name = "classification_reason")
    private String classificationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "classified_by", length = 30)
    private ClassifiedBy classifiedBy;

    @Column(name = "classified_at")
    private Instant classifiedAt;

    @Column(name = "xp_reward")
    private Integer xpReward;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 30)
    private CalendarTaskSource source;

    @Column(name = "completed_at")
    private Instant completedAt;
}
