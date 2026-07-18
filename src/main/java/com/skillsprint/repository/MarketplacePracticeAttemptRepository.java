package com.skillsprint.repository;

import com.skillsprint.entity.MarketplacePracticeAttempt;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplacePracticeAttemptRepository extends JpaRepository<MarketplacePracticeAttempt, UUID> {

    Optional<MarketplacePracticeAttempt>
    findByBuyerUserIdAndPackVersionVersionIdAndChapterSequenceNoAndStatus(
            String buyerId,
            UUID versionId,
            Integer chapterSequenceNo,
            MarketplacePracticeAttemptStatus status
    );

    List<MarketplacePracticeAttempt> findTop50ByBuyerUserIdAndPackVersionVersionIdOrderByStartedAtDesc(
            String buyerId,
            UUID versionId
    );

    boolean existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
            String buyerId,
            UUID versionId,
            MarketplacePracticeAttemptStatus status
    );

    @Query("""
            select attempt.chapterSequenceNo as chapterSequenceNo,
                   max(attempt.score) as bestScore,
                   count(attempt.attemptId) as attemptCount,
                   max(attempt.completedAt) as lastCompletedAt
            from MarketplacePracticeAttempt attempt
            where attempt.buyer.userId = :buyerId
              and attempt.packVersion.versionId = :versionId
              and attempt.status = :status
            group by attempt.chapterSequenceNo
            order by attempt.chapterSequenceNo
            """)
    List<ChapterPracticeSummary> summarizeCompletedChapters(
            @Param("buyerId") String buyerId,
            @Param("versionId") UUID versionId,
            @Param("status") MarketplacePracticeAttemptStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from MarketplacePracticeAttempt attempt where attempt.attemptId = :attemptId")
    Optional<MarketplacePracticeAttempt> findByAttemptIdForUpdate(@Param("attemptId") UUID attemptId);

    interface ChapterPracticeSummary {
        Integer getChapterSequenceNo();
        Integer getBestScore();
        Long getAttemptCount();
        java.time.Instant getLastCompletedAt();
    }
}
