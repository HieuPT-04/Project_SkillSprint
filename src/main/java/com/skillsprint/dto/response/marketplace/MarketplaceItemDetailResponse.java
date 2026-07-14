package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceItemDetailResponse {

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
    List<ChapterPreview> chapters;
    List<QuestionPreview> previewQuestions;

    @Getter
    @Builder
    public static class ChapterPreview {
        Integer sequenceNo;
        String title;
        String summary;
        Integer questionCount;
    }

    @Getter
    @Builder
    public static class QuestionPreview {
        UUID questionId;
        String question;
        List<OptionPreview> options;
    }

    @Getter
    @Builder
    public static class OptionPreview {
        UUID optionId;
        String label;
        String text;
    }
}
