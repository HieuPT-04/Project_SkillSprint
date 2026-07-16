package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceQuizAttemptResponse {

    UUID attemptId;
    UUID itemId;
    UUID packId;
    UUID versionId;
    Integer versionNo;
    Integer score;
    Integer correctCount;
    Integer questionCount;
    Long durationSeconds;
    Instant completedAt;
}
