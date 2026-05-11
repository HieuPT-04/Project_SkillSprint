package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.Subscription;
import com.skillsprint.enums.plan.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByUserUserId(String userId);

    Optional<Subscription> findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
            String userId,
            SubscriptionStatus status
    );
}
