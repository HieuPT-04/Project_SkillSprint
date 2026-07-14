package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceItemRepository extends JpaRepository<MarketplaceItem, UUID> {

    List<MarketplaceItem> findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus status);

    List<MarketplaceItem> findByCreatorUserIdOrderByCreatedAtDesc(String userId);

    Optional<MarketplaceItem> findByItemIdAndCreatorUserId(UUID itemId, String userId);
}
