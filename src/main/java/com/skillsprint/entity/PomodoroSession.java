package com.skillsprint.entity;

import java.time.Instant;
import java.util.UUID;

import com.skillsprint.enums.session.PomodoroPhase;
import com.skillsprint.enums.session.PomodoroSessionStatus;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pomodoro_sessions")
public class PomodoroSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pomodoro_id")
    private UUID pomodoroId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_task_id")
    private CalendarTask calendarTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_step_id")
    private RoadmapStep roadmapStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_session_id")
    private StudySession studySession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "focus_minutes", nullable = false)
    private Integer focusMinutes = 25;

    @Column(name = "short_break_minutes", nullable = false)
    private Integer shortBreakMinutes = 5;

    @Column(name = "long_break_minutes", nullable = false)
    private Integer longBreakMinutes = 15;

    @Column(name = "total_cycles", nullable = false)
    private Integer totalCycles = 4;

    @Column(name = "current_cycle", nullable = false)
    private Integer currentCycle = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false, length = 20)
    private PomodoroPhase currentPhase = PomodoroPhase.FOCUS;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PomodoroSessionStatus status = PomodoroSessionStatus.IN_PROGRESS;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "phase_started_at")
    private Instant phaseStartedAt;

    @Column(name = "phase_end_at")
    private Instant phaseEndAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "remaining_seconds_when_paused")
    private Integer remainingSecondsWhenPaused;

    @Column(name = "completed_focus_minutes")
    private Integer completedFocusMinutes = 0;
}
