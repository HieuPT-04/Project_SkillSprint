package com.skillsprint.configuration.payment;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server-owned Coin package catalogue. The client may only name a package key; the
 * VND and Coin amounts are never taken from the request.
 */
@ConfigurationProperties(prefix = "app.payment.coin")
public record CoinTopUpProperties(boolean enabled, List<CoinPackage> packages) {

    public record CoinPackage(String key, Integer coinAmount, BigDecimal vndAmount) {

        public boolean valid() {
            return key != null && !key.isBlank()
                    && coinAmount != null && coinAmount > 0
                    && vndAmount != null && vndAmount.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    public List<CoinPackage> packagesOrEmpty() {
        return packages == null ? List.of() : packages;
    }
}
