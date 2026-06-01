package com.skillsprint.dto.response.session;

import com.skillsprint.enums.session.PomodoroPhase;
import com.skillsprint.enums.session.PomodoroSessionStatus;
import com.skillsprint.enums.session.StudySessionStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StudySessionResponse {

    UUID sessionId;
    UUID workspaceId;
    UUID calendarTaskId;
    UUID roadmapStepId;
    StudySessionStatus status;
    Instant startedAt;
    Instant endedAt;
    Integer durationMinutes;
    Boolean taskCompleted;
    Integer minimumRequiredMinutes;
    String notes;
    Integer focusScore;
    PomodoroTimerResponse pomodoro;

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PomodoroTimerResponse {
        UUID pomodoroId;
        PomodoroSessionStatus status;
        PomodoroPhase currentPhase;
        Integer currentCycle;
        Integer totalCycles;
        Integer focusMinutes;
        Integer shortBreakMinutes;
        Integer longBreakMinutes;
        Integer remainingSeconds;
        Instant phaseStartedAt;
        Instant phaseEndAt;
        Instant startedAt;
        Instant endedAt;
        Integer completedFocusMinutes;
    }
}
