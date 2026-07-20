package com.skillsprint.dto.response.marketplace;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/** Server-driven eligibility for opening a dispute on a sale the caller owns. */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarketplaceDisputeEligibilityResponse {

    UUID saleId;
    boolean eligible;
    /** Machine-readable reason when not eligible: NOT_OWNER, ALREADY_REFUNDED, DISPUTE_ACTIVE, SALE_NOT_COMPLETED. */
    String ineligibilityReason;
    /** The active/most-recent dispute for this sale, if any. */
    MarketplaceDisputeResponse existingDispute;
}
