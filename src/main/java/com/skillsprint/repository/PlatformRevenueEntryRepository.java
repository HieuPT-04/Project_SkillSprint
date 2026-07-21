package com.skillsprint.repository;

import com.skillsprint.entity.PlatformRevenueEntry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformRevenueEntryRepository extends JpaRepository<PlatformRevenueEntry, UUID> {

    Optional<PlatformRevenueEntry> findBySaleSaleId(UUID saleId);

    /**
     * Recognized (net-of-refund) platform revenue for a version. The original entry stays immutable
     * for audit; recognition is driven by the authoritative settlement state, so a REVERSED
     * settlement (a completed refund) contributes zero.
     */
    @Query("""
            select coalesce(sum(entry.amount), 0)
            from PlatformRevenueEntry entry
            where entry.sale.packVersion.versionId = :versionId
              and entry.settlement.status = com.skillsprint.enums.marketplace.MarketplaceSettlementStatus.RECORDED
            """)
    long sumRecognizedPlatformRevenueByVersion(@Param("versionId") UUID versionId);

    @Query("""
            select coalesce(sum(entry.amount), 0)
            from PlatformRevenueEntry entry
            where entry.createdAt >= :from
              and entry.createdAt < :to
            """)
    long sumGrossCommissionCoinCreatedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            select coalesce(sum(entry.amount), 0)
            from PlatformRevenueEntry entry
            join MarketplaceRefundDispute dispute on dispute.sale.saleId = entry.sale.saleId
            where dispute.status = com.skillsprint.enums.marketplace.MarketplaceDisputeStatus.REFUNDED
              and dispute.refundedAt >= :from
              and dispute.refundedAt < :to
            """)
    long sumRefundedCommissionCoinBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

}
