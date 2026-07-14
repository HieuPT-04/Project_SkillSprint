package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceReview;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceReviewRepository extends JpaRepository<MarketplaceReview, UUID> {

    Optional<MarketplaceReview> findByItemItemIdAndUserUserId(UUID itemId, String userId);
}
