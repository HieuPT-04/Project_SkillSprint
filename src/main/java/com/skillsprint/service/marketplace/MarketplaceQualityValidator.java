package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.entity.MarketplacePackVersion;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceQualityValidator {

    static final int MINIMUM_CHAPTER_COUNT = 4;
    static final int MINIMUM_QUESTION_COUNT = 20;
    static final int PASSING_SCORE = 80;
    static final int BLOCKING_DEDUCTION = 10;

    ObjectMapper objectMapper;

    public ValidationResult validate(MarketplacePackVersion version) {
        ArrayNode issues = objectMapper.createArrayNode();
        JsonNode content = version.getContent();
        JsonNode chapters = content == null ? objectMapper.missingNode() : content.path("chapters");
        if (!chapters.isArray()) {
            addIssue(issues, "CHAPTERS_MISSING", null, null, "Snapshot không có danh sách chương hợp lệ.");
        }

        int storedChapterCount = version.getChapterCount() == null ? 0 : version.getChapterCount();
        if (storedChapterCount < MINIMUM_CHAPTER_COUNT || chapters.size() < MINIMUM_CHAPTER_COUNT) {
            addIssue(issues, "MINIMUM_CHAPTERS", null, null,
                    "Quiz Pack cần tối thiểu " + MINIMUM_CHAPTER_COUNT + " chương.");
        }

        Set<String> questionIds = new HashSet<>();
        int actualQuestionCount = 0;
        for (JsonNode chapter : chapters) {
            Integer chapterSequence = chapter.has("sequenceNo") ? chapter.path("sequenceNo").asInt() : null;
            if (chapter.path("title").asText("").isBlank()) {
                addIssue(issues, "CHAPTER_TITLE_MISSING", chapterSequence, null, "Chương chưa có tiêu đề.");
            }

            JsonNode questions = chapter.path("quiz").path("questions");
            if (!questions.isArray() || questions.isEmpty()) {
                addIssue(issues, "CHAPTER_QUIZ_EMPTY", chapterSequence, null, "Chương chưa có câu hỏi Quiz.");
                continue;
            }

            for (JsonNode question : questions) {
                actualQuestionCount++;
                String questionId = question.path("questionId").asText(null);
                boolean validQuestionId = isUuid(questionId);
                String questionText = question.path("text").asText("").trim();
                if (!validQuestionId || !questionIds.add(questionId)) {
                    addIssue(issues, "QUESTION_ID_INVALID", chapterSequence, questionId,
                            "Mã câu hỏi bị thiếu, sai định dạng hoặc trùng lặp.");
                }
                if (questionText.isBlank()) {
                    addIssue(issues, "QUESTION_TEXT_MISSING", chapterSequence, questionId,
                            "Câu hỏi chưa có nội dung.");
                }

                validateOptions(issues, chapterSequence, questionId, question.path("options"));
            }
        }

        int storedQuestionCount = version.getQuestionCount() == null ? 0 : version.getQuestionCount();
        if (actualQuestionCount < MINIMUM_QUESTION_COUNT || storedQuestionCount != actualQuestionCount) {
            addIssue(issues, "QUESTION_COUNT_INVALID", null, null,
                    "Số câu hỏi không khớp hoặc chưa đạt tối thiểu " + MINIMUM_QUESTION_COUNT + ".");
        }

        int score = Math.max(0, 100 - issues.size() * BLOCKING_DEDUCTION);
        boolean passed = issues.isEmpty() && score >= PASSING_SCORE;
        ObjectNode report = objectMapper.createObjectNode();
        report.put("passingScore", PASSING_SCORE);
        report.put("blockingIssueCount", issues.size());
        report.put("chapterCount", chapters.size());
        report.put("questionCount", actualQuestionCount);
        report.set("issues", issues);
        return new ValidationResult(score, passed, report);
    }

    private void validateOptions(
            ArrayNode issues,
            Integer chapterSequence,
            String questionId,
            JsonNode options
    ) {
        if (!options.isArray() || options.size() < 2) {
            addIssue(issues, "QUESTION_OPTIONS_INSUFFICIENT", chapterSequence, questionId,
                    "Câu hỏi cần ít nhất hai đáp án.");
            return;
        }

        int correctCount = 0;
        Set<String> optionIds = new HashSet<>();
        Set<String> optionTexts = new HashSet<>();
        for (JsonNode option : options) {
            String optionId = option.path("optionId").asText(null);
            String text = option.path("text").asText("").trim();
            if (!isUuid(optionId) || !optionIds.add(optionId)) {
                addIssue(issues, "OPTION_ID_INVALID", chapterSequence, questionId,
                        "Mã đáp án bị thiếu, sai định dạng hoặc trùng lặp.");
            }
            if (text.isBlank() || !optionTexts.add(normalize(text))) {
                addIssue(issues, "OPTION_TEXT_INVALID", chapterSequence, questionId,
                        "Đáp án bị trống hoặc trùng nội dung.");
            }
            if (option.path("correct").asBoolean(false)) {
                correctCount++;
            }
        }
        if (correctCount != 1) {
            addIssue(issues, "CORRECT_OPTION_COUNT", chapterSequence, questionId,
                    "Mỗi câu hỏi phải có đúng một đáp án đúng.");
        }
    }

    private void addIssue(
            ArrayNode issues,
            String code,
            Integer chapterSequence,
            String questionId,
            String message
    ) {
        ObjectNode issue = issues.addObject();
        issue.put("code", code);
        issue.put("severity", "BLOCKING");
        if (chapterSequence != null) {
            issue.put("chapterSequenceNo", chapterSequence);
        }
        if (questionId != null) {
            issue.put("questionId", questionId);
        }
        issue.put("message", message);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public record ValidationResult(int score, boolean passed, ObjectNode report) {
    }
}
