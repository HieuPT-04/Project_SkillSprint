package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceRankedAttempt;
import com.skillsprint.entity.MarketplaceRankedQuestionSelection;
import com.skillsprint.entity.MarketplaceRankedQuizDefinition;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import com.skillsprint.repository.MarketplaceRankedQuestionSelectionRepository;
import com.skillsprint.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceRankedAttemptService {

    static final ZoneId VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");
    static final long ATTEMPT_DURATION_SECONDS = 60 * 60;
    static final SecureRandom RANDOM = new SecureRandom();

    MarketplaceRankedQuizAccessService accessService;
    MarketplaceRankedQuizDefinitionService definitionService;
    MarketplaceRankedQuestionSelectionRepository selectionRepository;
    MarketplaceRankedAttemptRepository attemptRepository;
    UserRepository userRepository;
    ObjectMapper objectMapper;

    @Transactional
    public MarketplaceRankedAttemptResponse startOrResume(String buyerId, UUID versionId) {
        MarketplacePackVersion version = accessService.requireAndLockRankedAccess(buyerId, versionId);
        MarketplaceRankedQuizDefinition definition = definitionService.ensureDefinition(versionId);
        Instant now = Instant.now();
        MarketplaceRankedAttempt inProgress = findUsableInProgress(buyerId, versionId, now);
        if (inProgress != null) {
            return response(inProgress, attemptsRemaining(buyerId, versionId, definition, vietnamDate(now)));
        }

        LocalDate attemptDate = vietnamDate(now);
        long startedToday = attemptRepository.countByBuyerUserIdAndPackVersionVersionIdAndAttemptDate(
                buyerId, versionId, attemptDate);
        if (startedToday >= definition.getDailyAttemptLimit()) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_LIMIT_REACHED);
        }

        Snapshot snapshot = shuffledSnapshot(version, definition);
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
        MarketplaceRankedAttempt attempt = new MarketplaceRankedAttempt();
        attempt.setBuyer(buyer);
        attempt.setPackVersion(version);
        attempt.setDefinition(definition);
        attempt.setAttemptDate(attemptDate);
        attempt.setAttemptNumber(Math.toIntExact(startedToday + 1));
        attempt.setStatus(MarketplaceRankedAttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(now);
        attempt.setExpiresAt(now.plusSeconds(ATTEMPT_DURATION_SECONDS));
        attempt.setQuestionSnapshot(snapshot.questions());
        attempt.setAnswerSnapshot(snapshot.answers());
        attempt.setSuspicious(false);
        attempt.setLeaderboardEligible(false);
        attempt = attemptRepository.save(attempt);
        return response(attempt, attemptsRemaining(buyerId, versionId, definition, attemptDate));
    }

    @Transactional(readOnly = true)
    public MarketplaceRankedAttemptResponse getInProgress(String buyerId, UUID versionId) {
        accessService.requireRankedAccess(buyerId, versionId);
        Instant now = Instant.now();
        MarketplaceRankedAttempt attempt = attemptRepository
                .findByBuyerUserIdAndPackVersionVersionIdAndStatusOrderByStartedAtDesc(
                        buyerId, versionId, MarketplaceRankedAttemptStatus.IN_PROGRESS)
                .stream()
                .filter(value -> value.getExpiresAt().isAfter(now))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_NOT_FOUND));
        return response(
                attempt,
                attemptsRemaining(buyerId, versionId, attempt.getDefinition(), vietnamDate(now))
        );
    }

    private MarketplaceRankedAttempt findUsableInProgress(String buyerId, UUID versionId, Instant now) {
        List<MarketplaceRankedAttempt> expiredAttempts = new ArrayList<>();
        for (MarketplaceRankedAttempt attempt : attemptRepository
                .findByBuyerUserIdAndPackVersionVersionIdAndStatusOrderByStartedAtDesc(
                        buyerId, versionId, MarketplaceRankedAttemptStatus.IN_PROGRESS)) {
            if (attempt.getExpiresAt().isAfter(now)) {
                return attempt;
            }
            attempt.setStatus(MarketplaceRankedAttemptStatus.EXPIRED);
            expiredAttempts.add(attempt);
        }
        if (!expiredAttempts.isEmpty()) {
            attemptRepository.saveAll(expiredAttempts);
        }
        return null;
    }

    private Snapshot shuffledSnapshot(MarketplacePackVersion version, MarketplaceRankedQuizDefinition definition) {
        List<MarketplaceRankedQuestionSelection> selections = selectionRepository
                .findByDefinitionDefinitionIdOrderByStepOrderAscSelectionOrderAsc(definition.getDefinitionId());
        if (selections.size() != definition.getTotalQuestionCount()) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }

        Map<UUID, JsonNode> questionsById = questionsById(version.getContent());
        List<QuestionDraft> questions = new ArrayList<>();
        for (MarketplaceRankedQuestionSelection selection : selections) {
            JsonNode sourceQuestion = questionsById.get(selection.getQuestionId());
            if (sourceQuestion == null) {
                throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
            }
            questions.add(questionDraft(sourceQuestion));
        }
        Collections.shuffle(questions, RANDOM);

        ArrayNode questionNodes = objectMapper.createArrayNode();
        ArrayNode answerNodes = objectMapper.createArrayNode();
        for (QuestionDraft question : questions) {
            questionNodes.add(question.safeQuestion());
            ObjectNode answer = answerNodes.addObject();
            answer.put("questionId", question.questionId().toString());
            answer.put("correctOptionId", question.correctOptionId().toString());
        }
        ObjectNode questionSnapshot = objectMapper.createObjectNode();
        questionSnapshot.set("questions", questionNodes);
        ObjectNode answerSnapshot = objectMapper.createObjectNode();
        answerSnapshot.set("answers", answerNodes);
        return new Snapshot(questionSnapshot, answerSnapshot);
    }

    private Map<UUID, JsonNode> questionsById(JsonNode content) {
        Map<UUID, JsonNode> questions = new HashMap<>();
        if (content == null || !content.path("chapters").isArray()) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }
        for (JsonNode chapter : content.path("chapters")) {
            for (JsonNode question : chapter.path("quiz").path("questions")) {
                try {
                    questions.put(UUID.fromString(question.path("questionId").asText()), question);
                } catch (IllegalArgumentException exception) {
                    throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
                }
            }
        }
        return questions;
    }

    private QuestionDraft questionDraft(JsonNode sourceQuestion) {
        UUID questionId = uuid(sourceQuestion, "questionId");
        String type = requiredText(sourceQuestion, "type");
        String text = requiredText(sourceQuestion, "text");
        JsonNode sourceOptions = sourceQuestion.path("options");
        if (!sourceOptions.isArray()) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }

        List<ObjectNode> options = new ArrayList<>();
        UUID correctOptionId = null;
        for (JsonNode sourceOption : sourceOptions) {
            UUID optionId = uuid(sourceOption, "optionId");
            ObjectNode option = objectMapper.createObjectNode();
            option.put("optionId", optionId.toString());
            option.put("label", requiredText(sourceOption, "label"));
            option.put("text", requiredText(sourceOption, "text"));
            options.add(option);
            if (sourceOption.path("correct").asBoolean(false)) {
                if (correctOptionId != null) {
                    throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
                }
                correctOptionId = optionId;
            }
        }
        if (correctOptionId == null) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }
        Collections.shuffle(options, RANDOM);
        MarketplaceQuizOptionLabels.relabel(options);

        ObjectNode question = objectMapper.createObjectNode();
        question.put("questionId", questionId.toString());
        question.put("type", type);
        question.put("text", text);
        ArrayNode optionNodes = question.putArray("options");
        options.forEach(optionNodes::add);
        return new QuestionDraft(questionId, correctOptionId, question);
    }

    private UUID uuid(JsonNode node, String fieldName) {
        try {
            return UUID.fromString(node.path(fieldName).asText());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        if (value.isBlank()) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }
        return value;
    }

    private MarketplaceRankedAttemptResponse response(MarketplaceRankedAttempt attempt, int attemptsRemaining) {
        return MarketplaceRankedAttemptResponse.builder()
                .attemptId(attempt.getAttemptId())
                .versionId(attempt.getPackVersion().getVersionId())
                .versionNo(attempt.getPackVersion().getVersionNo())
                .status(attempt.getStatus())
                .attemptDate(attempt.getAttemptDate())
                .attemptNumber(attempt.getAttemptNumber())
                .startedAt(attempt.getStartedAt())
                .expiresAt(attempt.getExpiresAt())
                .totalQuestionCount(attempt.getDefinition().getTotalQuestionCount())
                .attemptsRemaining(attemptsRemaining)
                .questions(questionResponses(attempt.getQuestionSnapshot()))
                .build();
    }

    private List<MarketplaceRankedAttemptResponse.QuestionResponse> questionResponses(JsonNode snapshot) {
        List<MarketplaceRankedAttemptResponse.QuestionResponse> questions = new ArrayList<>();
        for (JsonNode question : snapshot.path("questions")) {
            List<MarketplaceRankedAttemptResponse.OptionResponse> options = new ArrayList<>();
            int optionIndex = 0;
            for (JsonNode option : question.path("options")) {
                options.add(MarketplaceRankedAttemptResponse.OptionResponse.builder()
                        .optionId(uuid(option, "optionId"))
                        .label(MarketplaceQuizOptionLabels.labelAt(optionIndex++))
                        .text(requiredText(option, "text"))
                        .build());
            }
            questions.add(MarketplaceRankedAttemptResponse.QuestionResponse.builder()
                    .questionId(uuid(question, "questionId"))
                    .type(requiredText(question, "type"))
                    .text(requiredText(question, "text"))
                    .options(options)
                    .build());
        }
        return questions;
    }

    private int attemptsRemaining(
            String buyerId,
            UUID versionId,
            MarketplaceRankedQuizDefinition definition,
            LocalDate attemptDate
    ) {
        long startedAttempts = attemptRepository.countByBuyerUserIdAndPackVersionVersionIdAndAttemptDate(
                buyerId, versionId, attemptDate);
        return Math.max(0, definition.getDailyAttemptLimit() - Math.toIntExact(startedAttempts));
    }

    private LocalDate vietnamDate(Instant instant) {
        return instant.atZone(VIETNAM_ZONE_ID).toLocalDate();
    }

    private record Snapshot(JsonNode questions, JsonNode answers) {
    }

    private record QuestionDraft(UUID questionId, UUID correctOptionId, ObjectNode safeQuestion) {
    }
}
