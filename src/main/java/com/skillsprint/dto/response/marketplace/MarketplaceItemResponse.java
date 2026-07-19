package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceItemResponse {

    UUID itemId;
    UUID packId;
    UUID versionId;
    Integer versionNo;
    UUID sourceWorkspaceId;
    String title;
    String description;
    String subject;
    Integer priceCoins;
    MarketplaceItemStatus status;
    Integer chapterCount;
    Integer quizCount;
    Integer questionCount;
    Integer creatorValidationScore;
    MarketplaceQualityJobStatus qualityStatus;
    Integer qualityScore;
    boolean qualityCurrent;
    String reviewNote;
    Instant createdAt;
    Instant publishedAt;
}
