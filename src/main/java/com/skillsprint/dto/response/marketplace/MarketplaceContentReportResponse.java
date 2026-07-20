package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceReportCategory;
import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportTargetType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * Report view for the reporter (own reports) and admins. Reporter identity fields are populated
 * only for the admin queue; buyer-facing responses leave them null so creators never see who
 * reported them.
 */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarketplaceContentReportResponse {

    UUID reportId;
    UUID packVersionId;
    UUID packId;
    Integer versionNo;
    String versionTitle;
    MarketplaceReportTargetType targetType;
    String targetRef;
    MarketplaceReportCategory category;
    String description;
    MarketplaceReportStatus status;
    String resolutionNote;
    /** Short-lived presigned GET URL for optional evidence; never persisted. Admin view only. */
    String evidenceUrl;
    boolean hasEvidence;
    Instant reviewedAt;
    Instant createdAt;
    Instant updatedAt;

    // Admin-only reporter identity.
    String reporterId;
    String reporterName;
    String reviewedByName;
}
