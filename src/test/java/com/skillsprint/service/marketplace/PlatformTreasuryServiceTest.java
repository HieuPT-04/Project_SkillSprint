package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryDirection;
import com.skillsprint.enums.marketplace.PlatformTreasuryEntryType;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.PlatformTreasuryEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformTreasuryServiceTest {

    @Mock PlatformTreasuryEntryRepository treasuryEntryRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @InjectMocks PlatformTreasuryService service;

    @Test
    void summarySeparatesSubscriptionPaymentsFromCoinTopUps() {
        when(treasuryEntryRepository.sumAmountByAssetAndDirection(
                any(PlatformTreasuryAsset.class), any(PlatformTreasuryDirection.class)))
                .thenReturn(new BigDecimal("100"));
        when(treasuryEntryRepository.sumAmountByAssetAndDirectionAndEntryType(
                any(PlatformTreasuryAsset.class),
                any(PlatformTreasuryDirection.class),
                any(PlatformTreasuryEntryType.class)))
                .thenReturn(new BigDecimal("40"));

        var summary = service.getSummary();

        assertThat(summary.getVndInflow()).isEqualByComparingTo("100");
        assertThat(summary.getSubscriptionPaymentVnd()).isEqualByComparingTo("40");
        assertThat(summary.getCoinTopUpVnd()).isEqualByComparingTo("40");
    }

    @Test
    void monthlySummariesIncludeAReconciledBreakdownForEveryMonth() {
        when(treasuryEntryRepository.sumAmountByAssetAndDirectionAndOccurredAtBetween(
                any(PlatformTreasuryAsset.class),
                any(PlatformTreasuryDirection.class),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(new BigDecimal("100"));
        when(treasuryEntryRepository.sumAmountByAssetAndDirectionAndEntryTypeAndOccurredAtBetween(
                any(PlatformTreasuryAsset.class),
                any(PlatformTreasuryDirection.class),
                any(PlatformTreasuryEntryType.class),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(new BigDecimal("40"));
        when(paymentTransactionRepository.countDistinctPaidSubscriptionBuyersBetween(
                any(Instant.class), any(Instant.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(12L);

        var summaries = service.getMonthlySummaries(3);

        assertThat(summaries).hasSize(3).allSatisfy(summary -> {
            assertThat(summary.getVndInflow()).isEqualByComparingTo("100");
            assertThat(summary.getSubscriptionPaymentVnd()).isEqualByComparingTo("40");
            assertThat(summary.getSubscriptionPurchaserCount()).isEqualTo(12L);
            assertThat(summary.getCoinTopUpVnd()).isEqualByComparingTo("40");
            assertThat(summary.getVndOutflow()).isEqualByComparingTo("100");
            assertThat(summary.getVndNetPosition()).isZero();
            assertThat(summary.getCommissionCoinNetPosition()).isZero();
        });
        verify(treasuryEntryRepository, org.mockito.Mockito.times(12))
                .sumAmountByAssetAndDirectionAndOccurredAtBetween(
                        any(PlatformTreasuryAsset.class),
                        any(PlatformTreasuryDirection.class),
                        any(Instant.class),
                        any(Instant.class));
        verify(treasuryEntryRepository, org.mockito.Mockito.times(6))
                .sumAmountByAssetAndDirectionAndEntryTypeAndOccurredAtBetween(
                        any(PlatformTreasuryAsset.class),
                        any(PlatformTreasuryDirection.class),
                        any(PlatformTreasuryEntryType.class),
                        any(Instant.class),
                        any(Instant.class));
        verify(paymentTransactionRepository, org.mockito.Mockito.times(3))
                .countDistinctPaidSubscriptionBuyersBetween(
                        any(Instant.class), any(Instant.class), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void subscriptionPurchaseSummaryCountsDistinctBuyersForTheSelectedPlan() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-01T00:00:00Z");
        UUID planId = UUID.randomUUID();
        when(paymentTransactionRepository.countDistinctPaidSubscriptionBuyersBetween(from, to, planId)).thenReturn(8L);

        var summary = service.getSubscriptionPurchaseSummary(from, to, planId);

        assertThat(summary.getPurchaserCount()).isEqualTo(8L);
        verify(paymentTransactionRepository).countDistinctPaidSubscriptionBuyersBetween(from, to, planId);
    }
}
