package com.skillsprint.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.Reminder;
import com.skillsprint.enums.notification.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<Reminder> findByDeliveryStatusAndScheduledAtLessThanEqual(
            DeliveryStatus deliveryStatus,
            Instant scheduledAt
    );
}
