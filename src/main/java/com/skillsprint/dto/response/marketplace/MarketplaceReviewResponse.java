package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceReviewResponse {
    UUID reviewId;
    UUID packId;
    UUID versionId;
    Integer versionNo;
    /** Kept during the item-endpoint compatibility period. */
    String userName;
    String reviewerName;
    Integer rating;
    String comment;
    Instant createdAt;
    Instant updatedAt;
    boolean mine;
}
