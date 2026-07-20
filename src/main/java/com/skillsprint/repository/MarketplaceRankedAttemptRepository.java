package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceRankedAttempt;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceRankedAttemptRepository extends JpaRepository<MarketplaceRankedAttempt, UUID> {

    List<MarketplaceRankedAttempt> findByBuyerUserIdAndPackVersionVersionIdAndStatusOrderByStartedAtDesc(
            String buyerId,
            UUID packVersionId,
            MarketplaceRankedAttemptStatus status
    );

    long countByBuyerUserIdAndPackVersionVersionIdAndAttemptDate(
            String buyerId,
            UUID packVersionId,
            LocalDate attemptDate
    );

    boolean existsByBuyerUserIdAndPackVersionVersionIdAndLeaderboardEligibleTrue(
            String buyerId,
            UUID packVersionId
    );

    boolean existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
            String buyerId,
            UUID packVersionId,
            MarketplaceRankedAttemptStatus status
    );

    List<MarketplaceRankedAttempt>
    findTop10ByPackVersionVersionIdAndStatusAndSuspiciousFalseAndLeaderboardEligibleTrueOrderByScoreDescDurationSecondsAscCompletedAtAsc(
            UUID packVersionId,
            MarketplaceRankedAttemptStatus status
    );

    List<MarketplaceRankedAttempt> findTop50ByBuyerUserIdAndPackVersionVersionIdOrderByStartedAtDesc(
            String buyerId,
            UUID packVersionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from MarketplaceRankedAttempt attempt where attempt.attemptId = :attemptId")
    java.util.Optional<MarketplaceRankedAttempt> findByAttemptIdForUpdate(@Param("attemptId") UUID attemptId);

    long countByPackVersionVersionIdAndStatus(UUID packVersionId, MarketplaceRankedAttemptStatus status);

    long countByPackVersionVersionIdAndStatusAndSuspiciousTrue(
            UUID packVersionId,
            MarketplaceRankedAttemptStatus status
    );
}
