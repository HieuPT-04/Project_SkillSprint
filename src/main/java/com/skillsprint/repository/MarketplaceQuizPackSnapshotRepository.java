package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceQuizPackSnapshotRepository extends JpaRepository<MarketplaceQuizPackSnapshot, UUID> {

    Optional<MarketplaceQuizPackSnapshot> findByItemItemId(UUID itemId);
}
