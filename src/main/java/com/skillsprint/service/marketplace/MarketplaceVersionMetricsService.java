package com.skillsprint.service.marketplace;

import com.skillsprint.dto.response.marketplace.MarketplaceVersionMetricsResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceContentReportRepository;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import com.skillsprint.repository.MarketplaceRefundDisputeRepository;
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.repository.MarketplaceVersionProgressRepository;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin operational quality metrics for a pack version. Every signal is derived from persisted data
 * with aggregate count/sum queries — no per-row loading, no AI, no background jobs.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceVersionMetricsService {

    MarketplacePackVersionRepository versionRepository;
    MarketplaceReviewRepository reviewRepository;
    MarketplaceContentReportRepository reportRepository;
    MarketplaceRankedAttemptRepository rankedAttemptRepository;
    MarketplaceVersionProgressRepository progressRepository;
    MarketplaceRefundDisputeRepository disputeRepository;
    MarketplaceEntitlementRepository entitlementRepository;

    @Transactional(readOnly = true)
    public MarketplaceVersionMetricsResponse getMetrics(UUID versionId) {
        MarketplacePackVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));

        long learnerCount = progressRepository.countByPackVersionVersionId(versionId);
        long completedLearnerCount = progressRepository.countCompletedByVersion(versionId);

        Rating rating = reviewRepository.summarizeByVersionIds(List.of(versionId)).stream()
                .findFirst()
                .map(summary -> new Rating(
                        summary.getAverageRating() == null ? 0D : summary.getAverageRating(),
                        summary.getReviewCount() == null ? 0L : summary.getReviewCount()))
                .orElse(new Rating(0D, 0L));

        long reportCount = reportRepository.countByPackVersionVersionId(versionId);
        long openReportCount = reportRepository.countByPackVersionVersionIdAndStatus(
                versionId, MarketplaceReportStatus.OPEN);

        long rankedAttemptCount = rankedAttemptRepository.countByPackVersionVersionIdAndStatus(
                versionId, MarketplaceRankedAttemptStatus.COMPLETED);
        long suspiciousRankedAttemptCount = rankedAttemptRepository
                .countByPackVersionVersionIdAndStatusAndSuspiciousTrue(
                        versionId, MarketplaceRankedAttemptStatus.COMPLETED);

        long entitlementCount = entitlementRepository.countByPackVersionVersionId(versionId);
        long disputeCount = disputeRepository.countByPackVersionVersionId(versionId);
        long refundedDisputeCount = disputeRepository.countByPackVersionVersionIdAndStatus(
                versionId, MarketplaceDisputeStatus.REFUNDED);
        long refundedCoinAmount = disputeRepository.sumRefundedCoinAmountByVersion(versionId);

        return MarketplaceVersionMetricsResponse.builder()
                .versionId(version.getVersionId())
                .packId(version.getPack().getPackId())
                .versionNo(version.getVersionNo())
                .versionTitle(version.getTitle())
                .learnerCount(learnerCount)
                .completedLearnerCount(completedLearnerCount)
                .completionRate(rate(completedLearnerCount, learnerCount))
                .averageRating(rating.average())
                .reviewCount(rating.count())
                .reportCount(reportCount)
                .openReportCount(openReportCount)
                .openReportRate(rate(openReportCount, reportCount))
                .rankedAttemptCount(rankedAttemptCount)
                .suspiciousRankedAttemptCount(suspiciousRankedAttemptCount)
                .suspiciousRankedAttemptRate(rate(suspiciousRankedAttemptCount, rankedAttemptCount))
                .disputeCount(disputeCount)
                .refundedDisputeCount(refundedDisputeCount)
                .refundRate(rate(refundedDisputeCount, entitlementCount))
                .refundedCoinAmount(refundedCoinAmount)
                .build();
    }

    private double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0D : (double) numerator / denominator;
    }

    private record Rating(double average, long count) {
    }
}
