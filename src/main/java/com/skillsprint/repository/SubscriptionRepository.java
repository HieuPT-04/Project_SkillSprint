package com.skillsprint.repository;

import java.util.Collection;
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
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByUserUserId(String userId);

    Optional<Subscription> findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
            String userId,
            SubscriptionStatus status
    );

    // --- Hàm mới thêm: Tìm danh sách gói dịch vụ theo lô User ID và trạng thái Active ---
    List<Subscription> findByUserUserIdInAndStatus(
            Collection<String> userIds,
            SubscriptionStatus status
    );

    @Query("""
            select s from Subscription s
            join fetch s.user
            join fetch s.plan
            where s.user.userId in :userIds
            """)
    List<Subscription> findByUserUserIdIn(@Param("userIds") Collection<String> userIds);

    @Query("""
            select s from Subscription s
            join fetch s.user
            join fetch s.plan
            where s.user.userId = :userId
            order by s.createdAt desc
            """)
    List<Subscription> findAllByUserUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

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