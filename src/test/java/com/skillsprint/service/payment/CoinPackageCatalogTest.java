package com.skillsprint.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skillsprint.configuration.payment.CoinTopUpProperties;
import com.skillsprint.configuration.payment.CoinTopUpProperties.CoinPackage;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoinPackageCatalogTest {

    static final CoinPackage COIN_100 = new CoinPackage("COIN_100", 100, new BigDecimal("19000"));
    static final CoinPackage COIN_500 = new CoinPackage("COIN_500", 500, new BigDecimal("85000"));

    @Test
    void resolvesConfiguredPackageByKey() {
        CoinPackage resolved = catalog(true, List.of(COIN_100, COIN_500)).require("COIN_500");

        assertThat(resolved.coinAmount()).isEqualTo(500);
        assertThat(resolved.vndAmount()).isEqualByComparingTo("85000");
    }

    @Test
    void fixedRatePackageUsesOneVndForEachCoin() {
        CoinPackage fixedRatePackage = new CoinPackage("COIN_10000", 10_000, new BigDecimal("10000"));

        assertThat(fixedRatePackage.valid()).isTrue();
        assertThat(fixedRatePackage.vndAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(fixedRatePackage.coinAmount()));
    }

    @Test
    void packageKeyIsMatchedCaseInsensitivelyAndTrimmed() {
        assertThat(catalog(true, List.of(COIN_100)).require("  coin_100 ").key()).isEqualTo("COIN_100");
    }

    @Test
    void unknownPackageKeyIsRejected() {
        assertThatThrownBy(() -> catalog(true, List.of(COIN_100)).require("COIN_999"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.COIN_PACKAGE_NOT_FOUND);
    }

    @Test
    void blankPackageKeyIsRejected() {
        assertThatThrownBy(() -> catalog(true, List.of(COIN_100)).require("  "))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void disabledCatalogOffersNothingAndRejectsEveryKey() {
        CoinPackageCatalog catalog = catalog(false, List.of(COIN_100));

        assertThat(catalog.availablePackages()).isEmpty();
        assertThatThrownBy(() -> catalog.require("COIN_100"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.COIN_TOP_UP_NOT_AVAILABLE);
    }

    @Test
    void structurallyInvalidPackagesAreNotOfferedOrResolvable() {
        List<CoinPackage> misconfigured = List.of(
                new CoinPackage("FREE_COIN", 100, BigDecimal.ZERO),
                new CoinPackage("NO_COIN", 0, new BigDecimal("19000")),
                new CoinPackage("NULL_PRICE", 100, null),
                COIN_100
        );
        CoinPackageCatalog catalog = catalog(true, misconfigured);

        assertThat(catalog.availablePackages()).extracting(CoinPackage::key).containsExactly("COIN_100");
        assertThatThrownBy(() -> catalog.require("FREE_COIN"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.COIN_PACKAGE_NOT_FOUND);
    }

    @Test
    void vndAmountIsChargedInWholeDong() {
        CoinPackageCatalog catalog = catalog(true, List.of(COIN_100));

        assertThat(catalog.vndAmountOf(new CoinPackage("X", 100, new BigDecimal("18999.60"))))
                .isEqualByComparingTo("19000");
    }

    @Test
    void nullPackageListIsTreatedAsEmpty() {
        assertThat(new CoinPackageCatalog(new CoinTopUpProperties(true, null)).availablePackages()).isEmpty();
    }

    private CoinPackageCatalog catalog(boolean enabled, List<CoinPackage> packages) {
        return new CoinPackageCatalog(new CoinTopUpProperties(enabled, packages));
    }
}
