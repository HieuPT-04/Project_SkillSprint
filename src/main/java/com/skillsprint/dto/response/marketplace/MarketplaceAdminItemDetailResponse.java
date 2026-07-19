package com.skillsprint.dto.response.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceAdminItemDetailResponse {

    UUID itemId;
    UUID packId;
    UUID versionId;
    Integer versionNo;
    String creatorId;
    String creatorName;
    String title;
    String description;
    String subject;
    Integer priceCoins;
    String status;
    Integer creatorValidationScore;
    MarketplaceQualityJobResponse qualityJob;
    List<MarketplaceQualityJobResponse> qualityJobHistory;
    String reviewNote;
    Instant createdAt;
    JsonNode content;
}
