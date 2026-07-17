package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceRankedQuestionSelection;
import com.skillsprint.entity.MarketplaceRankedQuizDefinition;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplaceRankedQuestionSelectionRepository;
import com.skillsprint.repository.MarketplaceRankedQuizDefinitionRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the immutable five-question-per-chapter Ranked Quiz definition for a
 * Pack Version. It reads only the version content snapshot, never the mutable
 * legacy item snapshot.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceRankedQuizDefinitionService {

    static final int QUESTIONS_PER_STEP = 5;
    static final int DEFAULT_DAILY_ATTEMPT_LIMIT = 3;

    MarketplacePackVersionRepository versionRepository;
    MarketplaceRankedQuizDefinitionRepository definitionRepository;
    MarketplaceRankedQuestionSelectionRepository selectionRepository;

    @Transactional
    public MarketplaceRankedQuizDefinition ensureDefinition(UUID versionId) {
        MarketplacePackVersion version = versionRepository.findByVersionIdForUpdate(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));

        return definitionRepository.findByPackVersionVersionId(versionId)
                .orElseGet(() -> createDefinition(version));
    }

    private MarketplaceRankedQuizDefinition createDefinition(MarketplacePackVersion version) {
        List<SelectionDraft> selectionDrafts = selectionDrafts(version.getContent());

        MarketplaceRankedQuizDefinition definition = new MarketplaceRankedQuizDefinition();
        definition.setPackVersion(version);
        definition.setQuestionsPerStep(QUESTIONS_PER_STEP);
        definition.setTotalQuestionCount(selectionDrafts.size());
        definition.setDailyAttemptLimit(DEFAULT_DAILY_ATTEMPT_LIMIT);
        MarketplaceRankedQuizDefinition savedDefinition = definitionRepository.save(definition);

        List<MarketplaceRankedQuestionSelection> selections = selectionDrafts.stream()
                .map(draft -> toSelection(savedDefinition, draft))
                .toList();
        selectionRepository.saveAll(selections);
        return savedDefinition;
    }

    private List<SelectionDraft> selectionDrafts(JsonNode content) {
        JsonNode chapters = content == null ? null : content.path("chapters");
        if (chapters == null || !chapters.isArray() || chapters.isEmpty()) {
            throw definitionUnavailable("The Pack Version has no chapters for a Ranked Quiz");
        }

        List<SelectionDraft> drafts = new ArrayList<>();
        Set<UUID> seenQuestionIds = new HashSet<>();
        int stepOrder = 0;
        for (JsonNode chapter : chapters) {
            stepOrder++;
            List<JsonNode> questions = questionsFor(chapter, stepOrder);
            String sourceStepKey = "chapter:" + stepOrder;
            for (int index = 0; index < QUESTIONS_PER_STEP; index++) {
                JsonNode question = questions.get(index);
                UUID questionId = questionId(question, stepOrder, index + 1);
                if (!seenQuestionIds.add(questionId)) {
                    throw definitionUnavailable("A question is duplicated in the Pack Version snapshot");
                }
                validateExactlyOneCorrectOption(question, stepOrder, index + 1);
                drafts.add(new SelectionDraft(sourceStepKey, stepOrder, questionId, index + 1));
            }
        }
        return drafts;
    }

    private List<JsonNode> questionsFor(JsonNode chapter, int stepOrder) {
        JsonNode questions = chapter.path("quiz").path("questions");
        if (!questions.isArray() || questions.size() < QUESTIONS_PER_STEP) {
            throw definitionUnavailable(
                    "Chapter %d needs at least %d valid questions"
                            .formatted(stepOrder, QUESTIONS_PER_STEP));
        }
        List<JsonNode> values = new ArrayList<>();
        questions.forEach(values::add);
        return values;
    }

    private UUID questionId(JsonNode question, int stepOrder, int selectionOrder) {
        try {
            return UUID.fromString(question.path("questionId").asText());
        } catch (IllegalArgumentException exception) {
            throw definitionUnavailable(
                    "Question %d in chapter %d has no valid questionId"
                            .formatted(selectionOrder, stepOrder));
        }
    }

    private void validateExactlyOneCorrectOption(JsonNode question, int stepOrder, int selectionOrder) {
        JsonNode options = question.path("options");
        if (!options.isArray()) {
            throw definitionUnavailable(
                    "Question %d in chapter %d has no valid options"
                            .formatted(selectionOrder, stepOrder));
        }

        int correctOptionCount = 0;
        for (JsonNode option : options) {
            try {
                UUID.fromString(option.path("optionId").asText());
            } catch (IllegalArgumentException exception) {
                throw definitionUnavailable(
                        "An option in question %d of chapter %d has no valid optionId"
                                .formatted(selectionOrder, stepOrder));
            }
            if (option.path("correct").asBoolean(false)) {
                correctOptionCount++;
            }
        }

        if (correctOptionCount != 1) {
            throw definitionUnavailable(
                    "Question %d in chapter %d must have exactly one correct option"
                            .formatted(selectionOrder, stepOrder));
        }
    }

    private MarketplaceRankedQuestionSelection toSelection(
            MarketplaceRankedQuizDefinition definition,
            SelectionDraft draft
    ) {
        MarketplaceRankedQuestionSelection selection = new MarketplaceRankedQuestionSelection();
        selection.setDefinition(definition);
        selection.setSourceStepKey(draft.sourceStepKey());
        selection.setStepOrder(draft.stepOrder());
        selection.setQuestionId(draft.questionId());
        selection.setSelectionOrder(draft.selectionOrder());
        return selection;
    }

    private AppException definitionUnavailable(String message) {
        return new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE, message);
    }

    private record SelectionDraft(
            String sourceStepKey,
            int stepOrder,
            UUID questionId,
            int selectionOrder
    ) {
    }
}
