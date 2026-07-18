package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptHistoryResponse;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePracticeAttempt;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePracticeAttemptRepository;
import com.skillsprint.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePracticeAttemptService {

    MarketplaceVersionAccessService accessService;
    MarketplacePracticeQuizSnapshotService snapshotService;
    MarketplacePracticeAttemptRepository attemptRepository;
    UserRepository userRepository;
    MarketplaceVersionProgressService progressService;

    @Transactional
    public MarketplacePracticeAttemptResponse startOrResume(
            String buyerId,
            UUID versionId,
            int chapterSequenceNo
    ) {
        MarketplacePackVersion version = accessService.requireAndLockAccess(buyerId, versionId);
        return attemptRepository
                .findByBuyerUserIdAndPackVersionVersionIdAndChapterSequenceNoAndStatus(
                        buyerId,
                        versionId,
                        chapterSequenceNo,
                        MarketplacePracticeAttemptStatus.IN_PROGRESS
                )
                .map(attempt -> {
                    progressService.recordActivity(attempt, Instant.now());
                    return response(attempt);
                })
                .orElseGet(() -> createAttempt(buyerId, version, chapterSequenceNo));
    }

    @Transactional(readOnly = true)
    public MarketplacePracticeAttemptResponse getInProgress(
            String buyerId,
            UUID versionId,
            int chapterSequenceNo
    ) {
        accessService.requireAccess(buyerId, versionId);
        MarketplacePracticeAttempt attempt = attemptRepository
                .findByBuyerUserIdAndPackVersionVersionIdAndChapterSequenceNoAndStatus(
                        buyerId,
                        versionId,
                        chapterSequenceNo,
                        MarketplacePracticeAttemptStatus.IN_PROGRESS
                )
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PRACTICE_ATTEMPT_NOT_FOUND));
        return response(attempt);
    }

    @Transactional(readOnly = true)
    public List<MarketplacePracticeAttemptHistoryResponse> history(String buyerId, UUID versionId) {
        accessService.requireAccess(buyerId, versionId);
        return attemptRepository.findTop50ByBuyerUserIdAndPackVersionVersionIdOrderByStartedAtDesc(
                        buyerId,
                        versionId
                ).stream()
                .map(this::historyResponse)
                .toList();
    }

    private MarketplacePracticeAttemptResponse createAttempt(
            String buyerId,
            MarketplacePackVersion version,
            int chapterSequenceNo
    ) {
        MarketplacePracticeQuizSnapshotService.PracticeSnapshot snapshot = snapshotService.create(
                version,
                chapterSequenceNo
        );
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
        MarketplacePracticeAttempt attempt = new MarketplacePracticeAttempt();
        attempt.setBuyer(buyer);
        attempt.setPackVersion(version);
        attempt.setChapterSequenceNo(chapterSequenceNo);
        attempt.setStatus(MarketplacePracticeAttemptStatus.IN_PROGRESS);
        attempt.setQuestionSnapshot(snapshot.questions());
        attempt.setAnswerSnapshot(snapshot.answers());
        attempt.setQuestionCount(snapshot.questionCount());
        attempt.setStartedAt(Instant.now());
        attempt = attemptRepository.save(attempt);
        progressService.recordActivity(attempt, attempt.getStartedAt());
        return response(attempt);
    }

    private MarketplacePracticeAttemptResponse response(MarketplacePracticeAttempt attempt) {
        JsonNode snapshot = attempt.getQuestionSnapshot();
        return MarketplacePracticeAttemptResponse.builder()
                .attemptId(attempt.getAttemptId())
                .versionId(attempt.getPackVersion().getVersionId())
                .versionNo(attempt.getPackVersion().getVersionNo())
                .chapterSequenceNo(attempt.getChapterSequenceNo())
                .chapterTitle(snapshot.path("chapterTitle").asText())
                .quizTitle(snapshot.path("quizTitle").asText())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .questionCount(attempt.getQuestionCount())
                .questions(snapshotService.questionResponses(snapshot))
                .build();
    }

    private MarketplacePracticeAttemptHistoryResponse historyResponse(MarketplacePracticeAttempt attempt) {
        return MarketplacePracticeAttemptHistoryResponse.builder()
                .attemptId(attempt.getAttemptId())
                .chapterSequenceNo(attempt.getChapterSequenceNo())
                .status(attempt.getStatus())
                .score(attempt.getScore())
                .correctCount(attempt.getCorrectCount())
                .questionCount(attempt.getQuestionCount())
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .build();
    }
}
