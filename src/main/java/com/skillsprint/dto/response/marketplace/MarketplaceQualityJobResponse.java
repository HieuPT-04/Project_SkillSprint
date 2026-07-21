package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketplaceQualityJobResponse {

    UUID jobId;
    UUID versionId;
    MarketplaceQualityJobStatus status;
    Integer score;
    boolean currentSnapshot;
    Integer retryCount;
    Integer maxRetries;
    String errorCode;
    Instant startedAt;
    Instant completedAt;
    Instant createdAt;
    QualityReportResponse report;

    @Getter
    @Builder
    public static class QualityReportResponse {
        Integer passingScore;
        Integer blockingIssueCount;
        Integer chapterCount;
        Integer questionCount;
        List<QualityIssueResponse> issues;
    }

    @Getter
    @Builder
    public static class QualityIssueResponse {
        String code;
        String severity;
        Integer chapterSequenceNo;
        UUID questionId;
        String message;
    }
}
