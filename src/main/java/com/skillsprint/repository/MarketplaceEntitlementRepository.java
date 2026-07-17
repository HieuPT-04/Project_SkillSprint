package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceEntitlementRepository extends JpaRepository<MarketplaceEntitlement, UUID> {

    boolean existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
            String buyerId,
            UUID packVersionId,
            MarketplaceEntitlementStatus status
    );

    Optional<MarketplaceEntitlement> findByBuyerUserIdAndPackVersionVersionIdAndStatus(
            String buyerId,
            UUID packVersionId,
            MarketplaceEntitlementStatus status
    );

    Optional<MarketplaceEntitlement> findBySourceSaleSaleId(UUID saleId);

    Optional<MarketplaceEntitlement> findFirstByBuyerUserIdAndStatusAndPackVersionPackPackIdAndPackVersionVersionNoLessThanOrderByPackVersionVersionNoDesc(
            String buyerId,
            MarketplaceEntitlementStatus status,
            UUID packId,
            Integer targetVersionNo
    );

    List<MarketplaceEntitlement> findByBuyerUserIdAndStatusOrderByGrantedAtDesc(
            String buyerId,
            MarketplaceEntitlementStatus status
    );
}
