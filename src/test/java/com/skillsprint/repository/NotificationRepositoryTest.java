package com.skillsprint.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skillsprint.entity.Notification;
import com.skillsprint.entity.User;
import com.skillsprint.enums.notification.NotificationType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;

    User owner;
    User otherUser;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(user("read-all-owner"));
        otherUser = userRepository.saveAndFlush(user("read-all-other"));
    }

    @Test
    void markAllAsReadUpdatesOnlyUnreadNotificationsOfTheOwner() {
        Instant alreadyReadAt = Instant.parse("2026-06-20T08:00:00Z");
        Notification unreadOne = notificationRepository.save(notification(owner, false, null));
        Notification unreadTwo = notificationRepository.save(notification(owner, false, null));
        Notification alreadyRead = notificationRepository.save(notification(owner, true, alreadyReadAt));
        Notification otherUsersUnread = notificationRepository.save(notification(otherUser, false, null));
        notificationRepository.flush();

        Instant readAt = Instant.parse("2026-07-16T10:00:00Z");
        int updated = notificationRepository.markAllAsReadForUser(owner.getUserId(), readAt);

        assertEquals(2, updated);

        Notification reloadedOne = notificationRepository.findById(unreadOne.getNotificationId()).orElseThrow();
        Notification reloadedTwo = notificationRepository.findById(unreadTwo.getNotificationId()).orElseThrow();
        assertTrue(reloadedOne.isRead());
        assertTrue(reloadedTwo.isRead());
        assertEquals(readAt, reloadedOne.getReadAt());
        assertEquals(readAt, reloadedTwo.getReadAt());

        // Already-read notification keeps its original timestamp untouched.
        Notification reloadedAlreadyRead = notificationRepository.findById(alreadyRead.getNotificationId()).orElseThrow();
        assertTrue(reloadedAlreadyRead.isRead());
        assertEquals(alreadyReadAt, reloadedAlreadyRead.getReadAt());

        // Another user's notification must never be modified.
        Notification reloadedOther = notificationRepository.findById(otherUsersUnread.getNotificationId()).orElseThrow();
        assertFalse(reloadedOther.isRead());
        assertNull(reloadedOther.getReadAt());
    }

    @Test
    void markAllAsReadIsIdempotentWhenNoUnreadNotificationsExist() {
        notificationRepository.save(notification(owner, true, Instant.parse("2026-06-20T08:00:00Z")));
        notificationRepository.flush();

        int updated = notificationRepository.markAllAsReadForUser(owner.getUserId(), Instant.now());

        assertEquals(0, updated);
    }

    private Notification notification(User user, boolean read, Instant readAt) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(NotificationType.TASK_REMINDER);
        notification.setTitle("Reminder");
        notification.setMessage("Study now");
        notification.setRead(read);
        notification.setReadAt(readAt);
        return notification;
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }
}
