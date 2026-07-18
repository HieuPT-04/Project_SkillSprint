package com.skillsprint.dto.response.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceVersionProgressResponse {

    UUID versionId;
    Integer versionNo;
    Integer totalChapterCount;
    Integer totalQuizCount;
    Integer completedChapterCount;
    Integer completedQuizCount;
    BigDecimal completionPercent;
    Instant firstActivityAt;
    Instant lastActivityAt;
    boolean reviewEligible;
    List<ChapterProgressResponse> chapters;

    @Getter
    @Builder
    public static class ChapterProgressResponse {
        Integer chapterSequenceNo;
        String chapterTitle;
        boolean completed;
        Integer bestScore;
        Long attemptCount;
        Instant lastCompletedAt;
    }
}
