package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePracticeAttempt;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.repository.MarketplacePracticeAttemptRepository;
import com.skillsprint.repository.UserRepository;
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
class MarketplacePracticeAttemptServiceTest {

    @Mock MarketplaceVersionAccessService accessService;
    @Mock MarketplacePracticeQuizSnapshotService snapshotService;
    @Mock MarketplacePracticeAttemptRepository attemptRepository;
    @Mock UserRepository userRepository;
    @Mock MarketplaceVersionProgressService progressService;

    MarketplacePracticeAttemptService service;
    MarketplacePackVersion version;
    ObjectNode questionSnapshot;

    @BeforeEach
    void setUp() {
        service = new MarketplacePracticeAttemptService(
                accessService,
                snapshotService,
                attemptRepository,
                userRepository,
                progressService
        );
        version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setVersionNo(2);
        questionSnapshot = new ObjectMapper().createObjectNode();
        questionSnapshot.put("chapterTitle", "Collections");
        questionSnapshot.put("quizTitle", "Collections Quiz");
        questionSnapshot.putArray("questions");
    }

    @Test
    void startsNewAttemptFromLockedOwnedVersion() {
        User buyer = new User();
        buyer.setUserId("buyer");
        ObjectNode answerSnapshot = new ObjectMapper().createObjectNode();
        when(accessService.requireAndLockAccess("buyer", version.getVersionId())).thenReturn(version);
        when(attemptRepository.findByBuyerUserIdAndPackVersionVersionIdAndChapterSequenceNoAndStatus(
                "buyer", version.getVersionId(), 1, MarketplacePracticeAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(snapshotService.create(version, 1)).thenReturn(
                new MarketplacePracticeQuizSnapshotService.PracticeSnapshot(
                        questionSnapshot,
                        answerSnapshot,
                        5
                )
        );
        when(userRepository.findById("buyer")).thenReturn(Optional.of(buyer));
        when(attemptRepository.save(any(MarketplacePracticeAttempt.class))).thenAnswer(invocation -> {
            MarketplacePracticeAttempt attempt = invocation.getArgument(0);
            attempt.setAttemptId(UUID.randomUUID());
            return attempt;
        });
        when(snapshotService.questionResponses(questionSnapshot)).thenReturn(List.of());

        MarketplacePracticeAttemptResponse response = service.startOrResume("buyer", version.getVersionId(), 1);

        assertThat(response.getChapterSequenceNo()).isEqualTo(1);
        assertThat(response.getQuestionCount()).isEqualTo(5);
        assertThat(response.getStatus()).isEqualTo(MarketplacePracticeAttemptStatus.IN_PROGRESS);
        verify(accessService).requireAndLockAccess("buyer", version.getVersionId());
        verify(progressService).recordActivity(any(MarketplacePracticeAttempt.class), any(Instant.class));
    }

    @Test
    void resumesExistingAttemptWithoutReplacingItsSnapshot() {
        MarketplacePracticeAttempt existing = attempt(MarketplacePracticeAttemptStatus.IN_PROGRESS);
        when(accessService.requireAndLockAccess("buyer", version.getVersionId())).thenReturn(version);
        when(attemptRepository.findByBuyerUserIdAndPackVersionVersionIdAndChapterSequenceNoAndStatus(
                "buyer", version.getVersionId(), 1, MarketplacePracticeAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(existing));
        when(snapshotService.questionResponses(questionSnapshot)).thenReturn(List.of());

        MarketplacePracticeAttemptResponse response = service.startOrResume("buyer", version.getVersionId(), 1);

        assertThat(response.getAttemptId()).isEqualTo(existing.getAttemptId());
        verify(snapshotService, never()).create(any(), any(Integer.class));
        verify(attemptRepository, never()).save(any());
        verify(progressService).recordActivity(
                org.mockito.ArgumentMatchers.eq(existing),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
    }

    @Test
    void returnsOnlyCurrentBuyerVersionHistoryAfterAccessCheck() {
        MarketplacePracticeAttempt completed = attempt(MarketplacePracticeAttemptStatus.COMPLETED);
        completed.setScore(80);
        completed.setCorrectCount(4);
        completed.setCompletedAt(Instant.now());
        when(attemptRepository.findTop50ByBuyerUserIdAndPackVersionVersionIdOrderByStartedAtDesc(
                "buyer", version.getVersionId())).thenReturn(List.of(completed));

        var history = service.history("buyer", version.getVersionId());

        assertThat(history).singleElement().satisfies(item -> {
            assertThat(item.getScore()).isEqualTo(80);
            assertThat(item.getChapterSequenceNo()).isEqualTo(1);
        });
        verify(accessService).requireAccess("buyer", version.getVersionId());
    }

    private MarketplacePracticeAttempt attempt(MarketplacePracticeAttemptStatus status) {
        User buyer = new User();
        buyer.setUserId("buyer");
        MarketplacePracticeAttempt attempt = new MarketplacePracticeAttempt();
        attempt.setAttemptId(UUID.randomUUID());
        attempt.setBuyer(buyer);
        attempt.setPackVersion(version);
        attempt.setChapterSequenceNo(1);
        attempt.setStatus(status);
        attempt.setQuestionCount(5);
        attempt.setQuestionSnapshot(questionSnapshot);
        attempt.setStartedAt(Instant.now());
        return attempt;
    }
}
