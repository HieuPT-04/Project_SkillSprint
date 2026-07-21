package com.skillsprint.dto.response.marketplace;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlatformTreasurySummaryResponse {
    BigDecimal vndInflow;
    BigDecimal vndOutflow;
    BigDecimal vndNetPosition;
    BigDecimal commissionCoinEarned;
    BigDecimal commissionCoinReversed;
    BigDecimal commissionCoinNetPosition;
}
