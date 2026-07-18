package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePracticeQuizSnapshotService {

    static final SecureRandom RANDOM = new SecureRandom();

    ObjectMapper objectMapper;

    public PracticeSnapshot create(MarketplacePackVersion version, int chapterSequenceNo) {
        JsonNode chapter = findChapter(version.getContent(), chapterSequenceNo);
        JsonNode quiz = chapter.path("quiz");
        JsonNode sourceQuestions = quiz.path("questions");
        if (!sourceQuestions.isArray() || sourceQuestions.isEmpty()) {
            throw unavailable();
        }

        List<QuestionDraft> drafts = new ArrayList<>();
        sourceQuestions.forEach(question -> drafts.add(questionDraft(question)));
        Collections.shuffle(drafts, RANDOM);

        ObjectNode questionSnapshot = objectMapper.createObjectNode();
        questionSnapshot.put("chapterSequenceNo", chapterSequenceNo);
        questionSnapshot.put("chapterTitle", requiredText(chapter, "title"));
        questionSnapshot.put("quizTitle", requiredText(quiz, "title"));
        ArrayNode questions = questionSnapshot.putArray("questions");

        ObjectNode answerSnapshot = objectMapper.createObjectNode();
        ArrayNode answers = answerSnapshot.putArray("answers");
        for (QuestionDraft draft : drafts) {
            questions.add(draft.safeQuestion());
            answers.addObject()
                    .put("questionId", draft.questionId().toString())
                    .put("correctOptionId", draft.correctOptionId().toString());
        }
        return new PracticeSnapshot(questionSnapshot, answerSnapshot, drafts.size());
    }

    public List<MarketplacePracticeAttemptResponse.QuestionResponse> questionResponses(JsonNode snapshot) {
        if (snapshot == null || !snapshot.path("questions").isArray()) {
            throw unavailable();
        }
        List<MarketplacePracticeAttemptResponse.QuestionResponse> questions = new ArrayList<>();
        for (JsonNode question : snapshot.path("questions")) {
            List<MarketplacePracticeAttemptResponse.OptionResponse> options = new ArrayList<>();
            for (JsonNode option : question.path("options")) {
                options.add(MarketplacePracticeAttemptResponse.OptionResponse.builder()
                        .optionId(uuid(option, "optionId"))
                        .label(requiredText(option, "label"))
                        .text(requiredText(option, "text"))
                        .build());
            }
            questions.add(MarketplacePracticeAttemptResponse.QuestionResponse.builder()
                    .questionId(uuid(question, "questionId"))
                    .type(requiredText(question, "type"))
                    .text(requiredText(question, "text"))
                    .options(options)
                    .build());
        }
        return questions;
    }

    private JsonNode findChapter(JsonNode content, int chapterSequenceNo) {
        if (content == null || !content.path("chapters").isArray()) {
            throw unavailable();
        }
        for (JsonNode chapter : content.path("chapters")) {
            if (chapter.path("sequenceNo").asInt(-1) == chapterSequenceNo) {
                return chapter;
            }
        }
        throw unavailable();
    }

    private QuestionDraft questionDraft(JsonNode sourceQuestion) {
        UUID questionId = uuid(sourceQuestion, "questionId");
        List<ObjectNode> options = new ArrayList<>();
        UUID correctOptionId = null;
        JsonNode sourceOptions = sourceQuestion.path("options");
        if (!sourceOptions.isArray() || sourceOptions.size() < 2) {
            throw unavailable();
        }
        for (JsonNode sourceOption : sourceOptions) {
            UUID optionId = uuid(sourceOption, "optionId");
            ObjectNode option = objectMapper.createObjectNode();
            option.put("optionId", optionId.toString());
            option.put("label", requiredText(sourceOption, "label"));
            option.put("text", requiredText(sourceOption, "text"));
            options.add(option);
            if (sourceOption.path("correct").asBoolean(false)) {
                if (correctOptionId != null) {
                    throw unavailable();
                }
                correctOptionId = optionId;
            }
        }
        if (correctOptionId == null) {
            throw unavailable();
        }
        Collections.shuffle(options, RANDOM);

        ObjectNode safeQuestion = objectMapper.createObjectNode();
        safeQuestion.put("questionId", questionId.toString());
        safeQuestion.put("type", requiredText(sourceQuestion, "type"));
        safeQuestion.put("text", requiredText(sourceQuestion, "text"));
        ArrayNode safeOptions = safeQuestion.putArray("options");
        options.forEach(safeOptions::add);
        return new QuestionDraft(questionId, correctOptionId, safeQuestion);
    }

    private UUID uuid(JsonNode node, String fieldName) {
        try {
            return UUID.fromString(node.path(fieldName).asText());
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (value.isBlank()) {
            throw unavailable();
        }
        return value;
    }

    private AppException unavailable() {
        return new AppException(ErrorCode.MARKETPLACE_PRACTICE_QUIZ_UNAVAILABLE);
    }

    public record PracticeSnapshot(JsonNode questions, JsonNode answers, int questionCount) {
    }

    private record QuestionDraft(UUID questionId, UUID correctOptionId, ObjectNode safeQuestion) {
    }
}
