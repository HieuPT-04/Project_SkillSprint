package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Buyer-safe Ranked Quiz attempt history. It intentionally excludes questions,
 * options, submitted answers, correct answers, and anti-cheat internals.
 */
@Getter
@Builder
public class MarketplaceRankedAttemptHistoryResponse {
    UUID attemptId;
    LocalDate attemptDate;
    Integer attemptNumber;
    MarketplaceRankedAttemptStatus status;
    Integer score;
    Integer correctCount;
    Integer questionCount;
    Long durationSeconds;
    Instant startedAt;
    Instant expiresAt;
    Instant completedAt;
    boolean leaderboardEligible;
}
