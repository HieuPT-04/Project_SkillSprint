package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import com.skillsprint.entity.Subscription;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.plan.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByUserUserId(String userId);

    Optional<Subscription> findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
            String userId,
            SubscriptionStatus status
    );

    List<Subscription> findByStatusAndEndAtBefore(
            SubscriptionStatus status,
            Instant endAt
    );

    long countByStatus(SubscriptionStatus status);

    @Query("""
            select count(subscription)
            from Subscription subscription
            where subscription.status = :status
              and subscription.plan.planType = :planType
            """)
    long countByStatusAndPlanType(
            @Param("status") SubscriptionStatus status,
            @Param("planType") ServicePlanType planType
    );
}
