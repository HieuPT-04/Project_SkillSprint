package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class MarketplaceLeaderboardEntryResponse {
    Integer rank; String userName; Integer score; Long durationSeconds; Instant completedAt;
}
