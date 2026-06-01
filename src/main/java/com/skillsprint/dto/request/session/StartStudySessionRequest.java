package com.skillsprint.dto.request.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StartStudySessionRequest {

    boolean usePomodoro;

    @Min(5)
    @Max(120)
    Integer focusMinutes;

    @Min(1)
    @Max(60)
    Integer shortBreakMinutes;

    @Min(5)
    @Max(90)
    Integer longBreakMinutes;

    @Min(1)
    @Max(12)
    Integer totalCycles;
}
