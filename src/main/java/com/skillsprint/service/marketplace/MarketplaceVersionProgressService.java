package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.dto.response.marketplace.MarketplaceVersionProgressResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePracticeAttempt;
import com.skillsprint.entity.MarketplaceVersionProgress;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.repository.MarketplacePracticeAttemptRepository;
import com.skillsprint.repository.MarketplaceVersionProgressRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceVersionProgressService {

    MarketplaceVersionAccessService accessService;
    MarketplaceVersionProgressRepository progressRepository;
    MarketplacePracticeAttemptRepository practiceAttemptRepository;
    MarketplaceLearningEligibilityService eligibilityService;

    @Transactional
    public void recordActivity(MarketplacePracticeAttempt attempt, Instant activityAt) {
        MarketplaceVersionProgress progress = getOrCreate(attempt);
        if (progress.getFirstActivityAt() == null) {
            progress.setFirstActivityAt(attempt.getStartedAt());
        }
        progress.setLastActivityAt(latest(progress.getLastActivityAt(), activityAt));
        progressRepository.save(progress);
    }

    @Transactional
    public void recordPracticeCompletion(MarketplacePracticeAttempt attempt) {
        MarketplaceVersionProgress progress = getOrCreate(attempt);
        List<MarketplacePracticeAttemptRepository.ChapterPracticeSummary> summaries = summaries(
                attempt.getBuyer().getUserId(),
                attempt.getPackVersion().getVersionId()
        );
        int completed = Math.min(summaries.size(), attempt.getPackVersion().getQuizCount());
        progress.setCompletedQuizCount(completed);
        progress.setCompletedChapterCount(Math.min(summaries.size(), attempt.getPackVersion().getChapterCount()));
        progress.setCompletionPercent(completionPercent(completed, attempt.getPackVersion().getQuizCount()));
        if (progress.getFirstActivityAt() == null) {
            progress.setFirstActivityAt(attempt.getStartedAt());
        }
        progress.setLastActivityAt(latest(progress.getLastActivityAt(), attempt.getCompletedAt()));
        progressRepository.save(progress);
    }

    @Transactional(readOnly = true)
    public MarketplaceVersionProgressResponse getProgress(String buyerId, UUID versionId) {
        MarketplacePackVersion version = accessService.requireAccess(buyerId, versionId);
        List<MarketplacePracticeAttemptRepository.ChapterPracticeSummary> summaries = summaries(buyerId, versionId);
        Map<Integer, MarketplacePracticeAttemptRepository.ChapterPracticeSummary> summariesByChapter = new HashMap<>();
        summaries.forEach(summary -> summariesByChapter.put(summary.getChapterSequenceNo(), summary));
        MarketplaceVersionProgress progress = progressRepository
                .findByBuyerUserIdAndPackVersionVersionId(buyerId, versionId)
                .orElse(null);
        int completedQuizCount = Math.min(summaries.size(), version.getQuizCount());
        int completedChapterCount = Math.min(summaries.size(), version.getChapterCount());

        return MarketplaceVersionProgressResponse.builder()
                .versionId(versionId)
                .versionNo(version.getVersionNo())
                .totalChapterCount(version.getChapterCount())
                .totalQuizCount(version.getQuizCount())
                .completedChapterCount(completedChapterCount)
                .completedQuizCount(completedQuizCount)
                .completionPercent(completionPercent(completedQuizCount, version.getQuizCount()))
                .firstActivityAt(progress == null ? null : progress.getFirstActivityAt())
                .lastActivityAt(progress == null ? null : progress.getLastActivityAt())
                .reviewEligible(eligibilityService.hasCompletedQuiz(buyerId, versionId))
                .chapters(chapterProgress(version, summariesByChapter))
                .build();
    }

    private MarketplaceVersionProgress getOrCreate(MarketplacePracticeAttempt attempt) {
        return progressRepository.findByBuyerAndVersionForUpdate(
                        attempt.getBuyer().getUserId(),
                        attempt.getPackVersion().getVersionId()
                )
                .orElseGet(() -> newProgress(attempt));
    }

    private MarketplaceVersionProgress newProgress(MarketplacePracticeAttempt attempt) {
        MarketplaceVersionProgress progress = new MarketplaceVersionProgress();
        progress.setBuyer(attempt.getBuyer());
        progress.setPackVersion(attempt.getPackVersion());
        return progress;
    }

    private List<MarketplacePracticeAttemptRepository.ChapterPracticeSummary> summaries(
            String buyerId,
            UUID versionId
    ) {
        return practiceAttemptRepository.summarizeCompletedChapters(
                buyerId,
                versionId,
                MarketplacePracticeAttemptStatus.COMPLETED
        );
    }

    private List<MarketplaceVersionProgressResponse.ChapterProgressResponse> chapterProgress(
            MarketplacePackVersion version,
            Map<Integer, MarketplacePracticeAttemptRepository.ChapterPracticeSummary> summaries
    ) {
        List<MarketplaceVersionProgressResponse.ChapterProgressResponse> chapters = new ArrayList<>();
        JsonNode content = version.getContent();
        if (content == null || !content.path("chapters").isArray()) {
            return chapters;
        }
        for (JsonNode chapter : content.path("chapters")) {
            int sequenceNo = chapter.path("sequenceNo").asInt();
            MarketplacePracticeAttemptRepository.ChapterPracticeSummary summary = summaries.get(sequenceNo);
            chapters.add(MarketplaceVersionProgressResponse.ChapterProgressResponse.builder()
                    .chapterSequenceNo(sequenceNo)
                    .chapterTitle(chapter.path("title").asText())
                    .completed(summary != null)
                    .bestScore(summary == null ? null : summary.getBestScore())
                    .attemptCount(summary == null ? 0L : summary.getAttemptCount())
                    .lastCompletedAt(summary == null ? null : summary.getLastCompletedAt())
                    .build());
        }
        return chapters;
    }

    private BigDecimal completionPercent(int completed, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private Instant latest(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
