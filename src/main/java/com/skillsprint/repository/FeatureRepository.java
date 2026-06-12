package com.skillsprint.repository;

import com.skillsprint.entity.Feature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureRepository extends JpaRepository<Feature, UUID> {

    Optional<Feature> findByFeatureKey(String featureKey);

    boolean existsByFeatureKey(String featureKey);

    List<Feature> findAllByOrderByFeatureNameAsc();
}
