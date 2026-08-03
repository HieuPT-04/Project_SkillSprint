package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryDirection;
import com.skillsprint.repository.PlatformTreasuryEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformTreasuryServiceTest {

    @Mock PlatformTreasuryEntryRepository treasuryEntryRepository;
    @InjectMocks PlatformTreasuryService service;

    @Test
    void monthlySummariesIncludeAReconciledBreakdownForEveryMonth() {
        when(treasuryEntryRepository.sumAmountByAssetAndDirectionAndOccurredAtBetween(
                any(PlatformTreasuryAsset.class),
                any(PlatformTreasuryDirection.class),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(new BigDecimal("100"));

        var summaries = service.getMonthlySummaries(3);

        assertThat(summaries).hasSize(3).allSatisfy(summary -> {
            assertThat(summary.getVndInflow()).isEqualByComparingTo("100");
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
    }
}
