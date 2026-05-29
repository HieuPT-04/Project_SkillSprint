package com.skillsprint.dto.response.calendar;

import com.skillsprint.enums.calendar.CalendarScheduleRunStatus;
import com.skillsprint.enums.calendar.CalendarScheduleScope;
import com.skillsprint.enums.calendar.WeekDay;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CalendarScheduleRunResponse {

    UUID runId;
    UUID workspaceId;
    UUID roadmapId;
    CalendarScheduleScope scheduleScope;
    LocalDate startDate;
    LocalDate endDate;
    List<WeekDay> availableDays;
    Integer preferredSessionMinutes;
    Integer maxSessionsPerDay;
    boolean includeReviewSessions;
    CalendarScheduleRunStatus status;
    Instant createdAt;
    Instant confirmedAt;
    List<CalendarTaskResponse> tasks;
}
