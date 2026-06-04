package com.skillsprint.service.tutor.ai;

import java.util.List;

public record AiTutorDraft(
        String answer,
        List<String> suggestedQuestions,
        String confidence
) {
}
