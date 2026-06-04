package com.skillsprint.service.quiz.ai;

import java.util.List;

public record AiQuizDraft(
        List<AiQuizQuestionDraft> questions
) {
}
