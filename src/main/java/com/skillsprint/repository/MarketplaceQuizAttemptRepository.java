package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceQuizAttempt;
import com.skillsprint.enums.marketplace.MarketplaceQuizAttemptType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceQuizAttemptRepository extends JpaRepository<MarketplaceQuizAttempt, UUID> {

    List<MarketplaceQuizAttempt> findByItemItemIdAndAttemptTypeAndSuspiciousFalseOrderByScoreDescDurationSecondsAscCompletedAtAsc(
            UUID itemId,
            MarketplaceQuizAttemptType attemptType
    );
}
