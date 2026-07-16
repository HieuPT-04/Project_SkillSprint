package com.skillsprint.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserUserIdAndReadOrderByCreatedAtDesc(String userId, boolean read);

    Optional<Notification> findByNotificationIdAndUserUserId(UUID notificationId, String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
            set notification.read = true,
                notification.readAt = :readAt
            where notification.user.userId = :userId
              and notification.read = false
            """)
    int markAllAsReadForUser(@Param("userId") String userId, @Param("readAt") Instant readAt);
}