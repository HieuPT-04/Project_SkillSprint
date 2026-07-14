package com.skillsprint.dto.response.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceAdminItemDetailResponse {

    UUID itemId;
    String creatorId;
    String creatorName;
    String title;
    String description;
    String subject;
    Integer priceCoins;
    String status;
    Integer creatorValidationScore;
    String reviewNote;
    Instant createdAt;
    JsonNode content;
}
