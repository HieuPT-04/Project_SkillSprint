package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/** Buyer-safe history without question, answer, or correct-answer snapshots. */
@Getter
@Builder
public class MarketplacePracticeAttemptHistoryResponse {

    UUID attemptId;
    Integer chapterSequenceNo;
    MarketplacePracticeAttemptStatus status;
    Integer score;
    Integer correctCount;
    Integer questionCount;
    Instant startedAt;
    Instant completedAt;
}
