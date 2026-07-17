package com.skillsprint.repository;

import com.skillsprint.entity.MarketplacePurchase;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePurchaseRepository extends JpaRepository<MarketplacePurchase, UUID> {

    boolean existsByUserUserIdAndItemItemId(String userId, UUID itemId);

    boolean existsByUserUserIdAndItemItemIdAndStatus(String userId, UUID itemId, MarketplacePurchaseStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select purchase
            from MarketplacePurchase purchase
            where purchase.user.userId = :buyerId
              and purchase.item.itemId = :itemId
              and purchase.status = :status
            """)
    java.util.Optional<MarketplacePurchase> findByBuyerAndItemAndStatusForUpdate(
            @Param("buyerId") String buyerId,
            @Param("itemId") UUID itemId,
            @Param("status") MarketplacePurchaseStatus status
    );

    List<MarketplacePurchase> findByUserUserIdAndStatusOrderByPurchasedAtDesc(String userId, MarketplacePurchaseStatus status);
}
