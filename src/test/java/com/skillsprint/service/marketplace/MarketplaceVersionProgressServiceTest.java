package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePracticeAttempt;
import com.skillsprint.entity.MarketplaceVersionProgress;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.repository.MarketplacePracticeAttemptRepository;
import com.skillsprint.repository.MarketplaceVersionProgressRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceVersionProgressServiceTest {

    @Mock MarketplaceVersionAccessService accessService;
    @Mock MarketplaceVersionProgressRepository progressRepository;
    @Mock MarketplacePracticeAttemptRepository practiceAttemptRepository;
    @Mock MarketplaceLearningEligibilityService eligibilityService;
    @InjectMocks MarketplaceVersionProgressService service;

    MarketplacePackVersion version;
    MarketplacePracticeAttempt attempt;

    @BeforeEach
    void setUp() {
        version = version();
        User buyer = new User();
        buyer.setUserId("buyer");
        attempt = new MarketplacePracticeAttempt();
        attempt.setBuyer(buyer);
        attempt.setPackVersion(version);
        attempt.setStartedAt(Instant.parse("2026-07-18T01:00:00Z"));
        attempt.setCompletedAt(Instant.parse("2026-07-18T01:10:00Z"));
    }

    @Test
    void recomputesDistinctCompletedChapterProgressWithoutInflation() {
        MarketplaceVersionProgress progress = new MarketplaceVersionProgress();
        progress.setBuyer(attempt.getBuyer());
        progress.setPackVersion(version);
        when(progressRepository.findByBuyerAndVersionForUpdate("buyer", version.getVersionId()))
                .thenReturn(Optional.of(progress));
        when(practiceAttemptRepository.summarizeCompletedChapters(
                "buyer", version.getVersionId(), MarketplacePracticeAttemptStatus.COMPLETED))
                .thenReturn(List.of(summary(1, 90, 3L), summary(2, 80, 1L)));

        service.recordPracticeCompletion(attempt);

        assertThat(progress.getCompletedQuizCount()).isEqualTo(2);
        assertThat(progress.getCompletedChapterCount()).isEqualTo(2);
        assertThat(progress.getCompletionPercent()).isEqualByComparingTo("50.00");
        verify(progressRepository).save(progress);
    }

    @Test
    void returnsVersionIsolatedChapterProgressAndReviewEligibility() {
        MarketplaceVersionProgress progress = new MarketplaceVersionProgress();
        progress.setFirstActivityAt(Instant.parse("2026-07-18T01:00:00Z"));
        progress.setLastActivityAt(Instant.parse("2026-07-18T02:00:00Z"));
        when(accessService.requireAccess("buyer", version.getVersionId())).thenReturn(version);
        when(progressRepository.findByBuyerUserIdAndPackVersionVersionId("buyer", version.getVersionId()))
                .thenReturn(Optional.of(progress));
        when(practiceAttemptRepository.summarizeCompletedChapters(
                "buyer", version.getVersionId(), MarketplacePracticeAttemptStatus.COMPLETED))
                .thenReturn(List.of(summary(2, 80, 2L)));
        when(eligibilityService.hasCompletedQuiz("buyer", version.getVersionId())).thenReturn(true);

        var response = service.getProgress("buyer", version.getVersionId());

        assertThat(response.getCompletionPercent()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(response.isReviewEligible()).isTrue();
        assertThat(response.getChapters()).hasSize(4);
        assertThat(response.getChapters().get(1).isCompleted()).isTrue();
        assertThat(response.getChapters().get(1).getBestScore()).isEqualTo(80);
        assertThat(response.getChapters().get(0).isCompleted()).isFalse();
    }

    private MarketplacePracticeAttemptRepository.ChapterPracticeSummary summary(
            int chapterSequenceNo,
            int bestScore,
            long attemptCount
    ) {
        return new MarketplacePracticeAttemptRepository.ChapterPracticeSummary() {
            public Integer getChapterSequenceNo() { return chapterSequenceNo; }
            public Integer getBestScore() { return bestScore; }
            public Long getAttemptCount() { return attemptCount; }
            public Instant getLastCompletedAt() { return Instant.parse("2026-07-18T02:00:00Z"); }
        };
    }

    private MarketplacePackVersion version() {
        MarketplacePackVersion value = new MarketplacePackVersion();
        value.setVersionId(UUID.randomUUID());
        value.setVersionNo(2);
        value.setChapterCount(4);
        value.setQuizCount(4);
        ObjectNode content = new ObjectMapper().createObjectNode();
        ArrayNode chapters = content.putArray("chapters");
        for (int chapterNo = 1; chapterNo <= 4; chapterNo++) {
            chapters.addObject().put("sequenceNo", chapterNo).put("title", "Chapter " + chapterNo);
        }
        value.setContent(content);
        return value;
    }
}
