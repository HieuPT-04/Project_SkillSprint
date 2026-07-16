package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceCatalogItemResponse {

    UUID itemId;
    UUID packId;
    UUID versionId;
    Integer versionNo;
    String title;
    String description;
    String subject;
    String creatorName;
    Integer priceCoins;
    Integer chapterCount;
    Integer quizCount;
    Integer questionCount;
    Double averageRating;
    Integer reviewCount;
    Instant publishedAt;
}
