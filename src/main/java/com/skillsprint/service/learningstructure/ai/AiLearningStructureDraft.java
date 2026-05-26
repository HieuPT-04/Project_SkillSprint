package com.skillsprint.service.learningstructure.ai;

import java.math.BigDecimal;
import java.util.List;

public record AiLearningStructureDraft(
        BigDecimal confidenceScore,
        List<String> warnings,
        List<AiChapterDraft> chapters
) {
}
