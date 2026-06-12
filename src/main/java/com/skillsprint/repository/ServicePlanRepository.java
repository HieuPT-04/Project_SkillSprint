package com.skillsprint.repository;

import com.skillsprint.entity.ServicePlan;
import com.skillsprint.enums.plan.ServicePlanType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServicePlanRepository extends JpaRepository<ServicePlan, UUID> {

    Optional<ServicePlan> findByPlanType(ServicePlanType planType);

    boolean existsByPlanType(ServicePlanType planType);

    List<ServicePlan> findByActiveTrueOrderByMonthlyPriceAsc();

    @Query("""
            select plan
            from ServicePlan plan
            where plan.active = true
              and (plan.publicVisible = true or plan.publicVisible is null)
            order by coalesce(plan.sortOrder, 999999) asc, plan.monthlyPrice asc
            """)
    List<ServicePlan> findVisibleActivePlans();

    List<ServicePlan> findAllByOrderBySortOrderAscMonthlyPriceAsc();
}
