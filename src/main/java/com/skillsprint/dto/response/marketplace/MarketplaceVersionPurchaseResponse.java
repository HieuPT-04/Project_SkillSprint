package com.skillsprint.dto.response.marketplace;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/** Immutable receipt shape for a successful version-aware Coin checkout. */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarketplaceVersionPurchaseResponse {

    UUID saleId;
    UUID entitlementId;
    UUID packId;
    UUID packVersionId;
    Integer versionNo;
    Integer grossCoinAmount;
    Integer creatorAmount;
    Integer platformAmount;
    Integer remainingCoinBalance;
}
