package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter @Builder
public class MarketplaceReviewResponse {
    UUID packId; UUID versionId; Integer versionNo;
    String userName; Integer rating; String comment; Instant updatedAt;
}
