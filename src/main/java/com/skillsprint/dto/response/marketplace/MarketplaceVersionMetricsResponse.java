package com.skillsprint.dto.response.marketplace;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * Admin operational quality metrics for a single pack version, derived from persisted data with
 * aggregate queries. All rates are 0..1 fractions computed from the counts also returned here.
 */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarketplaceVersionMetricsResponse {

    UUID versionId;
    UUID packId;
    Integer versionNo;
    String versionTitle;

    long learnerCount;
    long completedLearnerCount;
    double completionRate;

    double averageRating;
    long reviewCount;

    long reportCount;
    long openReportCount;
    double openReportRate;

    long rankedAttemptCount;
    long suspiciousRankedAttemptCount;
    double suspiciousRankedAttemptRate;

    long disputeCount;
    long refundedDisputeCount;
    double refundRate;
    long refundedCoinAmount;

    /** Recognized (net-of-refund) platform revenue: sum of RECORDED settlement platform amounts. */
    long recognizedPlatformRevenue;
}
