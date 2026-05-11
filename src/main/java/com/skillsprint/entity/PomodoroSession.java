package com.skillsprint.entity;

import java.time.Instant;
import java.util.UUID;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 25;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PomodoroSessionStatus status = PomodoroSessionStatus.IN_PROGRESS;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
