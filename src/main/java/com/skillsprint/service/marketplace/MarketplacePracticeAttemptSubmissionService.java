package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.request.marketplace.SubmitMarketplacePracticeAttemptRequest;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptSubmissionResponse;
import com.skillsprint.entity.MarketplacePracticeAttempt;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePracticeAttemptRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePracticeAttemptSubmissionService {

    MarketplaceVersionAccessService accessService;
    MarketplacePracticeAttemptRepository attemptRepository;
    ObjectMapper objectMapper;
    MarketplaceVersionProgressService progressService;

    @Transactional
    public MarketplacePracticeAttemptSubmissionResponse submit(
            String buyerId,
            UUID versionId,
            UUID attemptId,
            SubmitMarketplacePracticeAttemptRequest request
    ) {
        accessService.requireAndLockAccess(buyerId, versionId);
        MarketplacePracticeAttempt attempt = attemptRepository.findByAttemptIdForUpdate(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PRACTICE_ATTEMPT_NOT_FOUND));
        requireBuyerAndVersion(attempt, buyerId, versionId);

        String fingerprint = fingerprint(request.getAnswers());
        if (attempt.getStatus() == MarketplacePracticeAttemptStatus.COMPLETED) {
            return completedRetryResponse(attempt, request.getIdempotencyKey(), fingerprint);
        }
        if (attempt.getStatus() != MarketplacePracticeAttemptStatus.IN_PROGRESS) {
            throw invalidAttempt();
        }

        Map<UUID, UUID> submittedAnswers = submittedAnswers(request.getAnswers());
        Map<UUID, UUID> correctAnswers = correctAnswers(attempt.getAnswerSnapshot());
        Map<UUID, Set<UUID>> allowedOptions = allowedOptions(attempt.getQuestionSnapshot());
        validateSubmission(submittedAnswers, correctAnswers, allowedOptions, attempt.getQuestionCount());

        int correctCount = (int) submittedAnswers.entrySet().stream()
                .filter(answer -> answer.getValue().equals(correctAnswers.get(answer.getKey())))
                .count();
        attempt.setSubmittedAnswers(submittedAnswerSnapshot(submittedAnswers));
        attempt.setIdempotencyKey(request.getIdempotencyKey());
        attempt.setRequestFingerprint(fingerprint);
        attempt.setCorrectCount(correctCount);
        attempt.setScore((int) Math.round(correctCount * 100.0 / attempt.getQuestionCount()));
        attempt.setCompletedAt(Instant.now());
        attempt.setStatus(MarketplacePracticeAttemptStatus.COMPLETED);
        try {
            attempt = attemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.MARKETPLACE_PRACTICE_SUBMIT_IDEMPOTENCY_CONFLICT);
        }
        progressService.recordPracticeCompletion(attempt);
        return response(attempt);
    }

    private void requireBuyerAndVersion(MarketplacePracticeAttempt attempt, String buyerId, UUID versionId) {
        if (!attempt.getBuyer().getUserId().equals(buyerId)
                || !attempt.getPackVersion().getVersionId().equals(versionId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    private MarketplacePracticeAttemptSubmissionResponse completedRetryResponse(
            MarketplacePracticeAttempt attempt,
            UUID idempotencyKey,
            String fingerprint
    ) {
        if (!Objects.equals(idempotencyKey, attempt.getIdempotencyKey())) {
            throw invalidAttempt();
        }
        if (!fingerprint.equals(attempt.getRequestFingerprint())) {
            throw new AppException(ErrorCode.MARKETPLACE_PRACTICE_SUBMIT_IDEMPOTENCY_CONFLICT);
        }
        return response(attempt);
    }

    private Map<UUID, UUID> submittedAnswers(
            List<SubmitMarketplacePracticeAttemptRequest.AnswerRequest> answers
    ) {
        Map<UUID, UUID> submitted = new HashMap<>();
        for (SubmitMarketplacePracticeAttemptRequest.AnswerRequest answer : answers) {
            if (submitted.put(answer.getQuestionId(), answer.getOptionId()) != null) {
                throw invalidAttempt();
            }
        }
        return submitted;
    }

    private Map<UUID, UUID> correctAnswers(JsonNode snapshot) {
        Map<UUID, UUID> answers = new HashMap<>();
        if (snapshot == null || !snapshot.path("answers").isArray()) {
            throw quizUnavailable();
        }
        for (JsonNode answer : snapshot.path("answers")) {
            UUID questionId = uuid(answer, "questionId");
            if (answers.put(questionId, uuid(answer, "correctOptionId")) != null) {
                throw quizUnavailable();
            }
        }
        if (answers.isEmpty()) {
            throw quizUnavailable();
        }
        return answers;
    }

    private Map<UUID, Set<UUID>> allowedOptions(JsonNode snapshot) {
        Map<UUID, Set<UUID>> optionsByQuestion = new HashMap<>();
        if (snapshot == null || !snapshot.path("questions").isArray()) {
            throw quizUnavailable();
        }
        for (JsonNode question : snapshot.path("questions")) {
            UUID questionId = uuid(question, "questionId");
            Set<UUID> optionIds = new HashSet<>();
            for (JsonNode option : question.path("options")) {
                if (!optionIds.add(uuid(option, "optionId"))) {
                    throw quizUnavailable();
                }
            }
            if (optionIds.isEmpty() || optionsByQuestion.put(questionId, optionIds) != null) {
                throw quizUnavailable();
            }
        }
        return optionsByQuestion;
    }

    private void validateSubmission(
            Map<UUID, UUID> submittedAnswers,
            Map<UUID, UUID> correctAnswers,
            Map<UUID, Set<UUID>> allowedOptions,
            int expectedQuestionCount
    ) {
        if (correctAnswers.size() != expectedQuestionCount
                || !submittedAnswers.keySet().equals(correctAnswers.keySet())
                || !correctAnswers.keySet().equals(allowedOptions.keySet())) {
            throw invalidAttempt();
        }
        for (Map.Entry<UUID, UUID> submitted : submittedAnswers.entrySet()) {
            if (!allowedOptions.get(submitted.getKey()).contains(submitted.getValue())) {
                throw invalidAttempt();
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

    private String fingerprint(List<SubmitMarketplacePracticeAttemptRequest.AnswerRequest> answers) {
        List<String> canonicalAnswers = new ArrayList<>();
        for (SubmitMarketplacePracticeAttemptRequest.AnswerRequest answer : answers) {
            canonicalAnswers.add(answer.getQuestionId() + ":" + answer.getOptionId());
        }
        canonicalAnswers.sort(String::compareTo);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("|", canonicalAnswers).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UUID uuid(JsonNode node, String fieldName) {
        try {
            return UUID.fromString(node.path(fieldName).asText());
        } catch (IllegalArgumentException exception) {
            throw quizUnavailable();
        }
    }

    private MarketplacePracticeAttemptSubmissionResponse response(MarketplacePracticeAttempt attempt) {
        return MarketplacePracticeAttemptSubmissionResponse.builder()
                .attemptId(attempt.getAttemptId())
                .versionId(attempt.getPackVersion().getVersionId())
                .chapterSequenceNo(attempt.getChapterSequenceNo())
                .score(attempt.getScore())
                .correctCount(attempt.getCorrectCount())
                .questionCount(attempt.getQuestionCount())
                .completedAt(attempt.getCompletedAt())
                .build();
    }

    private AppException invalidAttempt() {
        return new AppException(ErrorCode.MARKETPLACE_PRACTICE_ATTEMPT_INVALID);
    }

    private AppException quizUnavailable() {
        return new AppException(ErrorCode.MARKETPLACE_PRACTICE_QUIZ_UNAVAILABLE);
    }
}
