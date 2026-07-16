package com.skillsprint.repository;

import com.skillsprint.entity.MarketplacePack;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePackRepository extends JpaRepository<MarketplacePack, UUID> {

    Optional<MarketplacePack> findByLegacyItemId(UUID legacyItemId);
}
