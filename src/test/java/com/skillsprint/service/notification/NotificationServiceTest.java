package com.skillsprint.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.response.notification.NotificationResponse;
import com.skillsprint.entity.Notification;
import com.skillsprint.entity.NotificationLog;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.notification.NotificationChannel;
import com.skillsprint.enums.notification.NotificationLogStatus;
import com.skillsprint.enums.notification.NotificationType;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.NotificationLogRepository;
import com.skillsprint.repository.NotificationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    @Mock
    NotificationLogRepository notificationLogRepository;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    NotificationService notificationService;
    User user;
    StudyWorkspace workspace;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                notificationLogRepository,
                messagingTemplate
        );
        user = user("user-1");
        workspace = workspace(user);
    }

    @Test
    void createAndDispatchSavesNotificationLogsAndSendsWebSocketMessage() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setNotificationId(UUID.randomUUID());
            return notification;
        });

        NotificationResponse response = notificationService.createAndDispatch(
                user,
                workspace,
                NotificationType.ROADMAP_READY,
                "Roadmap ready",
                "Your roadmap is ready"
        );

        assertEquals(workspace.getWorkspaceId(), response.getWorkspaceId());
        assertEquals(NotificationType.ROADMAP_READY, response.getType());
        assertEquals("Roadmap ready", response.getTitle());
        assertTrue(!response.isRead());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/users/user-1/notifications"),
                any(NotificationResponse.class)
        );

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        assertEquals(NotificationChannel.IN_APP, logCaptor.getAllValues().get(0).getChannel());
        assertEquals(NotificationLogStatus.SENT, logCaptor.getAllValues().get(0).getStatus());
        assertEquals(NotificationChannel.WEBSOCKET, logCaptor.getAllValues().get(1).getChannel());
        assertEquals(NotificationLogStatus.SENT, logCaptor.getAllValues().get(1).getStatus());
    }

    @Test
    void createAndDispatchRecordsFailedWebSocketLogWhenDispatchThrows() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setNotificationId(UUID.randomUUID());
            return notification;
        });
        doThrow(new IllegalStateException("socket down"))
                .when(messagingTemplate)
                .convertAndSend(eq("/topic/users/user-1/notifications"), any(NotificationResponse.class));

        notificationService.createAndDispatch(
                user,
                workspace,
                NotificationType.AI_SCHEDULE_READY,
                "Calendar ready",
                "Calendar is ready"
        );

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        NotificationLog websocketLog = logCaptor.getAllValues().get(1);
        assertEquals(NotificationChannel.WEBSOCKET, websocketLog.getChannel());
        assertEquals(NotificationLogStatus.FAILED, websocketLog.getStatus());
        assertEquals("socket down", websocketLog.getErrorMessage());
    }

    @Test
    void markAsReadSetsReadTimestampOnlyForOwnedUnreadNotification() {
        Notification notification = notification(false);
        when(notificationRepository.findByNotificationIdAndUserUserId(notification.getNotificationId(), "user-1"))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationResponse response = notificationService.markAsRead("user-1", notification.getNotificationId());

        assertTrue(response.isRead());
        assertEquals(notification.getReadAt(), response.getReadAt());
        assertSame(notification, captureSavedNotification());
    }

    @Test
    void markAllAsReadDelegatesToScopedBulkUpdateAndReturnsCount() {
        when(notificationRepository.markAllAsReadForUser(eq("user-1"), any(Instant.class))).thenReturn(3);

        int updated = notificationService.markAllAsRead("user-1");

        assertEquals(3, updated);
        verify(notificationRepository).markAllAsReadForUser(eq("user-1"), any(Instant.class));
    }

    @Test
    void markAllAsReadReturnsZeroWhenNoUnreadNotifications() {
        when(notificationRepository.markAllAsReadForUser(eq("user-1"), any(Instant.class))).thenReturn(0);

        int updated = notificationService.markAllAsRead("user-1");

        assertEquals(0, updated);
    }

    @Test
    void markAsReadRejectsForeignOrMissingNotification() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByNotificationIdAndUserUserId(notificationId, "user-1"))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> notificationService.markAsRead("user-1", notificationId)
        );

        assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, exception.getErrorCode());
    }

    private Notification captureSavedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private Notification notification(boolean read) {
        Notification notification = new Notification();
        notification.setNotificationId(UUID.randomUUID());
        notification.setUser(user);
        notification.setWorkspace(workspace);
        notification.setType(NotificationType.TASK_REMINDER);
        notification.setTitle("Reminder");
        notification.setMessage("Study now");
        notification.setRead(read);
        notification.setCreatedAt(Instant.parse("2026-06-23T10:00:00Z"));
        return notification;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(user);
        workspace.setName("Java");
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }
}
