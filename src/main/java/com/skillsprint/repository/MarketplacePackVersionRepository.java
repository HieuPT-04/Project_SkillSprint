package com.skillsprint.repository;

import com.skillsprint.entity.MarketplacePackVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePackVersionRepository extends JpaRepository<MarketplacePackVersion, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from MarketplacePackVersion version where version.versionId = :versionId")
    Optional<MarketplacePackVersion> findByVersionIdForUpdate(@Param("versionId") UUID versionId);

    Optional<MarketplacePackVersion> findByLegacyItemId(UUID legacyItemId);

    List<MarketplacePackVersion> findByLegacyItemIdIn(Collection<UUID> legacyItemIds);

    Optional<MarketplacePackVersion> findByPackPackIdAndSaleableTrue(UUID packId);

    Optional<MarketplacePackVersion> findByPackPackIdAndVersionNo(UUID packId, Integer versionNo);
}
