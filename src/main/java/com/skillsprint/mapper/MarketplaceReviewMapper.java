package com.skillsprint.mapper;

import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.entity.MarketplaceReview;
import com.skillsprint.service.marketplace.MarketplacePackVersionIdentity;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceReviewMapper {

    public MarketplaceReviewResponse toResponse(
            MarketplaceReview review,
            MarketplacePackVersionIdentity identity,
            String viewerId
    ) {
        String reviewerName = review.getUser().getFullName();
        return MarketplaceReviewResponse.builder()
                .reviewId(review.getReviewId())
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .userName(reviewerName)
                .reviewerName(reviewerName)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .mine(viewerId != null && viewerId.equals(review.getUser().getUserId()))
                .build();
    }
}
