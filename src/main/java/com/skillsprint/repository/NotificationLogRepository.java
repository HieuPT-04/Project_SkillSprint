package com.skillsprint.repository;

import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findByNotificationNotificationId(UUID notificationId);

    List<NotificationLog> findByReminderReminderId(UUID reminderId);
}
