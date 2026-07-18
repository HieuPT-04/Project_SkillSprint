package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplacePracticeAttemptResponse {

    UUID attemptId;
    UUID versionId;
    Integer versionNo;
    Integer chapterSequenceNo;
    String chapterTitle;
    String quizTitle;
    MarketplacePracticeAttemptStatus status;
    Instant startedAt;
    Integer questionCount;
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
