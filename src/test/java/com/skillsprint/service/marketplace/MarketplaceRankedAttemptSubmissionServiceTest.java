package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.request.marketplace.SubmitMarketplaceRankedAttemptRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptSubmissionResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceRankedAttempt;
import com.skillsprint.entity.MarketplaceRankedQuizDefinition;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceRankedAttemptSubmissionServiceTest {

    @Mock MarketplaceRankedAttemptRepository attemptRepository;

    ObjectMapper objectMapper = new ObjectMapper();
    MarketplaceRankedAttemptSubmissionService service;
    MarketplaceRankedAttempt attempt;
    UUID versionId;
    UUID firstQuestionId;
    UUID firstCorrectOptionId;
    UUID firstWrongOptionId;
    UUID secondQuestionId;
    UUID secondCorrectOptionId;

    @BeforeEach
    void setUp() {
        service = new MarketplaceRankedAttemptSubmissionService(attemptRepository, objectMapper);
        attempt = attempt(Instant.now().minusSeconds(20));
        versionId = attempt.getPackVersion().getVersionId();
        when(attemptRepository.findByAttemptIdForUpdate(attempt.getAttemptId())).thenReturn(Optional.of(attempt));
    }

    @Test
    void completesAndScoresUsingServerOwnedSnapshot() {
        allowSave();
        when(attemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndLeaderboardEligibleTrue(
                "buyer", versionId)).thenReturn(false);

        MarketplaceRankedAttemptSubmissionResponse response = service.submit(
                "buyer", versionId, attempt.getAttemptId(), request(UUID.randomUUID(), firstCorrectOptionId, secondCorrectOptionId));

        assertThat(response.getScore()).isEqualTo(100);
        assertThat(response.getCorrectCount()).isEqualTo(2);
        assertThat(response.getQuestionCount()).isEqualTo(2);
        assertThat(response.getDurationSeconds()).isGreaterThanOrEqualTo(20);
        assertThat(response.isSuspicious()).isFalse();
        assertThat(response.isLeaderboardEligible()).isTrue();
        MarketplaceRankedAttempt saved = savedAttempt();
        assertThat(saved.getStatus()).isEqualTo(MarketplaceRankedAttemptStatus.COMPLETED);
        assertThat(saved.getSubmittedAnswers().toString()).contains("optionId").doesNotContain("correctOptionId");
    }

    @Test
    void returnsOriginalResultForIdenticalRetry() {
        allowSave();
        when(attemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndLeaderboardEligibleTrue(
                "buyer", versionId)).thenReturn(false);
        SubmitMarketplaceRankedAttemptRequest request = request(
                UUID.randomUUID(), firstCorrectOptionId, secondCorrectOptionId);

        MarketplaceRankedAttemptSubmissionResponse first = service.submit(
                "buyer", versionId, attempt.getAttemptId(), request);
        MarketplaceRankedAttemptSubmissionResponse retry = service.submit(
                "buyer", versionId, attempt.getAttemptId(), request);

        assertThat(retry.getCompletedAt()).isEqualTo(first.getCompletedAt());
        assertThat(retry.getScore()).isEqualTo(first.getScore());
        verify(attemptRepository, times(1)).save(any(MarketplaceRankedAttempt.class));
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentAnswers() {
        allowSave();
        when(attemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndLeaderboardEligibleTrue(
                "buyer", versionId)).thenReturn(false);
        UUID idempotencyKey = UUID.randomUUID();
        service.submit("buyer", versionId, attempt.getAttemptId(), request(
                idempotencyKey, firstCorrectOptionId, secondCorrectOptionId));

        assertThatThrownBy(() -> service.submit("buyer", versionId, attempt.getAttemptId(), request(
                idempotencyKey, firstWrongOptionId, secondCorrectOptionId)))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_RANKED_SUBMIT_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void rejectsOptionOutsideThePersistedQuestionSnapshot() {
        assertThatThrownBy(() -> service.submit("buyer", versionId, attempt.getAttemptId(), request(
                UUID.randomUUID(), UUID.randomUUID(), secondCorrectOptionId)))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_INVALID));

        verify(attemptRepository, never()).save(any(MarketplaceRankedAttempt.class));
    }

    @Test
    void rejectsExpiredAttemptWithoutPersistingAResult() {
        attempt.setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.submit("buyer", versionId, attempt.getAttemptId(), request(
                UUID.randomUUID(), firstCorrectOptionId, secondCorrectOptionId)))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_EXPIRED));

        verify(attemptRepository, never()).save(any(MarketplaceRankedAttempt.class));
    }

    @Test
    void storesSuspiciousFastAttemptWithoutLeaderboardEligibility() {
        allowSave();
        attempt.setStartedAt(Instant.now());

        MarketplaceRankedAttemptSubmissionResponse response = service.submit(
                "buyer", versionId, attempt.getAttemptId(), request(UUID.randomUUID(), firstCorrectOptionId, secondCorrectOptionId));

        assertThat(response.isSuspicious()).isTrue();
        assertThat(response.isLeaderboardEligible()).isFalse();
        verify(attemptRepository, never()).existsByBuyerUserIdAndPackVersionVersionIdAndLeaderboardEligibleTrue(
                "buyer", versionId);
    }

    @Test
    void keepsLaterValidAttemptOutOfLeaderboardAfterFirstEligibleResult() {
        allowSave();
        when(attemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndLeaderboardEligibleTrue(
                "buyer", versionId)).thenReturn(true);

        MarketplaceRankedAttemptSubmissionResponse response = service.submit(
                "buyer", versionId, attempt.getAttemptId(), request(UUID.randomUUID(), firstCorrectOptionId, secondCorrectOptionId));

        assertThat(response.isSuspicious()).isFalse();
        assertThat(response.isLeaderboardEligible()).isFalse();
    }

    private MarketplaceRankedAttempt savedAttempt() {
        ArgumentCaptor<MarketplaceRankedAttempt> attempts = ArgumentCaptor.forClass(MarketplaceRankedAttempt.class);
        verify(attemptRepository).save(attempts.capture());
        return attempts.getValue();
    }

    private void allowSave() {
        when(attemptRepository.save(any(MarketplaceRankedAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SubmitMarketplaceRankedAttemptRequest request(
            UUID idempotencyKey,
            UUID firstOptionId,
            UUID secondOptionId
    ) {
        SubmitMarketplaceRankedAttemptRequest request = new SubmitMarketplaceRankedAttemptRequest();
        request.setIdempotencyKey(idempotencyKey);
        List<SubmitMarketplaceRankedAttemptRequest.AnswerRequest> answers = new ArrayList<>();
        answers.add(answer(firstQuestionId, firstOptionId));
        answers.add(answer(secondQuestionId, secondOptionId));
        request.setAnswers(answers);
        return request;
    }

    private SubmitMarketplaceRankedAttemptRequest.AnswerRequest answer(UUID questionId, UUID optionId) {
        SubmitMarketplaceRankedAttemptRequest.AnswerRequest answer =
                new SubmitMarketplaceRankedAttemptRequest.AnswerRequest();
        answer.setQuestionId(questionId);
        answer.setOptionId(optionId);
        return answer;
    }

    private MarketplaceRankedAttempt attempt(Instant startedAt) {
        User buyer = new User();
        buyer.setUserId("buyer");
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        MarketplaceRankedQuizDefinition definition = new MarketplaceRankedQuizDefinition();
        definition.setTotalQuestionCount(2);
        MarketplaceRankedAttempt value = new MarketplaceRankedAttempt();
        value.setAttemptId(UUID.randomUUID());
        value.setBuyer(buyer);
        value.setPackVersion(version);
        value.setDefinition(definition);
        value.setStatus(MarketplaceRankedAttemptStatus.IN_PROGRESS);
        value.setStartedAt(startedAt);
        value.setExpiresAt(Instant.now().plusSeconds(300));

        firstQuestionId = UUID.randomUUID();
        firstCorrectOptionId = UUID.randomUUID();
        firstWrongOptionId = UUID.randomUUID();
        secondQuestionId = UUID.randomUUID();
        secondCorrectOptionId = UUID.randomUUID();

        ObjectNode questions = objectMapper.createObjectNode();
        ArrayNode questionNodes = questions.putArray("questions");
        questionNodes.add(question(firstQuestionId, firstCorrectOptionId, firstWrongOptionId));
        questionNodes.add(question(secondQuestionId, secondCorrectOptionId, UUID.randomUUID()));
        value.setQuestionSnapshot(questions);

        ObjectNode answers = objectMapper.createObjectNode();
        ArrayNode answerNodes = answers.putArray("answers");
        answerNodes.addObject().put("questionId", firstQuestionId.toString())
                .put("correctOptionId", firstCorrectOptionId.toString());
        answerNodes.addObject().put("questionId", secondQuestionId.toString())
                .put("correctOptionId", secondCorrectOptionId.toString());
        value.setAnswerSnapshot(answers);
        return value;
    }

    private ObjectNode question(UUID questionId, UUID firstOptionId, UUID secondOptionId) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("questionId", questionId.toString());
        ArrayNode options = question.putArray("options");
        options.addObject().put("optionId", firstOptionId.toString());
        options.addObject().put("optionId", secondOptionId.toString());
        return question;
    }
}
