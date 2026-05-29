package com.skillsprint.repository;

import com.skillsprint.entity.ServicePlan;
import com.skillsprint.enums.plan.ServicePlanType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicePlanRepository extends JpaRepository<ServicePlan, UUID> {

    Optional<ServicePlan> findByPlanType(ServicePlanType planType);

    boolean existsByPlanType(ServicePlanType planType);

    List<ServicePlan> findByActiveTrueOrderByMonthlyPriceAsc();
}