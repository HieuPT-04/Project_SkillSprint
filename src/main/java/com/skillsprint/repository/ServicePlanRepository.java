package com.skillsprint.repository;

import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.ServicePlan;
import com.skillsprint.enums.plan.ServicePlanType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicePlanRepository extends JpaRepository<ServicePlan, UUID> {

    Optional<ServicePlan> findByPlanType(ServicePlanType planType);

    boolean existsByPlanType(ServicePlanType planType);
}
