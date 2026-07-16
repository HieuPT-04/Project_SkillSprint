package com.skillsprint.service.payment;

import com.skillsprint.configuration.payment.CoinTopUpProperties;
import com.skillsprint.configuration.payment.CoinTopUpProperties.CoinPackage;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

/**
 * Resolves a client-supplied package key to the server-configured Coin package.
 *
 * <p>This is the only place a top-up's VND and Coin amounts come from. A request can
 * name a package but can never state a price or a Coin quantity.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CoinPackageCatalog {

    CoinTopUpProperties properties;

    /** Configured, structurally valid packages, in configuration order. */
    public List<CoinPackage> availablePackages() {
        if (!properties.enabled()) {
            return List.of();
        }
        return properties.packagesOrEmpty().stream().filter(CoinPackage::valid).toList();
    }

    public CoinPackage require(String packageKey) {
        if (!properties.enabled()) {
            throw new AppException(ErrorCode.COIN_TOP_UP_NOT_AVAILABLE);
        }
        String normalized = normalize(packageKey);
        if (normalized == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cần chọn gói nạp Coin");
        }
        return availablePackages().stream()
                .filter(candidate -> normalize(candidate.key()).equals(normalized))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.COIN_PACKAGE_NOT_FOUND));
    }

    /** VND is charged in whole dong; SePay reports whole-dong transfer amounts. */
    public BigDecimal vndAmountOf(CoinPackage coinPackage) {
        return coinPackage.vndAmount().setScale(0, RoundingMode.HALF_UP);
    }

    private String normalize(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
