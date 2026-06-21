package com.skillsprint.service.calendar;

import com.skillsprint.entity.CalendarTask;
import com.skillsprint.enums.notification.NotificationType;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.service.notification.NotificationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaskOverdueSchedulerService {

    static ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    CalendarTaskRepository calendarTaskRepository;
    NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.task.overdue.scan-ms:300000}")
    @Transactional
    public void scanOverdueTasks() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        List<CalendarTask> overdueTasks = calendarTaskRepository.findOverdueUnnotifiedTasks(today, time);

        for (CalendarTask task : overdueTasks) {
            try {
                notificationService.createAndDispatch(
                        task.getUser(),
                        task.getWorkspace(),
                        NotificationType.TASK_OVERDUE,
                        "Nhiệm vụ quá hạn",
                        "Nhiệm vụ \"%s\" đã quá hạn. Hãy hoàn thành hoặc dời lịch nhé.".formatted(task.getTitle())
                );
                task.setOverdueNotified(true);
            } catch (Exception ex) {
                log.warn("[TASK_OVERDUE] Failed to notify overdue task {}", task.getTaskId(), ex);
            }
        }
    }
}
