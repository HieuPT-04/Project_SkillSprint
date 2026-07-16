package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.notification.CreateReminderRequest;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.notification.NotificationResponse;
import com.skillsprint.dto.response.progress.ProgressDashboardResponse;
import com.skillsprint.dto.response.progress.ProgressStepResponse;
import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskSource;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.notification.NotificationType;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import com.skillsprint.enums.session.StudySessionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.notification.NotificationService;
import com.skillsprint.service.notification.ReminderService;
import com.skillsprint.service.progress.ProgressService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgressNotificationApiFlowTest {

    private static final String USER_ID = "progress-notification-flow-user";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    NotificationService notificationService;

    @MockBean
    ReminderService reminderService;

    @MockBean
    ProgressService progressService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID workspaceId;
    UUID notificationId;
    UUID roadmapId;
    UUID taskId;
    UUID sessionId;
    UUID currentStepId;
    UUID nextStepId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        roadmapId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        currentStepId = UUID.randomUUID();
        nextStepId = UUID.randomUUID();
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void anonymousUserCannotUseProgressNotificationReminderEndpoints() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/progress", workspaceId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/reminders", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Study now",
                                  "scheduledAt": "2026-06-24T12:00:00Z"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(notificationService, never()).getMyNotifications(any());
        verify(progressService, never()).getDashboard(any(), any());
        verify(reminderService, never()).createReminder(any(), any(), any());
    }

    @Test
    void notificationEndpointsReturnExpectedShapesAndMapErrors() throws Exception {
        when(notificationService.getMyNotifications(USER_ID)).thenReturn(List.of(unreadNotification()));
        when(notificationService.getMyUnreadNotifications(USER_ID)).thenReturn(List.of(unreadNotification()));
        when(notificationService.markAsRead(USER_ID, notificationId)).thenReturn(readNotification());

        mockMvc.perform(get("/api/notifications")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thành công"))
                .andExpect(jsonPath("$.data[0].notificationId").value(notificationId.toString()))
                .andExpect(jsonPath("$.data[0].read").value(false));

        mockMvc.perform(get("/api/notifications/unread")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("TASK_REMINDER"));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mark notification as read successfully"))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").exists());

        when(notificationService.markAsRead(USER_ID, notificationId))
                .thenThrow(new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void markAllAsReadResolvesUserFromJwtAndReturnsUpdatedCount() throws Exception {
        when(notificationService.markAllAsRead(USER_ID)).thenReturn(2);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Mark all notifications as read successfully"))
                .andExpect(jsonPath("$.data").value(2));

        verify(notificationService).markAllAsRead(USER_ID);
    }

    @Test
    void markAllAsReadIsIdempotentWhenNothingUnread() throws Exception {
        when(notificationService.markAllAsRead(USER_ID)).thenReturn(0);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    void anonymousUserCannotMarkAllNotificationsAsRead() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(notificationService, never()).markAllAsRead(any());
    }

    @Test
    void reminderCreateValidationSuccessAndBusinessErrorsAreMapped() throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/reminders", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reminderType": "GENERAL",
                                  "message": "Review Java basics",
                                  "scheduledAt": "2026-06-24T12:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Create reminder successfully"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(reminderService).createReminder(eq(USER_ID), eq(workspaceId), any(CreateReminderRequest.class));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/reminders", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "",
                                  "scheduledAt": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());

        org.mockito.Mockito.doThrow(new AppException(ErrorCode.WORKSPACE_NOT_FOUND))
                .when(reminderService)
                .createReminder(eq(USER_ID), eq(workspaceId), any(CreateReminderRequest.class));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/reminders", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Review Java basics",
                                  "scheduledAt": "2026-06-24T12:00:00Z"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void progressDashboardReturnsExpectedShapeAndMapsErrors() throws Exception {
        when(progressService.getDashboard(USER_ID, workspaceId)).thenReturn(progressDashboardResponse());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/progress", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy tiến độ học thành công"))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.progressPercent").value(50))
                .andExpect(jsonPath("$.data.todayProgress.completedTasks").value(1))
                .andExpect(jsonPath("$.data.study.completedSessions").value(3))
                .andExpect(jsonPath("$.data.currentSession.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.currentStep.stepId").value(currentStepId.toString()))
                .andExpect(jsonPath("$.data.todayTasks[0].taskId").value(taskId.toString()));

        when(progressService.getDashboard(USER_ID, workspaceId))
                .thenThrow(new AppException(ErrorCode.ROADMAP_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/progress", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private NotificationResponse unreadNotification() {
        return NotificationResponse.builder()
                .notificationId(notificationId)
                .workspaceId(workspaceId)
                .type(NotificationType.TASK_REMINDER)
                .title("Nhắc lịch học")
                .message("Review Java basics")
                .read(false)
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .build();
    }

    private NotificationResponse readNotification() {
        return NotificationResponse.builder()
                .notificationId(notificationId)
                .workspaceId(workspaceId)
                .type(NotificationType.TASK_REMINDER)
                .title("Nhắc lịch học")
                .message("Review Java basics")
                .read(true)
                .readAt(Instant.parse("2026-06-23T12:10:00Z"))
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .build();
    }

    private ProgressDashboardResponse progressDashboardResponse() {
        return ProgressDashboardResponse.builder()
                .workspaceId(workspaceId)
                .roadmapId(roadmapId)
                .roadmapStatus(RoadmapStatus.ACTIVE)
                .progressPercent(new BigDecimal("50.00"))
                .totalSteps(4)
                .completedSteps(2)
                .totalXp(120)
                .totalTasks(3)
                .completedTasks(1)
                .todayTaskCount(1)
                .overdueTaskCount(1)
                .today(LocalDate.parse("2026-06-24"))
                .todayProgress(ProgressDashboardResponse.TodayProgressResponse.builder()
                        .totalTasks(1)
                        .completedTasks(1)
                        .studyMinutes(45)
                        .earnedXp(20)
                        .build())
                .study(ProgressDashboardResponse.StudyStatsResponse.builder()
                        .totalStudyMinutes(180)
                        .completedSessions(3)
                        .currentStreakDays(2)
                        .lastStudyDate(LocalDate.parse("2026-06-23"))
                        .build())
                .currentSession(currentSessionResponse())
                .currentStep(ProgressStepResponse.builder()
                        .stepId(currentStepId)
                        .title("Practice Java basics")
                        .sequenceNo(2)
                        .status(RoadmapStepStatus.CURRENT)
                        .build())
                .nextStep(ProgressStepResponse.builder()
                        .stepId(nextStepId)
                        .title("Review collections")
                        .sequenceNo(3)
                        .status(RoadmapStepStatus.UPCOMING)
                        .build())
                .todayTasks(List.of(calendarTaskResponse()))
                .overdueTasks(List.of(overdueTaskResponse()))
                .build();
    }

    private StudySessionResponse currentSessionResponse() {
        return StudySessionResponse.builder()
                .sessionId(sessionId)
                .workspaceId(workspaceId)
                .calendarTaskId(taskId)
                .roadmapStepId(currentStepId)
                .status(StudySessionStatus.IN_PROGRESS)
                .startedAt(Instant.parse("2026-06-23T12:00:00Z"))
                .durationMinutes(25)
                .taskCompleted(false)
                .minimumRequiredMinutes(15)
                .build();
    }

    private CalendarTaskResponse calendarTaskResponse() {
        return CalendarTaskResponse.builder()
                .taskId(taskId)
                .workspaceId(workspaceId)
                .roadmapId(roadmapId)
                .roadmapStepId(currentStepId)
                .title("Practice Java basics")
                .description("Hands-on practice")
                .taskDate(LocalDate.parse("2026-06-24"))
                .startTime(LocalTime.parse("19:00:00"))
                .endTime(LocalTime.parse("19:45:00"))
                .durationMinutes(45)
                .category(CalendarTaskCategory.PRACTICE)
                .priority(CalendarTaskPriority.HIGH)
                .status(CalendarTaskStatus.COMPLETED)
                .source(CalendarTaskSource.AI_GENERATED)
                .xpReward(20)
                .overdue(false)
                .studySessionEndpoint("/api/calendar/tasks/%s/study-session".formatted(taskId))
                .build();
    }

    private CalendarTaskResponse overdueTaskResponse() {
        return CalendarTaskResponse.builder()
                .taskId(UUID.randomUUID())
                .workspaceId(workspaceId)
                .roadmapId(roadmapId)
                .roadmapStepId(currentStepId)
                .title("Overdue review")
                .taskDate(LocalDate.parse("2026-06-22"))
                .status(CalendarTaskStatus.TODO)
                .priority(CalendarTaskPriority.MEDIUM)
                .category(CalendarTaskCategory.REVIEW)
                .source(CalendarTaskSource.SYSTEM_GENERATED)
                .overdue(true)
                .build();
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("progress-notification-flow@example.com");
        user.setFullName("Progress Notification Flow");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
