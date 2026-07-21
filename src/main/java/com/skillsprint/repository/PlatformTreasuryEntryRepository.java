package com.skillsprint.repository;

import com.skillsprint.entity.PlatformTreasuryEntry;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryDirection;
import com.skillsprint.enums.marketplace.PlatformTreasuryEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformTreasuryEntryRepository extends JpaRepository<PlatformTreasuryEntry, UUID> {

    Optional<PlatformTreasuryEntry> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select entry from PlatformTreasuryEntry entry
            where (:asset is null or entry.asset = :asset)
              and (:entryType is null or entry.entryType = :entryType)
              and (:from is null or entry.occurredAt >= :from)
              and (:to is null or entry.occurredAt < :to)
            order by entry.occurredAt desc, entry.createdAt desc
            """)
    Page<PlatformTreasuryEntry> search(
            @Param("asset") PlatformTreasuryAsset asset,
            @Param("entryType") PlatformTreasuryEntryType entryType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
            select coalesce(sum(entry.amount), 0)
            from PlatformTreasuryEntry entry
            where entry.asset = :asset and entry.direction = :direction
            """)
    BigDecimal sumAmountByAssetAndDirection(
            @Param("asset") PlatformTreasuryAsset asset,
            @Param("direction") PlatformTreasuryDirection direction
    );
}
