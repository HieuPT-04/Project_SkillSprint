package com.skillsprint.service.calendar.ai;

import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import java.time.LocalDate;
import java.time.LocalTime;

public record AiCalendarTaskInput(
        int taskIndex,
        String title,
        String description,
        String chapterTitle,
        DifficultyLevel difficulty,
        Integer estimatedMinutes,
        CalendarTaskCategory category,
        CalendarTaskPriority priority,
        LocalDate suggestedTaskDate,
        LocalTime suggestedStartTime,
        Integer suggestedDurationMinutes
) {
}
