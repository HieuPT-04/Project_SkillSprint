package com.skillsprint.dto.response.calendar;

import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskSource;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CalendarTaskResponse {

    UUID taskId;
    UUID workspaceId;
    UUID roadmapId;
    UUID roadmapStepId;
    String title;
    String description;
    LocalDate taskDate;
    LocalTime startTime;
    LocalTime endTime;
    Integer durationMinutes;
    CalendarTaskCategory category;
    CalendarTaskPriority priority;
    CalendarTaskStatus status;
    CalendarTaskSource source;
    Instant completedAt;
    Instant createdAt;
    Instant updatedAt;
}
