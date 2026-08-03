package com.skillsprint.dto.response.marketplace;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlatformTreasurySubscriptionPurchaseSummaryResponse {
    long purchaserCount;
}
