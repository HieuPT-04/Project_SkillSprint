package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceRankedQuestionSelection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceRankedQuestionSelectionRepository
        extends JpaRepository<MarketplaceRankedQuestionSelection, UUID> {

    List<MarketplaceRankedQuestionSelection> findByDefinitionDefinitionIdOrderByStepOrderAscSelectionOrderAsc(
            UUID definitionId
    );
}
