package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceCatalogItemResponse {

    UUID itemId;
    String title;
    String description;
    String subject;
    String creatorName;
    Integer priceCoins;
    Integer chapterCount;
    Integer quizCount;
    Integer questionCount;
    Instant publishedAt;
}
