package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class MarketplacePurchaseResponse {
    UUID purchaseId;
    UUID itemId;
    UUID packId;
    UUID versionId;
    Integer versionNo;
    Integer priceCoins;
    Integer remainingCoins;
    Instant purchasedAt;
}
