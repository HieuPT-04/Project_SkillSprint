package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePracticeAttemptRepository;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceLearningEligibilityServiceTest {

    @Mock MarketplacePracticeAttemptRepository practiceAttemptRepository;
    @Mock MarketplaceRankedAttemptRepository rankedAttemptRepository;
    @InjectMocks MarketplaceLearningEligibilityService service;

    @Test
    void completedPracticeUnlocksReview() {
        UUID versionId = UUID.randomUUID();
        when(practiceAttemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", versionId, MarketplacePracticeAttemptStatus.COMPLETED)).thenReturn(true);

        assertThat(service.hasCompletedQuiz("buyer", versionId)).isTrue();
        verify(rankedAttemptRepository, never()).existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", versionId, MarketplaceRankedAttemptStatus.COMPLETED);
    }

    @Test
    void completedRankedQuizUnlocksReview() {
        UUID versionId = UUID.randomUUID();
        when(rankedAttemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", versionId, MarketplaceRankedAttemptStatus.COMPLETED)).thenReturn(true);

        assertThat(service.hasCompletedQuiz("buyer", versionId)).isTrue();
    }

    @Test
    void rejectsReviewWhenNoQuizWasCompletedForThatVersion() {
        UUID versionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.requireCompletedQuiz("buyer", versionId))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_REVIEW_QUIZ_COMPLETION_REQUIRED));
    }
}
