package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entitlement
            from MarketplaceEntitlement entitlement
            where entitlement.buyer.userId = :buyerId
              and entitlement.packVersion.versionId = :versionId
              and entitlement.status = :status
            """)
    Optional<MarketplaceEntitlement> findByBuyerAndVersionAndStatusForUpdate(
            @Param("buyerId") String buyerId,
            @Param("versionId") UUID versionId,
            @Param("status") MarketplaceEntitlementStatus status
    );

    Optional<MarketplaceEntitlement> findBySourceSaleSaleId(UUID saleId);

    Optional<MarketplaceEntitlement>
    findFirstByBuyerUserIdAndPackVersionVersionIdOrderByGrantedAtDesc(String buyerId, UUID versionId);

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

    Optional<MarketplaceEntitlement>
    findFirstByBuyerUserIdAndStatusAndPackVersionPackPackIdOrderByPackVersionVersionNoDesc(
            String buyerId,
            MarketplaceEntitlementStatus status,
            UUID packId
    );
}
