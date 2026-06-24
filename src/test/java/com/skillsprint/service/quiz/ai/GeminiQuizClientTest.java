package com.skillsprint.service.quiz.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.ai.GeminiProperties;
import com.skillsprint.entity.MaterialChunk;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GeminiQuizClientTest {

    private final GeminiQuizClient client = new GeminiQuizClient(
            new GeminiProperties(true, "key", "model", "http://localhost", 18000),
            new ObjectMapper(),
            RestClient.builder()
    );

    @Test
    void validateDraftAcceptsValidSingleChoiceDraftWithFiveQuestions() {
        AiQuizDraft draft = new AiQuizDraft(List.of(
                validSingleChoice(1),
                validSingleChoice(2),
                validSingleChoice(3),
                validSingleChoice(4),
                validSingleChoice(5)
        ));

        assertNotNull(client.validateDraft(draft));
    }

    @Test
    void validateDraftRejectsDraftWithFewerThanFiveQuestions() {
        AiQuizDraft draft = new AiQuizDraft(List.of(
                validSingleChoice(1),
                validSingleChoice(2),
                validSingleChoice(3),
                validSingleChoice(4)
        ));

        assertNull(client.validateDraft(draft));
    }

    @Test
    void validateDraftRejectsDuplicateOptionTexts() {
        AiQuizQuestionDraft duplicate = new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                "Which is correct?",
                List.of(
                        new AiQuizOptionDraft("A", "Same"),
                        new AiQuizOptionDraft("B", "Same"),
                        new AiQuizOptionDraft("C", "Other"),
                        new AiQuizOptionDraft("D", "Another")
                ),
                "A",
                "Explanation"
        );

        assertNull(client.validateDraft(draftWithLast(duplicate)));
    }

    @Test
    void validateDraftRejectsMissingSingleChoiceLabels() {
        AiQuizQuestionDraft missingLabel = new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                "Which is correct?",
                List.of(
                        new AiQuizOptionDraft("A", "One"),
                        new AiQuizOptionDraft("B", "Two"),
                        new AiQuizOptionDraft("C", "Three"),
                        new AiQuizOptionDraft("C", "Four")
                ),
                "A",
                "Explanation"
        );

        assertNull(client.validateDraft(draftWithLast(missingLabel)));
    }

    @Test
    void validateDraftRejectsTrueFalseWithMoreThanTwoOptions() {
        AiQuizQuestionDraft threeOptions = new AiQuizQuestionDraft(
                "TRUE_FALSE",
                "Java is a programming language.",
                List.of(
                        new AiQuizOptionDraft("A", "True"),
                        new AiQuizOptionDraft("B", "False"),
                        new AiQuizOptionDraft("C", "Maybe")
                ),
                "A",
                "Explanation"
        );

        assertNull(client.validateDraft(draftWithLast(threeOptions)));
    }

    @Test
    void validateDraftRejectsCorrectLabelOutsideOptions() {
        AiQuizQuestionDraft badCorrect = new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                "Which is correct?",
                List.of(
                        new AiQuizOptionDraft("A", "One"),
                        new AiQuizOptionDraft("B", "Two"),
                        new AiQuizOptionDraft("C", "Three"),
                        new AiQuizOptionDraft("D", "Four")
                ),
                "Z",
                "Explanation"
        );

        assertNull(client.validateDraft(draftWithLast(badCorrect)));
    }

    @Test
    void validateDraftRejectsExplanationLongerThanLimit() {
        AiQuizQuestionDraft longExplanation = new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                "Which is correct?",
                List.of(
                        new AiQuizOptionDraft("A", "One"),
                        new AiQuizOptionDraft("B", "Two"),
                        new AiQuizOptionDraft("C", "Three"),
                        new AiQuizOptionDraft("D", "Four")
                ),
                "A",
                "x".repeat(241)
        );

        assertNull(client.validateDraft(draftWithLast(longExplanation)));
    }

    @Test
    void buildChunkContextSkipsBlankChunksBeforeApplyingMaxChunks() {
        List<MaterialChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            chunks.add(chunk("   "));
        }
        chunks.add(chunk("Important content"));

        String context = client.buildChunkContext(chunks);

        assertTrue(context.contains("Important content"));
    }

    @Test
    void cleanJsonParsesJsonWrappedInMarkdownFence() {
        String cleaned = client.cleanJson("```json\n{\"questions\": []}\n```");

        assertEquals("{\"questions\": []}", cleaned);
    }

    private AiQuizDraft draftWithLast(AiQuizQuestionDraft last) {
        return new AiQuizDraft(List.of(
                validSingleChoice(1),
                validSingleChoice(2),
                validSingleChoice(3),
                validSingleChoice(4),
                last
        ));
    }

    private AiQuizQuestionDraft validSingleChoice(int n) {
        return new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                "Question " + n + "?",
                List.of(
                        new AiQuizOptionDraft("A", "Option A" + n),
                        new AiQuizOptionDraft("B", "Option B" + n),
                        new AiQuizOptionDraft("C", "Option C" + n),
                        new AiQuizOptionDraft("D", "Option D" + n)
                ),
                "A",
                "Explanation " + n
        );
    }

    private MaterialChunk chunk(String content) {
        MaterialChunk chunk = new MaterialChunk();
        chunk.setContent(content);
        return chunk;
    }
}
