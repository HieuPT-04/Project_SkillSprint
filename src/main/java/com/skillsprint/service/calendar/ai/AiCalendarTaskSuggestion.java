package com.skillsprint.service.calendar.ai;

import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import java.time.LocalDate;
import java.time.LocalTime;

public record AiCalendarTaskSuggestion(
        Integer taskIndex,
        String title,
        String description,
        LocalDate taskDate,
        LocalTime startTime,
        Integer durationMinutes,
        CalendarTaskCategory category,
        CalendarTaskPriority priority,
        String reason
) {
}
