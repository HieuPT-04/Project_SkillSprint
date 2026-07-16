package com.skillsprint.dto.response.marketplace;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/** A server-priced Coin package the buyer may select by {@code packageKey}. */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CoinPackageResponse {

    String packageKey;
    Integer coinAmount;
    BigDecimal vndAmount;
    String currency;
}
