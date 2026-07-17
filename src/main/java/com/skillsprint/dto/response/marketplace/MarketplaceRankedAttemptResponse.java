package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceRankedAttemptResponse {

    UUID attemptId;
    UUID versionId;
    Integer versionNo;
    MarketplaceRankedAttemptStatus status;
    LocalDate attemptDate;
    Integer attemptNumber;
    Instant startedAt;
    Instant expiresAt;
    Integer totalQuestionCount;
    Integer attemptsRemaining;
    List<QuestionResponse> questions;

    @Getter
    @Builder
    public static class QuestionResponse {
        UUID questionId;
        String type;
        String text;
        List<OptionResponse> options;
    }

    @Getter
    @Builder
    public static class OptionResponse {
        UUID optionId;
        String label;
        String text;
    }
}
