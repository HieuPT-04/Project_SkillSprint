package com.skillsprint.service.quiz.ai;

import java.util.List;

public record AiQuizQuestionDraft(
        String type,
        String question,
        List<AiQuizOptionDraft> options,
        String correctLabel,
        String explanation
) {
}
