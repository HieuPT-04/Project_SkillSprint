package com.skillsprint.service.calendar.ai;

import java.util.List;

public record AiCalendarPlanDraft(
        List<String> warnings,
        List<AiCalendarTaskSuggestion> tasks
) {
}
