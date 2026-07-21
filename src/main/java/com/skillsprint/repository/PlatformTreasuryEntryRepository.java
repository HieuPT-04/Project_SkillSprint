package com.skillsprint.repository;

import com.skillsprint.entity.PlatformTreasuryEntry;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryDirection;
import com.skillsprint.enums.marketplace.PlatformTreasuryEntryType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformTreasuryEntryRepository extends JpaRepository<PlatformTreasuryEntry, UUID>, JpaSpecificationExecutor<PlatformTreasuryEntry> {

    Optional<PlatformTreasuryEntry> findByIdempotencyKey(String idempotencyKey);

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
