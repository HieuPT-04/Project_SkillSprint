package com.skillsprint.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.skillsprint.enums.CalendarScheduleRunStatus;
import com.skillsprint.enums.CalendarScheduleScope;
import com.skillsprint.enums.WeekDay;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "calendar_schedule_runs")
public class CalendarScheduleRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "run_id")
    private UUID runId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private StudyWorkspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_scope", length = 30)
    private CalendarScheduleScope scheduleScope;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "available_days")
    private List<WeekDay> availableDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "available_time_windows")
    private String availableTimeWindows;

    @Column(name = "preferred_session_minutes")
    private Integer preferredSessionMinutes;

    @Column(name = "max_sessions_per_day")
    private Integer maxSessionsPerDay;

    @Column(name = "include_review_sessions", nullable = false)
    private boolean includeReviewSessions = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CalendarScheduleRunStatus status = CalendarScheduleRunStatus.PREVIEW;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "generated_tasks")
    private String generatedTasks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
