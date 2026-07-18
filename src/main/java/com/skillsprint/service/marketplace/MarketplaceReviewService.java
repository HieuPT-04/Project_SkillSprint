package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.UpsertMarketplaceReviewRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceReview;
import com.skillsprint.entity.User;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.repository.UserRepository;
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
public class MarketplaceReviewService {

    MarketplaceItemRepository itemRepository;
    MarketplaceReviewRepository reviewRepository;
    UserRepository userRepository;
    MarketplacePackVersionService packVersionService;
    MarketplaceOwnershipService marketplaceOwnershipService;
    MarketplaceLearningEligibilityService learningEligibilityService;

    @Transactional
    public MarketplaceReviewResponse upsert(
            String userId,
            UUID itemId,
            UpsertMarketplaceReviewRequest request
    ) {
        MarketplaceOwnershipService.Ownership ownership = marketplaceOwnershipService.requireActiveOwnership(
                userId,
                itemId,
                "Bạn cần mua Quiz Pack trước khi đánh giá"
        );
        MarketplacePackVersion version = ownership.packVersion();
        if (version == null) {
            version = packVersionService.findByItemId(itemId)
                    .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_REVIEW_QUIZ_COMPLETION_REQUIRED));
        }
        learningEligibilityService.requireCompletedQuiz(userId, version.getVersionId());

        MarketplaceReview review = reviewRepository.findByItemItemIdAndUserUserId(itemId, userId)
                .orElseGet(MarketplaceReview::new);
        if (review.getReviewId() == null) {
            review.setItem(requireItem(itemId));
            review.setUser(requireUser(userId));
        }
        review.setPackVersion(version);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        return response(reviewRepository.save(review), MarketplacePackVersionIdentity.of(version));
    }

    @Transactional(readOnly = true)
    public List<MarketplaceReviewResponse> getReviews(UUID itemId) {
        MarketplacePackVersionIdentity fallbackIdentity = packVersionService.identityOf(itemId);
        return reviewRepository.findByItemItemId(itemId).stream()
                .map(review -> response(
                        review,
                        review.getPackVersion() == null
                                ? fallbackIdentity
                                : MarketplacePackVersionIdentity.of(review.getPackVersion())
                ))
                .toList();
    }

    private MarketplaceItem requireItem(UUID itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private MarketplaceReviewResponse response(
            MarketplaceReview review,
            MarketplacePackVersionIdentity identity
    ) {
        return MarketplaceReviewResponse.builder()
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .userName(review.getUser().getFullName())
                .rating(review.getRating())
                .comment(review.getComment())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
