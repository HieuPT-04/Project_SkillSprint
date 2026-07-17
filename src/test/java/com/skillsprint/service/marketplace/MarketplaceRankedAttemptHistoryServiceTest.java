package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.response.marketplace.MarketplaceLeaderboardEntryResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptHistoryResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceRankedAttempt;
import com.skillsprint.entity.MarketplaceRankedQuizDefinition;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceRankedAttemptHistoryServiceTest {

    @Mock MarketplaceRankedAttemptRepository attemptRepository;
    @Mock MarketplaceRankedQuizAccessService rankedQuizAccessService;

    MarketplaceRankedAttemptHistoryService service;
    UUID versionId;

    @BeforeEach
    void setUp() {
        service = new MarketplaceRankedAttemptHistoryService(attemptRepository, rankedQuizAccessService);
        versionId = UUID.randomUUID();
    }

    @Test
    void returnsOnlyEligibleCompletedAttemptsForVersionLeaderboard() {
        MarketplaceRankedAttempt first = completedAttempt("First buyer", 100, 12, true);
        MarketplaceRankedAttempt second = completedAttempt("Second buyer", 90, 20, true);
        when(attemptRepository
                .findTop10ByPackVersionVersionIdAndStatusAndSuspiciousFalseAndLeaderboardEligibleTrueOrderByScoreDescDurationSecondsAscCompletedAtAsc(
                        versionId, MarketplaceRankedAttemptStatus.COMPLETED))
                .thenReturn(List.of(first, second));

        List<MarketplaceLeaderboardEntryResponse> response = service.leaderboard("buyer", versionId);

        assertThat(response).extracting(MarketplaceLeaderboardEntryResponse::getRank).containsExactly(1, 2);
        assertThat(response).extracting(MarketplaceLeaderboardEntryResponse::getUserName)
                .containsExactly("First buyer", "Second buyer");
        assertThat(response).extracting(MarketplaceLeaderboardEntryResponse::getScore).containsExactly(100, 90);
        verify(rankedQuizAccessService).requireRankedAccess("buyer", versionId);
    }

    @Test
    void returnsOnlyTheCurrentBuyersVersionAttemptHistoryWithoutAnswerData() {
        MarketplaceRankedAttempt attempt = completedAttempt("Buyer", 80, 42, false);
        when(attemptRepository.findTop50ByBuyerUserIdAndPackVersionVersionIdOrderByStartedAtDesc("buyer", versionId))
                .thenReturn(List.of(attempt));

        List<MarketplaceRankedAttemptHistoryResponse> response = service.history("buyer", versionId);

        assertThat(response).hasSize(1);
        MarketplaceRankedAttemptHistoryResponse entry = response.get(0);
        assertThat(entry.getAttemptId()).isEqualTo(attempt.getAttemptId());
        assertThat(entry.getScore()).isEqualTo(80);
        assertThat(entry.getCorrectCount()).isEqualTo(4);
        assertThat(entry.getQuestionCount()).isEqualTo(5);
        assertThat(entry.isLeaderboardEligible()).isFalse();
        verify(rankedQuizAccessService).requireRankedAccess("buyer", versionId);
    }

    private MarketplaceRankedAttempt completedAttempt(
            String buyerName,
            int score,
            long durationSeconds,
            boolean leaderboardEligible
    ) {
        User buyer = new User();
        buyer.setUserId("buyer");
        buyer.setFullName(buyerName);

        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(versionId);
        MarketplaceRankedQuizDefinition definition = new MarketplaceRankedQuizDefinition();
        definition.setTotalQuestionCount(5);

        MarketplaceRankedAttempt attempt = new MarketplaceRankedAttempt();
        attempt.setAttemptId(UUID.randomUUID());
        attempt.setBuyer(buyer);
        attempt.setPackVersion(version);
        attempt.setDefinition(definition);
        attempt.setAttemptDate(LocalDate.now());
        attempt.setAttemptNumber(1);
        attempt.setStatus(MarketplaceRankedAttemptStatus.COMPLETED);
        attempt.setScore(score);
        attempt.setCorrectCount(4);
        attempt.setDurationSeconds(durationSeconds);
        attempt.setStartedAt(Instant.now().minusSeconds(durationSeconds));
        attempt.setExpiresAt(Instant.now().plusSeconds(60));
        attempt.setCompletedAt(Instant.now());
        attempt.setLeaderboardEligible(leaderboardEligible);
        return attempt;
    }
}
