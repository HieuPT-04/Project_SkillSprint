package com.skillsprint.dto.response.marketplace;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceReviewCollectionResponse {
    UUID packId;
    UUID versionId;
    Integer versionNo;
    Double averageRating;
    Integer reviewCount;
    List<MarketplaceReviewResponse> reviews;
}
