package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceRefundDispute;
import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceRefundDisputeRepository extends JpaRepository<MarketplaceRefundDispute, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dispute from MarketplaceRefundDispute dispute where dispute.disputeId = :disputeId")
    Optional<MarketplaceRefundDispute> findByIdForUpdate(@Param("disputeId") UUID disputeId);

    @Query("""
            select count(dispute) > 0
            from MarketplaceRefundDispute dispute
            where dispute.sale.saleId = :saleId
              and dispute.status in (
                  com.skillsprint.enums.marketplace.MarketplaceDisputeStatus.OPEN,
                  com.skillsprint.enums.marketplace.MarketplaceDisputeStatus.UNDER_REVIEW,
                  com.skillsprint.enums.marketplace.MarketplaceDisputeStatus.APPROVED)
            """)
    boolean existsActiveForSale(@Param("saleId") UUID saleId);

    List<MarketplaceRefundDispute> findByBuyerUserIdOrderByCreatedAtDesc(String buyerId);

    Optional<MarketplaceRefundDispute> findByDisputeIdAndBuyerUserId(UUID disputeId, String buyerId);

    @Query("""
            select dispute
            from MarketplaceRefundDispute dispute
            where (:status is null or dispute.status = :status)
            """)
    Page<MarketplaceRefundDispute> searchAdmin(
            @Param("status") MarketplaceDisputeStatus status,
            Pageable pageable
    );

    long countByPackVersionVersionId(UUID versionId);

    long countByPackVersionVersionIdAndStatus(UUID versionId, MarketplaceDisputeStatus status);

    @Query("""
            select coalesce(sum(dispute.refundCoinAmount), 0)
            from MarketplaceRefundDispute dispute
            where dispute.packVersion.versionId = :versionId
              and dispute.status = com.skillsprint.enums.marketplace.MarketplaceDisputeStatus.REFUNDED
            """)
    long sumRefundedCoinAmountByVersion(@Param("versionId") UUID versionId);
}
