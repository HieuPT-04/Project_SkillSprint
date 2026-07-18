package com.skillsprint.service.marketplace;

import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePracticeAttemptRepository;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceLearningEligibilityService {

    MarketplacePracticeAttemptRepository practiceAttemptRepository;
    MarketplaceRankedAttemptRepository rankedAttemptRepository;

    @Transactional(readOnly = true)
    public boolean hasCompletedQuiz(String buyerId, UUID versionId) {
        return practiceAttemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                buyerId,
                versionId,
                MarketplacePracticeAttemptStatus.COMPLETED
        ) || rankedAttemptRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                buyerId,
                versionId,
                MarketplaceRankedAttemptStatus.COMPLETED
        );
    }

    @Transactional(readOnly = true)
    public void requireCompletedQuiz(String buyerId, UUID versionId) {
        if (!hasCompletedQuiz(buyerId, versionId)) {
            throw new AppException(ErrorCode.MARKETPLACE_REVIEW_QUIZ_COMPLETION_REQUIRED);
        }
    }
}
