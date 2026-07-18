package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketplacePracticeQuizSnapshotServiceTest {

    ObjectMapper objectMapper = new ObjectMapper();
    MarketplacePracticeQuizSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new MarketplacePracticeQuizSnapshotService(objectMapper);
    }

    @Test
    void createsBuyerSafeSnapshotForRequestedChapterOnly() throws Exception {
        MarketplacePackVersion version = version(content());

        MarketplacePracticeQuizSnapshotService.PracticeSnapshot snapshot = service.create(version, 2);

        assertThat(snapshot.questionCount()).isEqualTo(2);
        assertThat(snapshot.questions().path("chapterSequenceNo").asInt()).isEqualTo(2);
        assertThat(snapshot.questions().path("chapterTitle").asText()).isEqualTo("Chapter 2");
        assertThat(snapshot.questions().path("quizTitle").asText()).isEqualTo("Quiz 2");
        assertThat(snapshot.questions().path("questions")).hasSize(2);
        assertThat(snapshot.answers().path("answers")).hasSize(2);
        assertThat(objectMapper.writeValueAsString(snapshot.questions()))
                .doesNotContain("correct", "explanation", "Chapter 1");
        assertThat(snapshot.questions().path("questions"))
                .allSatisfy(question -> assertThat(question.path("options"))
                        .extracting(option -> option.path("label").asText())
                        .containsExactly("A", "B"));
        List<MarketplacePracticeAttemptResponse.QuestionResponse> responses =
                service.questionResponses(snapshot.questions());
        assertThat(objectMapper.writeValueAsString(responses))
                .doesNotContain("correct", "explanation");
        assertThat(responses).allSatisfy(question -> assertThat(question.getOptions())
                .extracting(MarketplacePracticeAttemptResponse.OptionResponse::getLabel)
                .containsExactly("A", "B"));
    }

    @Test
    void rejectsUnknownChapterWithTypedError() {
        assertThatThrownBy(() -> service.create(version(content()), 99))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_PRACTICE_QUIZ_UNAVAILABLE));
    }

    @Test
    void relabelsLegacyShuffledOptionsAtResponseBoundary() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        ObjectNode question = snapshot.putArray("questions").addObject();
        question.put("questionId", UUID.randomUUID().toString());
        question.put("type", "SINGLE_CHOICE");
        question.put("text", "Legacy question");
        ArrayNode options = question.putArray("options");
        for (String label : List.of("C", "A", "D", "B")) {
            options.addObject()
                    .put("optionId", UUID.randomUUID().toString())
                    .put("label", label)
                    .put("text", "Option " + label);
        }

        List<MarketplacePracticeAttemptResponse.QuestionResponse> responses =
                service.questionResponses(snapshot);

        assertThat(responses.get(0).getOptions())
                .extracting(MarketplacePracticeAttemptResponse.OptionResponse::getLabel)
                .containsExactly("A", "B", "C", "D");
    }

    private JsonNode content() {
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode chapters = content.putArray("chapters");
        for (int chapterNo = 1; chapterNo <= 2; chapterNo++) {
            ObjectNode chapter = chapters.addObject();
            chapter.put("sequenceNo", chapterNo);
            chapter.put("title", "Chapter " + chapterNo);
            ObjectNode quiz = chapter.putObject("quiz");
            quiz.put("title", "Quiz " + chapterNo);
            ArrayNode questions = quiz.putArray("questions");
            for (int questionNo = 1; questionNo <= 2; questionNo++) {
                ObjectNode question = questions.addObject();
                question.put("questionId", UUID.randomUUID().toString());
                question.put("type", "SINGLE_CHOICE");
                question.put("text", "Question " + chapterNo + '-' + questionNo);
                question.put("explanation", "Hidden");
                ArrayNode options = question.putArray("options");
                options.add(option("A", true));
                options.add(option("B", false));
            }
        }
        return content;
    }

    private ObjectNode option(String label, boolean correct) {
        return objectMapper.createObjectNode()
                .put("optionId", UUID.randomUUID().toString())
                .put("label", label)
                .put("text", "Option " + label)
                .put("correct", correct);
    }

    private MarketplacePackVersion version(JsonNode content) {
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setContent(content);
        return version;
    }
}
