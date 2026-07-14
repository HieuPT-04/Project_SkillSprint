package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.MarketplaceQuizAttempt;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.enums.marketplace.MarketplaceQuizAttemptType;
import com.skillsprint.exception.AppException;
import com.skillsprint.repository.MarketplacePurchaseRepository;
import com.skillsprint.repository.MarketplaceQuizAttemptRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceChallengeServiceTest {

    @Mock MarketplacePurchaseRepository purchaseRepository;
    @Mock MarketplaceQuizPackSnapshotRepository snapshotRepository;
    @Mock MarketplaceQuizAttemptRepository attemptRepository;
    @Mock UserRepository userRepository;
    @InjectMocks MarketplaceChallengeService service;

    @Test
    void submitRejectsBuyerWithoutActivePurchase() {
        UUID itemId = UUID.randomUUID();
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", itemId, MarketplacePurchaseStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> service.submit("buyer", itemId, new com.skillsprint.dto.request.marketplace.SubmitMarketplaceChallengeRequest()))
                .isInstanceOf(AppException.class);
    }

    @Test
    void leaderboardKeepsOnlyBestAttemptPerBuyer() {
        UUID itemId = UUID.randomUUID();
        when(attemptRepository.findByItemItemIdAndAttemptTypeAndSuspiciousFalseOrderByScoreDescDurationSecondsAscCompletedAtAsc(
                itemId, MarketplaceQuizAttemptType.RANKED))
                .thenReturn(List.of(attempt("a", "An", 100, 20), attempt("a", "An", 90, 10), attempt("b", "Binh", 90, 15)));

        var result = service.leaderboard(itemId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserName()).isEqualTo("An");
        assertThat(result.get(1).getUserName()).isEqualTo("Binh");
    }

    private MarketplaceQuizAttempt attempt(String userId, String name, int score, long duration) {
        User user = new User(); user.setUserId(userId); user.setFullName(name);
        MarketplaceQuizAttempt attempt = new MarketplaceQuizAttempt(); attempt.setUser(user); attempt.setScore(score);
        attempt.setDurationSeconds(duration); attempt.setCompletedAt(Instant.now()); return attempt;
    }
}
