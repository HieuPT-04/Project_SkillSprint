package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.request.marketplace.SubmitMarketplacePracticeAttemptRequest;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptSubmissionResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePracticeAttempt;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePracticeAttemptRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplacePracticeAttemptSubmissionServiceTest {

    @Mock MarketplaceVersionAccessService accessService;
    @Mock MarketplacePracticeAttemptRepository attemptRepository;
    @Mock MarketplaceVersionProgressService progressService;

    ObjectMapper objectMapper = new ObjectMapper();
    MarketplacePracticeAttemptSubmissionService service;
    MarketplacePracticeAttempt attempt;
    UUID versionId;
    UUID firstQuestionId;
    UUID firstCorrectOptionId;
    UUID firstWrongOptionId;
    UUID secondQuestionId;
    UUID secondCorrectOptionId;

    @BeforeEach
    void setUp() {
        service = new MarketplacePracticeAttemptSubmissionService(
                accessService,
                attemptRepository,
                objectMapper,
                progressService
        );
        attempt = attempt();
        versionId = attempt.getPackVersion().getVersionId();
        when(attemptRepository.findByAttemptIdForUpdate(attempt.getAttemptId()))
                .thenReturn(Optional.of(attempt));
    }

    @Test
    void completesAndScoresUsingServerOwnedAnswers() {
        allowSave();

        MarketplacePracticeAttemptSubmissionResponse response = service.submit(
                "buyer",
                versionId,
                attempt.getAttemptId(),
                request(UUID.randomUUID(), firstCorrectOptionId, secondCorrectOptionId)
        );

        assertThat(response.getScore()).isEqualTo(100);
        assertThat(response.getCorrectCount()).isEqualTo(2);
        assertThat(response.getQuestionCount()).isEqualTo(2);
        assertThat(attempt.getStatus()).isEqualTo(MarketplacePracticeAttemptStatus.COMPLETED);
        assertThat(attempt.getSubmittedAnswers().toString())
                .contains("optionId")
                .doesNotContain("correctOptionId");
        verify(accessService).requireAndLockAccess("buyer", versionId);
        verify(progressService).recordPracticeCompletion(attempt);
    }

    @Test
    void identicalRetryReturnsOriginalResultWithoutSecondWrite() {
        allowSave();
        SubmitMarketplacePracticeAttemptRequest request = request(
                UUID.randomUUID(),
                firstCorrectOptionId,
                secondCorrectOptionId
        );

        MarketplacePracticeAttemptSubmissionResponse first = service.submit(
                "buyer", versionId, attempt.getAttemptId(), request);
        MarketplacePracticeAttemptSubmissionResponse retry = service.submit(
                "buyer", versionId, attempt.getAttemptId(), request);

        assertThat(retry.getCompletedAt()).isEqualTo(first.getCompletedAt());
        assertThat(retry.getScore()).isEqualTo(first.getScore());
        verify(attemptRepository, times(1)).saveAndFlush(any(MarketplacePracticeAttempt.class));
    }

    @Test
    void sameIdempotencyKeyWithDifferentAnswersIsRejected() {
        allowSave();
        UUID key = UUID.randomUUID();
        service.submit("buyer", versionId, attempt.getAttemptId(), request(
                key, firstCorrectOptionId, secondCorrectOptionId));

        assertThatThrownBy(() -> service.submit("buyer", versionId, attempt.getAttemptId(), request(
                key, firstWrongOptionId, secondCorrectOptionId)))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_PRACTICE_SUBMIT_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void rejectsAnswerOutsidePersistedQuestionSnapshot() {
        assertThatThrownBy(() -> service.submit("buyer", versionId, attempt.getAttemptId(), request(
                UUID.randomUUID(), UUID.randomUUID(), secondCorrectOptionId)))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_PRACTICE_ATTEMPT_INVALID));

        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAttemptOwnedByAnotherBuyer() {
        assertThatThrownBy(() -> service.submit("intruder", versionId, attempt.getAttemptId(), request(
                UUID.randomUUID(), firstCorrectOptionId, secondCorrectOptionId)))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(attemptRepository, never()).saveAndFlush(any());
    }

    private void allowSave() {
        when(attemptRepository.saveAndFlush(any(MarketplacePracticeAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SubmitMarketplacePracticeAttemptRequest request(
            UUID key,
            UUID firstOptionId,
            UUID secondOptionId
    ) {
        SubmitMarketplacePracticeAttemptRequest request = new SubmitMarketplacePracticeAttemptRequest();
        request.setIdempotencyKey(key);
        request.setAnswers(List.of(
                answer(firstQuestionId, firstOptionId),
                answer(secondQuestionId, secondOptionId)
        ));
        return request;
    }

    private SubmitMarketplacePracticeAttemptRequest.AnswerRequest answer(UUID questionId, UUID optionId) {
        SubmitMarketplacePracticeAttemptRequest.AnswerRequest answer =
                new SubmitMarketplacePracticeAttemptRequest.AnswerRequest();
        answer.setQuestionId(questionId);
        answer.setOptionId(optionId);
        return answer;
    }

    private MarketplacePracticeAttempt attempt() {
        User buyer = new User();
        buyer.setUserId("buyer");
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());

        firstQuestionId = UUID.randomUUID();
        firstCorrectOptionId = UUID.randomUUID();
        firstWrongOptionId = UUID.randomUUID();
        secondQuestionId = UUID.randomUUID();
        secondCorrectOptionId = UUID.randomUUID();

        MarketplacePracticeAttempt value = new MarketplacePracticeAttempt();
        value.setAttemptId(UUID.randomUUID());
        value.setBuyer(buyer);
        value.setPackVersion(version);
        value.setChapterSequenceNo(1);
        value.setStatus(MarketplacePracticeAttemptStatus.IN_PROGRESS);
        value.setQuestionCount(2);
        value.setStartedAt(Instant.now());

        ObjectNode questionSnapshot = objectMapper.createObjectNode();
        ArrayNode questions = questionSnapshot.putArray("questions");
        questions.add(question(firstQuestionId, firstCorrectOptionId, firstWrongOptionId));
        questions.add(question(secondQuestionId, secondCorrectOptionId, UUID.randomUUID()));
        value.setQuestionSnapshot(questionSnapshot);

        ObjectNode answerSnapshot = objectMapper.createObjectNode();
        ArrayNode answers = answerSnapshot.putArray("answers");
        answers.addObject().put("questionId", firstQuestionId.toString())
                .put("correctOptionId", firstCorrectOptionId.toString());
        answers.addObject().put("questionId", secondQuestionId.toString())
                .put("correctOptionId", secondCorrectOptionId.toString());
        value.setAnswerSnapshot(answerSnapshot);
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
