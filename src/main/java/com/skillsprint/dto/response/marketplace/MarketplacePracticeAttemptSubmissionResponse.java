package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplacePracticeAttemptSubmissionResponse {

    UUID attemptId;
    UUID versionId;
    Integer chapterSequenceNo;
    Integer score;
    Integer correctCount;
    Integer questionCount;
    Instant completedAt;
}
