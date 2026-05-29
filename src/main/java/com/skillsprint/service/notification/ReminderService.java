package com.skillsprint.service.notification;

import com.skillsprint.dto.request.notification.CreateReminderRequest;
import com.skillsprint.entity.Reminder;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.enums.notification.DeliveryStatus;
import com.skillsprint.enums.notification.ReminderType;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.ReminderRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReminderService {

    ReminderRepository reminderRepository;
    StudyWorkspaceRepository workspaceRepository;
    NotificationService notificationService;

    @Transactional
    public void createReminder(String userId, UUID workspaceId, CreateReminderRequest request) {
        StudyWorkspace workspace = workspaceRepository
                .findByWorkspaceIdAndUserUserIdAndStatusNot(workspaceId, userId, WorkspaceStatus.DELETED)
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));

        Reminder reminder = new Reminder();
        reminder.setWorkspace(workspace);
        reminder.setReminderType(request.getReminderType() == null ? ReminderType.GENERAL : request.getReminderType());
        reminder.setMessage(request.getMessage().trim());
        reminder.setScheduledAt(request.getScheduledAt());
        reminder.setDeliveryStatus(DeliveryStatus.PENDING);

        reminderRepository.save(reminder);
    }

    @Scheduled(fixedDelayString = "${app.reminder.dispatch.fixed-delay-ms:60000}")
    @Transactional
    public void dispatchDueReminders() {
        List<Reminder> reminders = reminderRepository
                .findByDeliveryStatusAndScheduledAtLessThanEqual(DeliveryStatus.PENDING, Instant.now());

        for (Reminder reminder : reminders) {
            try {
                notificationService.notifyReminder(reminder);
                reminder.setDeliveryStatus(DeliveryStatus.SENT);
                reminder.setSentAt(Instant.now());
            } catch (Exception ex) {
                reminder.setDeliveryStatus(DeliveryStatus.FAILED);
                log.warn("[REMINDER] Failed to dispatch reminder {}", reminder.getReminderId(), ex);
            }
        }
    }
}