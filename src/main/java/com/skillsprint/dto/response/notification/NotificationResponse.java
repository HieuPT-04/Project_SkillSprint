package com.skillsprint.dto.response.notification;

import com.skillsprint.enums.notification.NotificationType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationResponse {

    UUID notificationId;
    UUID workspaceId;
    NotificationType type;
    String title;
    String message;
    boolean read;
    Instant readAt;
    Instant createdAt;
}