package com.skillsprint.repository;

import com.skillsprint.entity.MarketplacePurchase;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePurchaseRepository extends JpaRepository<MarketplacePurchase, UUID> {

    boolean existsByUserUserIdAndItemItemIdAndStatus(String userId, UUID itemId, MarketplacePurchaseStatus status);

    List<MarketplacePurchase> findByUserUserIdAndStatusOrderByPurchasedAtDesc(String userId, MarketplacePurchaseStatus status);
}
