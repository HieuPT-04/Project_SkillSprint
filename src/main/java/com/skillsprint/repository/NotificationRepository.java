package com.skillsprint.repository;

import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserUserIdAndReadOrderByCreatedAtDesc(String userId, boolean read);
}
