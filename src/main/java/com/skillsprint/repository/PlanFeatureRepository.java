package com.skillsprint.repository;

import com.skillsprint.entity.PlanFeature;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {

    List<PlanFeature> findByPlanPlanId(UUID planId);

    List<PlanFeature> findByPlanPlanIdIn(Collection<UUID> planIds);

    Optional<PlanFeature> findByPlanPlanIdAndFeatureFeatureKey(UUID planId, String featureKey);
}
