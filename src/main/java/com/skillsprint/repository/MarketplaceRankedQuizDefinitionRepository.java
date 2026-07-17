package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceRankedQuizDefinition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceRankedQuizDefinitionRepository
        extends JpaRepository<MarketplaceRankedQuizDefinition, UUID> {

    Optional<MarketplaceRankedQuizDefinition> findByPackVersionVersionId(UUID packVersionId);
}
