package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceReviewIneligibilityReason;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceReviewContextResponse {
    UUID packId;
    UUID versionId;
    Integer versionNo;
    boolean eligible;
    MarketplaceReviewIneligibilityReason ineligibilityReason;
    MarketplaceReviewResponse currentUserReview;
}
