package com.skillsprint.repository;

import com.skillsprint.entity.MarketplacePackVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePackVersionRepository extends JpaRepository<MarketplacePackVersion, UUID> {

    Optional<MarketplacePackVersion> findByLegacyItemId(UUID legacyItemId);

    List<MarketplacePackVersion> findByLegacyItemIdIn(Collection<UUID> legacyItemIds);

    Optional<MarketplacePackVersion> findByPackPackIdAndSaleableTrue(UUID packId);

    Optional<MarketplacePackVersion> findByPackPackIdAndVersionNo(UUID packId, Integer versionNo);
}
