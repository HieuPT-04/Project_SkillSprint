package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.response.marketplace.MarketplaceVersionMetricsResponse;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import com.skillsprint.repository.MarketplaceContentReportRepository;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import com.skillsprint.repository.MarketplaceRefundDisputeRepository;
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.repository.MarketplaceVersionProgressRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceVersionMetricsServiceTest {

    @Mock MarketplacePackVersionRepository versionRepository;
    @Mock MarketplaceReviewRepository reviewRepository;
    @Mock MarketplaceContentReportRepository reportRepository;
    @Mock MarketplaceRankedAttemptRepository rankedAttemptRepository;
    @Mock MarketplaceVersionProgressRepository progressRepository;
    @Mock MarketplaceRefundDisputeRepository disputeRepository;
    @Mock MarketplaceEntitlementRepository entitlementRepository;

    MarketplaceVersionMetricsService service;
    UUID versionId;

    @BeforeEach
    void setUp() {
        service = new MarketplaceVersionMetricsService(
                versionRepository, reviewRepository, reportRepository, rankedAttemptRepository,
                progressRepository, disputeRepository, entitlementRepository);
        versionId = UUID.randomUUID();
    }

    @Test
    void computesRatesFromAggregateCounts() {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(versionId);
        version.setPack(pack);
        version.setVersionNo(2);
        version.setTitle("Pack");

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(progressRepository.countByPackVersionVersionId(versionId)).thenReturn(10L);
        when(progressRepository.countCompletedByVersion(versionId)).thenReturn(4L);
        when(reviewRepository.summarizeByVersionIds(List.of(versionId))).thenReturn(List.of(summary(4.5D, 8L)));
        when(reportRepository.countByPackVersionVersionId(versionId)).thenReturn(5L);
        when(reportRepository.countByPackVersionVersionIdAndStatus(versionId, MarketplaceReportStatus.OPEN)).thenReturn(2L);
        when(rankedAttemptRepository.countByPackVersionVersionIdAndStatus(
                eq(versionId), eq(MarketplaceRankedAttemptStatus.COMPLETED))).thenReturn(20L);
        when(rankedAttemptRepository.countByPackVersionVersionIdAndStatusAndSuspiciousTrue(
                eq(versionId), eq(MarketplaceRankedAttemptStatus.COMPLETED))).thenReturn(1L);
        when(entitlementRepository.countByPackVersionVersionId(versionId)).thenReturn(10L);
        when(disputeRepository.countByPackVersionVersionId(versionId)).thenReturn(3L);
        when(disputeRepository.countByPackVersionVersionIdAndStatus(versionId, MarketplaceDisputeStatus.REFUNDED)).thenReturn(2L);
        when(disputeRepository.sumRefundedCoinAmountByVersion(versionId)).thenReturn(200L);

        MarketplaceVersionMetricsResponse metrics = service.getMetrics(versionId);

        assertThat(metrics.getCompletionRate()).isEqualTo(0.4D);
        assertThat(metrics.getAverageRating()).isEqualTo(4.5D);
        assertThat(metrics.getReviewCount()).isEqualTo(8L);
        assertThat(metrics.getOpenReportRate()).isEqualTo(0.4D);
        assertThat(metrics.getSuspiciousRankedAttemptRate()).isEqualTo(0.05D);
        assertThat(metrics.getRefundRate()).isEqualTo(0.2D);
        assertThat(metrics.getRefundedCoinAmount()).isEqualTo(200L);
    }

    @Test
    void ratesAreZeroWhenNoData() {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(versionId);
        version.setPack(pack);
        version.setVersionNo(1);
        version.setTitle("Empty");
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(reviewRepository.summarizeByVersionIds(List.of(versionId))).thenReturn(List.of());

        MarketplaceVersionMetricsResponse metrics = service.getMetrics(versionId);

        assertThat(metrics.getCompletionRate()).isEqualTo(0D);
        assertThat(metrics.getAverageRating()).isEqualTo(0D);
        assertThat(metrics.getRefundRate()).isEqualTo(0D);
    }

    private MarketplaceReviewRepository.VersionRatingSummary summary(double average, long count) {
        return new MarketplaceReviewRepository.VersionRatingSummary() {
            @Override public UUID getVersionId() { return versionId; }
            @Override public Double getAverageRating() { return average; }
            @Override public Long getReviewCount() { return count; }
        };
    }
}
