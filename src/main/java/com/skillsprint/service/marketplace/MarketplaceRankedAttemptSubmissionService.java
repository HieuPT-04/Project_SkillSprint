package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.request.marketplace.SubmitMarketplaceRankedAttemptRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptSubmissionResponse;
import com.skillsprint.entity.MarketplaceRankedAttempt;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceRankedAttemptSubmissionService {

    static final long SUSPICIOUS_DURATION_SECONDS = 5;

    MarketplaceRankedAttemptRepository attemptRepository;
    ObjectMapper objectMapper;

    @Transactional
    public MarketplaceRankedAttemptSubmissionResponse submit(
            String buyerId,
            UUID versionId,
            UUID attemptId,
            SubmitMarketplaceRankedAttemptRequest request
    ) {
        MarketplaceRankedAttempt attempt = attemptRepository.findByAttemptIdForUpdate(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_NOT_FOUND));
        requireBuyerAndVersion(attempt, buyerId, versionId);

        String fingerprint = fingerprint(request.getAnswers());
        if (attempt.getStatus() == MarketplaceRankedAttemptStatus.COMPLETED) {
            return completedRetryResponse(attempt, request.getIdempotencyKey(), fingerprint);
        }
        if (attempt.getStatus() != MarketplaceRankedAttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_INVALID);
        }

        Instant completedAt = Instant.now();
        if (!attempt.getExpiresAt().isAfter(completedAt)) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_EXPIRED);
        }

        Map<UUID, UUID> submittedAnswers = submittedAnswers(request.getAnswers());
        Map<UUID, UUID> correctAnswers = correctAnswers(attempt.getAnswerSnapshot());
        Map<UUID, Set<UUID>> allowedOptions = allowedOptions(attempt.getQuestionSnapshot());
        validateSubmission(submittedAnswers, correctAnswers, allowedOptions);

        int correctCount = (int) submittedAnswers.entrySet().stream()
                .filter(answer -> answer.getValue().equals(correctAnswers.get(answer.getKey())))
                .count();
        int questionCount = correctAnswers.size();
        long durationSeconds = Math.max(0, Duration.between(attempt.getStartedAt(), completedAt).getSeconds());
        boolean suspicious = durationSeconds < SUSPICIOUS_DURATION_SECONDS;
        boolean leaderboardEligible = !suspicious
                && !attemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndLeaderboardEligibleTrue(
                buyerId, versionId);

        attempt.setSubmittedAnswers(submittedAnswerSnapshot(submittedAnswers));
        attempt.setIdempotencyKey(request.getIdempotencyKey());
        attempt.setRequestFingerprint(fingerprint);
        attempt.setCorrectCount(correctCount);
        attempt.setScore((int) Math.round(correctCount * 100.0 / questionCount));
        attempt.setDurationSeconds(durationSeconds);
        attempt.setCompletedAt(completedAt);
        attempt.setSuspicious(suspicious);
        attempt.setLeaderboardEligible(leaderboardEligible);
        attempt.setStatus(MarketplaceRankedAttemptStatus.COMPLETED);
        attempt = attemptRepository.save(attempt);
        return response(attempt);
    }

    private void requireBuyerAndVersion(MarketplaceRankedAttempt attempt, String buyerId, UUID versionId) {
        if (!attempt.getBuyer().getUserId().equals(buyerId)
                || !attempt.getPackVersion().getVersionId().equals(versionId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    private MarketplaceRankedAttemptSubmissionResponse completedRetryResponse(
            MarketplaceRankedAttempt attempt,
            UUID idempotencyKey,
            String fingerprint
    ) {
        if (!idempotencyKey.equals(attempt.getIdempotencyKey())) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_INVALID);
        }
        if (!fingerprint.equals(attempt.getRequestFingerprint())) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_SUBMIT_IDEMPOTENCY_CONFLICT);
        }
        return response(attempt);
    }

    private Map<UUID, UUID> submittedAnswers(List<SubmitMarketplaceRankedAttemptRequest.AnswerRequest> answers) {
        Map<UUID, UUID> submitted = new HashMap<>();
        for (SubmitMarketplaceRankedAttemptRequest.AnswerRequest answer : answers) {
            if (submitted.put(answer.getQuestionId(), answer.getOptionId()) != null) {
                throw new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_INVALID);
            }
        }
        return submitted;
    }

    private Map<UUID, UUID> correctAnswers(JsonNode snapshot) {
        Map<UUID, UUID> answers = new HashMap<>();
        if (snapshot == null || !snapshot.path("answers").isArray()) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }
        for (JsonNode answer : snapshot.path("answers")) {
            UUID questionId = uuid(answer, "questionId");
            if (answers.put(questionId, uuid(answer, "correctOptionId")) != null) {
                throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
            }
        }
        if (answers.isEmpty()) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }
        return answers;
    }

    private Map<UUID, Set<UUID>> allowedOptions(JsonNode snapshot) {
        Map<UUID, Set<UUID>> optionsByQuestion = new HashMap<>();
        if (snapshot == null || !snapshot.path("questions").isArray()) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }
        for (JsonNode question : snapshot.path("questions")) {
            UUID questionId = uuid(question, "questionId");
            Set<UUID> optionIds = new HashSet<>();
            for (JsonNode option : question.path("options")) {
                if (!optionIds.add(uuid(option, "optionId"))) {
                    throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
                }
            }
            if (optionIds.isEmpty() || optionsByQuestion.put(questionId, optionIds) != null) {
                throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
            }
        }
        return optionsByQuestion;
    }

    private void validateSubmission(
            Map<UUID, UUID> submittedAnswers,
            Map<UUID, UUID> correctAnswers,
            Map<UUID, Set<UUID>> allowedOptions
    ) {
        if (!submittedAnswers.keySet().equals(correctAnswers.keySet())
                || !correctAnswers.keySet().equals(allowedOptions.keySet())) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_INVALID);
        }
        for (Map.Entry<UUID, UUID> submitted : submittedAnswers.entrySet()) {
            if (!allowedOptions.get(submitted.getKey()).contains(submitted.getValue())) {
                throw new AppException(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_INVALID);
            }
        }
    }

    private JsonNode submittedAnswerSnapshot(Map<UUID, UUID> submittedAnswers) {
        ArrayNode answers = objectMapper.createArrayNode();
        submittedAnswers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(answer -> answers.addObject()
                        .put("questionId", answer.getKey().toString())
                        .put("optionId", answer.getValue().toString()));
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("answers", answers);
        return snapshot;
    }

    private String fingerprint(List<SubmitMarketplaceRankedAttemptRequest.AnswerRequest> answers) {
        List<String> canonicalAnswers = new ArrayList<>();
        for (SubmitMarketplaceRankedAttemptRequest.AnswerRequest answer : answers) {
            canonicalAnswers.add(answer.getQuestionId() + ":" + answer.getOptionId());
        }
        canonicalAnswers.sort(String::compareTo);
        String canonicalPayload = String.join("|", canonicalAnswers);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UUID uuid(JsonNode node, String fieldName) {
        try {
            return UUID.fromString(node.path(fieldName).asText());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE);
        }
    }

    private MarketplaceRankedAttemptSubmissionResponse response(MarketplaceRankedAttempt attempt) {
        return MarketplaceRankedAttemptSubmissionResponse.builder()
                .attemptId(attempt.getAttemptId())
                .versionId(attempt.getPackVersion().getVersionId())
                .score(attempt.getScore())
                .correctCount(attempt.getCorrectCount())
                .questionCount(attempt.getDefinition().getTotalQuestionCount())
                .durationSeconds(attempt.getDurationSeconds())
                .completedAt(attempt.getCompletedAt())
                .suspicious(attempt.isSuspicious())
                .leaderboardEligible(attempt.isLeaderboardEligible())
                .build();
    }
}
