package com.skillsprint.mapper;

import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.entity.PomodoroSession;
import com.skillsprint.entity.StudySession;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.session.PomodoroSessionStatus;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class StudySessionMapper {

    public StudySessionResponse toResponse(StudySession session) {
        return toResponse(session, null, null);
    }

    public StudySessionResponse toResponse(
            StudySession session,
            PomodoroSession pomodoro,
            Integer remainingSeconds
    ) {
        return toResponse(session, pomodoro, remainingSeconds, resolveTaskCompleted(session), null);
    }

    public StudySessionResponse toResponse(
            StudySession session,
            PomodoroSession pomodoro,
            Integer remainingSeconds,
            Boolean taskCompleted,
            Integer minimumRequiredMinutes
    ) {
        return StudySessionResponse.builder()
                .sessionId(session.getSessionId())
                .workspaceId(session.getWorkspace().getWorkspaceId())
                .calendarTaskId(session.getCalendarTask() == null ? null : session.getCalendarTask().getTaskId())
                .roadmapStepId(session.getRoadmapStep() == null ? null : session.getRoadmapStep().getStepId())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .durationMinutes(session.getDurationMinutes())
                .taskCompleted(taskCompleted)
                .minimumRequiredMinutes(minimumRequiredMinutes)
                .notes(session.getNotes())
                .focusScore(session.getFocusScore())
                .pomodoro(toPomodoroResponse(pomodoro, remainingSeconds))
                .build();
    }

    private StudySessionResponse.PomodoroTimerResponse toPomodoroResponse(
            PomodoroSession pomodoro,
            Integer remainingSeconds
    ) {
        if (pomodoro == null) {
            return null;
        }

        Integer resolvedRemainingSeconds = remainingSeconds;
        if (resolvedRemainingSeconds == null) {
            resolvedRemainingSeconds = calculateRemainingSeconds(pomodoro);
        }

        return StudySessionResponse.PomodoroTimerResponse.builder()
                .pomodoroId(pomodoro.getPomodoroId())
                .status(pomodoro.getStatus())
                .currentPhase(pomodoro.getCurrentPhase())
                .currentCycle(pomodoro.getCurrentCycle())
                .totalCycles(pomodoro.getTotalCycles())
                .focusMinutes(pomodoro.getFocusMinutes())
                .shortBreakMinutes(pomodoro.getShortBreakMinutes())
                .longBreakMinutes(pomodoro.getLongBreakMinutes())
                .remainingSeconds(resolvedRemainingSeconds)
                .phaseStartedAt(pomodoro.getPhaseStartedAt())
                .phaseEndAt(pomodoro.getPhaseEndAt())
                .startedAt(pomodoro.getStartedAt())
                .endedAt(pomodoro.getEndedAt())
                .completedFocusMinutes(pomodoro.getCompletedFocusMinutes())
                .build();
    }

    private int calculateRemainingSeconds(PomodoroSession pomodoro) {
        if (pomodoro.getStatus() == PomodoroSessionStatus.PAUSED) {
            return Math.max(0, pomodoro.getRemainingSecondsWhenPaused() == null
                    ? 0
                    : pomodoro.getRemainingSecondsWhenPaused());
        }
        if (pomodoro.getPhaseEndAt() == null || pomodoro.getStatus() == PomodoroSessionStatus.COMPLETED) {
            return 0;
        }
        return Math.max(0, (int) java.time.Duration.between(Instant.now(), pomodoro.getPhaseEndAt()).getSeconds());
    }

    private Boolean resolveTaskCompleted(StudySession session) {
        if (session.getCalendarTask() == null) {
            return null;
        }
        return session.getCalendarTask().getStatus() == CalendarTaskStatus.COMPLETED;
    }
}
