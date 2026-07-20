package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.UpsertMarketplaceReviewRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewCollectionResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewContextResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceReview;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplaceReviewIneligibilityReason;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.MarketplaceReviewMapper;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
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
    MarketplacePackVersionRepository versionRepository;
    MarketplacePackVersionService packVersionService;
    MarketplaceOwnershipService marketplaceOwnershipService;
    MarketplaceVersionAccessService versionAccessService;
    MarketplaceLearningEligibilityService learningEligibilityService;
    MarketplaceReviewMapper reviewMapper;

    @Transactional
    public MarketplaceReviewResponse upsertVersion(
            String userId,
            UUID versionId,
            UpsertMarketplaceReviewRequest request
    ) {
        MarketplacePackVersion version = versionAccessService.requireAndLockAccess(userId, versionId);
        learningEligibilityService.requireCompletedQuiz(userId, versionId);

        MarketplaceReview review = reviewRepository
                .findByPackVersionVersionIdAndUserUserId(versionId, userId)
                .orElseGet(MarketplaceReview::new);
        if (review.getReviewId() == null) {
            review.setPackVersion(version);
            review.setUser(requireUser(userId));
            if (version.getLegacyItemId() != null) {
                review.setItem(itemRepository.findById(version.getLegacyItemId())
                        .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND)));
            }
        }
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        MarketplaceReview saved = reviewRepository.saveAndFlush(review);
        return reviewMapper.toResponse(saved, MarketplacePackVersionIdentity.of(version), userId);
    }

    /** Compatibility adapter for the legacy item-scoped write route. */
    @Transactional
    public MarketplaceReviewResponse upsert(
            String userId,
            UUID itemId,
            UpsertMarketplaceReviewRequest request
    ) {
        MarketplacePackVersion version = packVersionService.findByItemId(itemId).orElse(null);
        if (version != null) {
            return upsertVersion(userId, version.getVersionId(), request);
        }

        // Preserve the legacy denial order for an item that cannot be mapped to a
        // version. Such an item has no version-scoped quiz completion to validate.
        marketplaceOwnershipService.requireActiveOwnership(
                userId,
                itemId,
                "Bạn cần mua Quiz Pack trước khi đánh giá"
        );
        throw new AppException(ErrorCode.MARKETPLACE_REVIEW_QUIZ_COMPLETION_REQUIRED);
    }

    @Transactional(readOnly = true)
    public MarketplaceReviewCollectionResponse getVersionReviews(String viewerId, UUID versionId) {
        MarketplacePackVersion version = requireReadableVersion(viewerId, versionId);
        MarketplacePackVersionIdentity identity = MarketplacePackVersionIdentity.of(version);
        List<MarketplaceReviewResponse> reviews = reviewRepository
                .findByPackVersionVersionIdOrderByUpdatedAtDesc(versionId)
                .stream()
                .map(review -> reviewMapper.toResponse(review, identity, viewerId))
                .toList();
        RatingSummary summary = versionSummary(versionId);

        return MarketplaceReviewCollectionResponse.builder()
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .averageRating(summary.averageRating())
                .reviewCount(summary.reviewCount())
                .reviews(reviews)
                .build();
    }

    @Transactional(readOnly = true)
    public MarketplaceReviewContextResponse getReviewContext(String userId, UUID versionId) {
        MarketplacePackVersion version = requireReadableVersion(userId, versionId);
        MarketplacePackVersionIdentity identity = MarketplacePackVersionIdentity.of(version);
        MarketplaceReviewResponse currentReview = reviewRepository
                .findByPackVersionVersionIdAndUserUserId(versionId, userId)
                .map(review -> reviewMapper.toResponse(review, identity, userId))
                .orElse(null);

        boolean hasAccess = versionAccessService.hasAccess(userId, version);
        boolean completedQuiz = hasAccess && learningEligibilityService.hasCompletedQuiz(userId, versionId);
        MarketplaceReviewIneligibilityReason reason = !hasAccess
                ? MarketplaceReviewIneligibilityReason.ACCESS_REQUIRED
                : completedQuiz ? null : MarketplaceReviewIneligibilityReason.QUIZ_COMPLETION_REQUIRED;

        return MarketplaceReviewContextResponse.builder()
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .eligible(hasAccess && completedQuiz)
                .ineligibilityReason(reason)
                .currentUserReview(currentReview)
                .build();
    }

    /** Compatibility adapter for the legacy item-scoped read route. */
    @Transactional(readOnly = true)
    public List<MarketplaceReviewResponse> getReviews(String viewerId, UUID itemId) {
        MarketplacePackVersion version = packVersionService.findByItemId(itemId).orElse(null);
        if (version != null) {
            return getVersionReviews(viewerId, version.getVersionId()).getReviews();
        }

        return reviewRepository.findByItemItemIdAndPackVersionIsNullOrderByUpdatedAtDesc(itemId)
                .stream()
                .map(review -> reviewMapper.toResponse(review, MarketplacePackVersionIdentity.EMPTY, viewerId))
                .toList();
    }

    private MarketplacePackVersion requireReadableVersion(String viewerId, UUID versionId) {
        MarketplacePackVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
        if (version.getStatus() == MarketplacePackVersionStatus.PUBLISHED
                || versionAccessService.hasAccess(viewerId, version)) {
            return version;
        }
        throw new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND);
    }

    private RatingSummary versionSummary(UUID versionId) {
        return reviewRepository.summarizeByVersionIds(List.of(versionId)).stream()
                .findFirst()
                .map(summary -> new RatingSummary(
                        summary.getAverageRating(),
                        Math.toIntExact(summary.getReviewCount())
                ))
                .orElse(RatingSummary.EMPTY);
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private record RatingSummary(double averageRating, int reviewCount) {
        private static final RatingSummary EMPTY = new RatingSummary(0D, 0);
    }
}
