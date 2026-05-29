package com.skillsprint.service.notification;

import com.skillsprint.dto.response.notification.NotificationResponse;
import com.skillsprint.entity.Notification;
import com.skillsprint.entity.NotificationLog;
import com.skillsprint.entity.Reminder;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.UploadedMaterial;
import com.skillsprint.entity.User;
import com.skillsprint.enums.notification.NotificationChannel;
import com.skillsprint.enums.notification.NotificationLogStatus;
import com.skillsprint.enums.notification.NotificationType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.NotificationLogRepository;
import com.skillsprint.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationService {

    NotificationRepository notificationRepository;
    NotificationLogRepository notificationLogRepository;
    SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String userId) {
        return notificationRepository.findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toNotificationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyUnreadNotifications(String userId) {
        return notificationRepository.findByUserUserIdAndReadOrderByCreatedAtDesc(userId, false)
                .stream()
                .map(this::toNotificationResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(String userId, UUID notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndUserUserId(notificationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tìm thấy notification"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
        }

        return toNotificationResponse(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationResponse createAndDispatch(
            User user,
            StudyWorkspace workspace,
            NotificationType type,
            String title,
            String message
    ) {
        return createAndDispatch(user, workspace, null, type, title, message);
    }

    @Transactional
    public NotificationResponse createAndDispatch(
            User user,
            StudyWorkspace workspace,
            Reminder reminder,
            NotificationType type,
            String title,
            String message
    ) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setWorkspace(workspace);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(false);

        Notification savedNotification = notificationRepository.save(notification);

        saveNotificationLog(savedNotification, reminder, NotificationChannel.IN_APP, NotificationLogStatus.SENT, null);
        dispatchWebSocket(savedNotification, reminder);

        return toNotificationResponse(savedNotification);
    }

    @Transactional
    public void notifyMaterialProcessingCompleted(UploadedMaterial material) {
        try {
            createAndDispatch(
                    material.getUser(),
                    material.getWorkspace(),
                    NotificationType.MATERIAL_ANALYSIS_DONE,
                    "Tài liệu đã xử lý xong",
                    "Tài liệu \"%s\" đã được xử lý xong và sẵn sàng để tạo roadmap."
                            .formatted(material.getOriginalFileName())
            );
        } catch (Exception ex) {
            log.warn("[NOTIFICATION] Failed to notify material completed: {}", material.getMaterialId(), ex);
        }
    }

    @Transactional
    public void notifyMaterialProcessingFailed(UploadedMaterial material, String reason) {
        try {
            createAndDispatch(
                    material.getUser(),
                    material.getWorkspace(),
                    NotificationType.MATERIAL_PROCESSING_FAILED,
                    "Tài liệu xử lý thất bại",
                    "Tài liệu \"%s\" xử lý thất bại. Lý do: %s"
                            .formatted(material.getOriginalFileName(), reason)
            );
        } catch (Exception ex) {
            log.warn("[NOTIFICATION] Failed to notify material failed: {}", material.getMaterialId(), ex);
        }
    }

    @Transactional
    public void notifyReminder(Reminder reminder) {
        StudyWorkspace workspace = reminder.getWorkspace();

        createAndDispatch(
                workspace.getUser(),
                workspace,
                reminder,
                NotificationType.TASK_REMINDER,
                "Nhắc lịch học",
                reminder.getMessage()
        );
    }

    @Transactional
    public void notifyRoadmapReady(User user, StudyWorkspace workspace) {
        createAndDispatch(
                user,
                workspace,
                NotificationType.ROADMAP_READY,
                "Roadmap đã được tạo",
                "Roadmap học tập của bạn đã sẵn sàng."
        );
    }

    @Transactional
    public void notifyCalendarReady(User user, StudyWorkspace workspace) {
        createAndDispatch(
                user,
                workspace,
                NotificationType.AI_SCHEDULE_READY,
                "Lịch học đã được tạo",
                "Calendar học tập của bạn đã sẵn sàng."
        );
    }

    private void dispatchWebSocket(Notification notification, Reminder reminder) {
        NotificationResponse response = toNotificationResponse(notification);
        String destination = "/topic/users/" + notification.getUser().getUserId() + "/notifications";

        try {
            messagingTemplate.convertAndSend(destination, response);
            saveNotificationLog(notification, reminder, NotificationChannel.WEBSOCKET, NotificationLogStatus.SENT, null);
        } catch (Exception ex) {
            saveNotificationLog(
                    notification,
                    reminder,
                    NotificationChannel.WEBSOCKET,
                    NotificationLogStatus.FAILED,
                    ex.getMessage()
            );
            log.warn("[WEBSOCKET] Failed to send notification {}", notification.getNotificationId(), ex);
        }
    }

    private void saveNotificationLog(
            Notification notification,
            Reminder reminder,
            NotificationChannel channel,
            NotificationLogStatus status,
            String errorMessage
    ) {
        NotificationLog log = new NotificationLog();
        log.setNotification(notification);
        log.setReminder(reminder);
        log.setChannel(channel);
        log.setStatus(status);

        if (NotificationLogStatus.SENT.equals(status)) {
            log.setSentAt(Instant.now());
        }

        log.setErrorMessage(errorMessage);
        notificationLogRepository.save(log);
    }

    private NotificationResponse toNotificationResponse(Notification notification) {
        UUID workspaceId = notification.getWorkspace() == null
                ? null
                : notification.getWorkspace().getWorkspaceId();

        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .workspaceId(workspaceId)
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}