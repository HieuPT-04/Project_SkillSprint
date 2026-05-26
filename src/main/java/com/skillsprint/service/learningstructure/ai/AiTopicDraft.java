package com.skillsprint.service.learningstructure.ai;

import com.skillsprint.enums.learningstructure.DifficultyLevel;
import java.util.List;

public record AiTopicDraft(
        String title,
        String summaryContent,
        List<String> whatToLearn,
        List<String> keyConcepts,
        List<String> learningOutcomes,
        List<String> recommendedFocus,
        DifficultyLevel difficulty,
        Integer estimatedMinutes,
        List<String> sourceChunkIds
) {
}
