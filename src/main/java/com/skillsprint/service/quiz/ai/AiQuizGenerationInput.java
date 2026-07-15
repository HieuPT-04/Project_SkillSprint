package com.skillsprint.service.quiz.ai;

import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.RoadmapStep;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of everything the Gemini prompt needs, captured while a
 * database transaction is still open. The non-transactional generation phase
 * must only ever see this value object — never lazy JPA entities.
 */
public record AiQuizGenerationInput(
        UUID stepId,
        String title,
        String subtitle,
        String summary,
        List<String> keyConcepts,
        List<String> learningOutcomes,
        List<Chunk> chunks
) {

    public record Chunk(String sectionTitle, String content) {
    }

    public static AiQuizGenerationInput from(RoadmapStep step, List<MaterialChunk> chunks) {
        return new AiQuizGenerationInput(
                step.getStepId(),
                step.getTitle(),
                step.getSubtitle(),
                step.getSummary(),
                copyOf(step.getKeyConcepts()),
                copyOf(step.getLearningOutcomes()),
                chunks == null ? List.of() : chunks.stream()
                        .map(chunk -> new Chunk(chunk.getSectionTitle(), chunk.getContent()))
                        .toList()
        );
    }

    private static List<String> copyOf(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
