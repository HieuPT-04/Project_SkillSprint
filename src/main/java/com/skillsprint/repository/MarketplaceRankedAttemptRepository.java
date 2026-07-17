package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceRankedAttempt;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
}
