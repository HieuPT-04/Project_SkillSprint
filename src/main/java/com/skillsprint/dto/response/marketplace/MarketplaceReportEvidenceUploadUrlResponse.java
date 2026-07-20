package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarketplaceReportEvidenceUploadUrlResponse {

    String uploadUrl;
    String objectKey;
    Instant expiresAt;
}
