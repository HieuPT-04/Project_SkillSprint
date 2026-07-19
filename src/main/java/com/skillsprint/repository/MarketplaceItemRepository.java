package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MarketplaceItemRepository extends JpaRepository<MarketplaceItem, UUID> {

    List<MarketplaceItem> findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus status);

    List<MarketplaceItem> findByStatusAndSubjectIgnoreCaseOrderByPublishedAtDesc(
            MarketplaceItemStatus status,
            String subject
    );

    List<MarketplaceItem> findByCreatorUserIdOrderByCreatedAtDesc(String userId);

    Optional<MarketplaceItem> findByItemIdAndCreatorUserId(UUID itemId, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from MarketplaceItem item where item.itemId = :itemId and item.creator.userId = :userId")
    Optional<MarketplaceItem> findByItemIdAndCreatorUserIdForUpdate(
            @Param("itemId") UUID itemId,
            @Param("userId") String userId
    );
}
