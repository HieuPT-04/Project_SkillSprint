package com.skillsprint.service.quiz.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.ai.GeminiProperties;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
    void validateDraftRejectsWellFormedTrueFalseQuestion() {
        AiQuizQuestionDraft trueFalse = new AiQuizQuestionDraft(
                "TRUE_FALSE",
                "Java is a programming language.",
                List.of(
                        new AiQuizOptionDraft("A", "True"),
                        new AiQuizOptionDraft("B", "False")
                ),
                "A",
                "Explanation"
        );

        assertNull(client.validateDraft(draftWithLast(trueFalse)));
    }

    @Test
    void validateDraftRejectsUnknownQuestionType() {
        AiQuizQuestionDraft unknownType = new AiQuizQuestionDraft(
                "MULTI_SELECT",
                "Which are valid greetings?",
                List.of(
                        new AiQuizOptionDraft("A", "One"),
                        new AiQuizOptionDraft("B", "Two"),
                        new AiQuizOptionDraft("C", "Three"),
                        new AiQuizOptionDraft("D", "Four")
                ),
                "A",
                "Explanation"
        );

        assertNull(client.validateDraft(draftWithLast(unknownType)));
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
    void validateDraftRejectsVietnameseMetaQuestion() {
        assertNull(client.validateDraft(draftWithLast(
                singleChoiceWithQuestion("Bài học này nói về điều gì?"))));
    }

    @Test
    void validateDraftRejectsEnglishMetaQuestion() {
        assertNull(client.validateDraft(draftWithLast(
                singleChoiceWithQuestion("What is this lesson about?"))));
    }

    @Test
    void validateDraftRejectsMainTopicMetaQuestion() {
        assertNull(client.validateDraft(draftWithLast(
                singleChoiceWithQuestion("Chủ đề chính của bài này là gì?"))));
    }

    @Test
    void validateDraftAcceptsVietnameseContentQuestion() {
        assertNotNull(client.validateDraft(draftWithLast(
                singleChoiceWithQuestion("Trợ từ は dùng để làm gì?"))));
    }

    @Test
    void validateDraftAcceptsJapaneseContentQuestion() {
        assertNotNull(client.validateDraft(draftWithLast(
                singleChoiceWithQuestion("「です」は文の中でどのような役割をしますか？"))));
    }

    void validateDraftRejectsPlaceholderDistractorOptions() {
        AiQuizQuestionDraft placeholder = new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                "Trợ từ は dùng để làm gì trong câu?",
                List.of(
                        new AiQuizOptionDraft("A", "Đánh dấu chủ đề của câu"),
                        new AiQuizOptionDraft("B", "Thông tin ngoài tài liệu"),
                        new AiQuizOptionDraft("C", "Nội dung không liên quan"),
                        new AiQuizOptionDraft("D", "Tên file upload")
                ),
                "A",
                "Explanation"
        );

        assertNull(client.validateDraft(draftWithLast(placeholder)));
    }

    @Test
    void validateDraftRejectsVietnameseAllOfTheAboveOption() {
        AiQuizQuestionDraft allOfTheAbove = new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                "Cách chào buổi sáng trong tiếng Nhật là gì?",
                List.of(
                        new AiQuizOptionDraft("A", "おはよう"),
                        new AiQuizOptionDraft("B", "こんにちは"),
                        new AiQuizOptionDraft("C", "こんばんは"),
                        new AiQuizOptionDraft("D", "Tất cả đáp án trên")
                ),
                "A",
                "Explanation"
        );

        assertNull(client.validateDraft(draftWithLast(allOfTheAbove)));
    }

    @Test
    void buildChunkContextSkipsBlankChunksBeforeApplyingMaxChunks() {
        List<AiQuizGenerationInput.Chunk> chunks = new ArrayList<>();
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

    @Test
    void generateThrowsNotReadyWhenGeminiIsDisabled() {
        GeminiQuizClient disabledClient = new GeminiQuizClient(
                new GeminiProperties(false, "key", "model", "http://localhost", 18000),
                new ObjectMapper(),
                RestClient.builder()
        );

        AiQuizGenerationException exception = assertThrows(
                AiQuizGenerationException.class,
                () -> disabledClient.generate(input(List.of(chunk("content"))))
        );

        assertEquals(AiQuizGenerationFailureReason.NOT_READY, exception.getReason());
        assertFalse(exception.isRetryable());
    }

    @Test
    void generateThrowsNotReadyWhenThereAreNoMaterialChunks() {
        AiQuizGenerationException exception = assertThrows(
                AiQuizGenerationException.class,
                () -> client.generate(input(List.of()))
        );

        assertEquals(AiQuizGenerationFailureReason.NOT_READY, exception.getReason());
        assertFalse(exception.isRetryable());
    }

    @Test
    void classifyMaps429ToRetryableRateLimitWithRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "3");

        AiQuizGenerationException exception = client.classify(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                headers,
                "quota exceeded body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));

        assertEquals(AiQuizGenerationFailureReason.RATE_LIMITED, exception.getReason());
        assertEquals(Integer.valueOf(429), exception.getUpstreamStatus());
        assertEquals(Duration.ofSeconds(3), exception.getRetryAfter());
        assertTrue(exception.isRetryable());
    }

    @Test
    void classifyMapsServerErrorsToRetryableUpstreamUnavailable() {
        AiQuizGenerationException exception = client.classify(HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                new HttpHeaders(),
                new byte[0],
                StandardCharsets.UTF_8
        ));

        assertEquals(AiQuizGenerationFailureReason.UPSTREAM_UNAVAILABLE, exception.getReason());
        assertEquals(Integer.valueOf(503), exception.getUpstreamStatus());
        assertTrue(exception.isRetryable());
    }

    @Test
    void classifyMapsTimeoutToRetryableUpstreamUnavailableWithoutStatus() {
        AiQuizGenerationException exception = client.classify(
                new ResourceAccessException("read timed out", new SocketTimeoutException()));

        assertEquals(AiQuizGenerationFailureReason.UPSTREAM_UNAVAILABLE, exception.getReason());
        assertNull(exception.getUpstreamStatus());
        assertTrue(exception.isRetryable());
    }

    @Test
    void classifyMapsClientErrorsToNonRetryableInvalidConfiguration() {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)) {
            AiQuizGenerationException exception = client.classify(HttpClientErrorException.create(
                    status,
                    status.getReasonPhrase(),
                    new HttpHeaders(),
                    new byte[0],
                    StandardCharsets.UTF_8
            ));

            assertEquals(AiQuizGenerationFailureReason.INVALID_CONFIGURATION, exception.getReason());
            assertEquals(Integer.valueOf(status.value()), exception.getUpstreamStatus());
            assertFalse(exception.isRetryable());
        }
    }

    @Test
    void classifyNeverCopiesUpstreamResponseBodyIntoTheExceptionMessage() {
        AiQuizGenerationException exception = client.classify(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                new HttpHeaders(),
                "sensitive upstream body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));

        assertFalse(exception.getMessage().contains("sensitive upstream body"));
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

    private AiQuizQuestionDraft singleChoiceWithQuestion(String questionText) {
        return new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                questionText,
                List.of(
                        new AiQuizOptionDraft("A", "One"),
                        new AiQuizOptionDraft("B", "Two"),
                        new AiQuizOptionDraft("C", "Three"),
                        new AiQuizOptionDraft("D", "Four")
                ),
                "A",
                "Explanation"
        );
    }

    private AiQuizGenerationInput input(List<AiQuizGenerationInput.Chunk> chunks) {
        return new AiQuizGenerationInput(
                UUID.randomUUID(),
                "Java",
                "Basics",
                "Learn Java",
                List.of("JVM"),
                List.of(),
                chunks
        );
    }

    private AiQuizGenerationInput.Chunk chunk(String content) {
        return new AiQuizGenerationInput.Chunk(null, content);
    }
}
