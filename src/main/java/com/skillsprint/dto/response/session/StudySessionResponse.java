package com.skillsprint.dto.response.session;

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
    String notes;
    Integer focusScore;
}
