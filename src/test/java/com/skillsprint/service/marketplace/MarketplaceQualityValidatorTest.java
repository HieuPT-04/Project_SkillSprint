package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.entity.MarketplacePackVersion;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketplaceQualityValidatorTest {

    ObjectMapper objectMapper = new ObjectMapper();
    MarketplaceQualityValidator validator = new MarketplaceQualityValidator(objectMapper);

    @Test
    void validSnapshotPassesDeterministicValidation() {
        MarketplaceQualityValidator.ValidationResult result = validator.validate(validVersion());

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.report().path("issues")).isEmpty();
    }

    @Test
    void duplicateQuestionAndMissingEvidenceAreBlocking() {
        MarketplacePackVersion version = validVersion();
        ArrayNode chapters = (ArrayNode) version.getContent().path("chapters");
        ObjectNode firstQuestion = (ObjectNode) chapters.get(0).path("quiz").path("questions").get(0);
        ObjectNode secondQuestion = (ObjectNode) chapters.get(0).path("quiz").path("questions").get(1);
        secondQuestion.put("text", firstQuestion.path("text").asText());
        secondQuestion.remove("evidence");

        MarketplaceQualityValidator.ValidationResult result = validator.validate(version);

        assertThat(result.passed()).isFalse();
        assertThat(result.report().path("issues").findValuesAsText("code"))
                .contains("QUESTION_DUPLICATE", "QUESTION_EVIDENCE_MISSING");
    }

    @Test
    void questionMustHaveExactlyOneCorrectOption() {
        MarketplacePackVersion version = validVersion();
        ObjectNode firstQuestion = (ObjectNode) version.getContent().path("chapters").get(0)
                .path("quiz").path("questions").get(0);
        ((ObjectNode) firstQuestion.path("options").get(1)).put("correct", true);

        MarketplaceQualityValidator.ValidationResult result = validator.validate(version);

        assertThat(result.passed()).isFalse();
        assertThat(result.report().path("issues").findValuesAsText("code"))
                .contains("CORRECT_OPTION_COUNT");
    }

    private MarketplacePackVersion validVersion() {
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode chapters = content.putArray("chapters");
        int questionCount = 0;
        for (int chapterNo = 1; chapterNo <= 4; chapterNo++) {
            ObjectNode chapter = chapters.addObject();
            chapter.put("sequenceNo", chapterNo);
            chapter.put("title", "Chương " + chapterNo);
            ArrayNode questions = chapter.putObject("quiz").putArray("questions");
            for (int questionNo = 1; questionNo <= 5; questionNo++) {
                questionCount++;
                ObjectNode question = questions.addObject();
                question.put("questionId", UUID.randomUUID().toString());
                question.put("text", "Câu hỏi " + chapterNo + "-" + questionNo);
                ObjectNode evidence = question.putObject("evidence");
                evidence.put("sourceStepId", UUID.randomUUID().toString());
                evidence.put("explanation", "Giải thích đáp án " + questionNo);
                evidence.putArray("sourceChunkIds").add("chunk-" + chapterNo);
                ArrayNode options = question.putArray("options");
                option(options, "A", true);
                option(options, "B", false);
            }
        }

        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setChapterCount(4);
        version.setQuizCount(4);
        version.setQuestionCount(questionCount);
        version.setContent(content);
        return version;
    }

    private void option(ArrayNode options, String text, boolean correct) {
        ObjectNode option = options.addObject();
        option.put("optionId", UUID.randomUUID().toString());
        option.put("text", text);
        option.put("correct", correct);
    }
}
