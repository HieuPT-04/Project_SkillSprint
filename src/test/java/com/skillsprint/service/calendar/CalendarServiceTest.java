package com.skillsprint.service.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.calendar.CreateCalendarTaskRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskRequest;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskSource;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.calendar.ClassifiedBy;
import com.skillsprint.enums.calendar.EisenhowerQuadrant;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.CalendarMapper;
import com.skillsprint.repository.CalendarScheduleRunRepository;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.OnboardingProfileRepository;
import com.skillsprint.repository.RoadmapProgressLogRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.repository.StudySessionRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.service.calendar.ai.GeminiCalendarPlannerClient;
import com.skillsprint.service.notification.NotificationService;
import com.skillsprint.service.points.PointService;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.SubscriptionService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    StudyWorkspaceRepository workspaceRepository;

    @Mock
    RoadmapRepository roadmapRepository;

    @Mock
    RoadmapStepRepository roadmapStepRepository;

    @Mock
    RoadmapProgressLogRepository roadmapProgressLogRepository;

    @Mock
    OnboardingProfileRepository onboardingProfileRepository;

    @Mock
    CalendarScheduleRunRepository scheduleRunRepository;

    @Mock
    CalendarTaskRepository calendarTaskRepository;

    @Mock
    StudySessionRepository studySessionRepository;

    @Mock
    CalendarMapper calendarMapper;

    @Mock
    GeminiCalendarPlannerClient geminiCalendarPlannerClient;

    @Mock
    QuotaService quotaService;

    @Mock
    NotificationService notificationService;

    @Mock
    PointService pointService;

    @Mock
    SubscriptionService subscriptionService;

    CalendarService calendarService;
    User user;
    StudyWorkspace workspace;

    @BeforeEach
    void setUp() {
        calendarService = new CalendarService(
                workspaceRepository,
                roadmapRepository,
                roadmapStepRepository,
                roadmapProgressLogRepository,
                onboardingProfileRepository,
                scheduleRunRepository,
                calendarTaskRepository,
                studySessionRepository,
                calendarMapper,
                geminiCalendarPlannerClient,
                new ObjectMapper(),
                quotaService,
                notificationService,
                pointService,
                subscriptionService
        );
        user = user("user-1");
        workspace = workspace(user);
    }

    @Test
    void createTaskBuildsPersonalTaskWithDefaultScheduleClassification() {
        CalendarTaskResponse expected = CalendarTaskResponse.builder()
                .workspaceId(workspace.getWorkspaceId())
                .title("Read notes")
                .build();
        whenOwnedWorkspace(workspace.getWorkspaceId());
        when(calendarTaskRepository.save(any(CalendarTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarMapper.toTaskResponse(any(CalendarTask.class))).thenReturn(expected);

        CalendarTaskResponse response = calendarService.createTask(
                "user-1",
                workspace.getWorkspaceId(),
                createTaskRequest()
        );

        assertSame(expected, response);

        ArgumentCaptor<CalendarTask> captor = ArgumentCaptor.forClass(CalendarTask.class);
        verify(calendarTaskRepository).save(captor.capture());
        CalendarTask saved = captor.getValue();
        assertSame(workspace, saved.getWorkspace());
        assertSame(user, saved.getUser());
        assertEquals("Read notes", saved.getTitle());
        assertEquals(LocalDate.parse("2026-06-25"), saved.getTaskDate());
        assertEquals(LocalTime.parse("19:00"), saved.getStartTime());
        assertEquals(LocalTime.parse("20:00"), saved.getEndTime());
        assertEquals(CalendarTaskCategory.PERSONAL, saved.getCategory());
        assertEquals(CalendarTaskPriority.MEDIUM, saved.getPriority());
        assertEquals(CalendarTaskStatus.TODO, saved.getStatus());
        assertEquals(CalendarTaskSource.USER_CREATED, saved.getSource());
        assertEquals(EisenhowerQuadrant.SCHEDULE, saved.getEisenhowerQuadrant());
        assertTrue(saved.getImportant());
        assertFalse(saved.getUrgent());
        assertEquals(ClassifiedBy.USER, saved.getClassifiedBy());
    }

    @Test
    void updateTaskRejectsCompletedTaskBeforeMutatingIt() {
        CalendarTask task = task(CalendarTaskStatus.COMPLETED);
        when(calendarTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        AppException exception = assertThrows(
                AppException.class,
                () -> calendarService.updateTask("user-1", task.getTaskId(), updateRequest("20:00", "21:00"))
        );

        assertEquals(ErrorCode.CALENDAR_TASK_ALREADY_COMPLETED, exception.getErrorCode());
        verify(calendarTaskRepository, never()).save(any());
    }

    @Test
    void updateTaskRejectsOverlappingTaskForSameWorkspaceUserAndDate() {
        CalendarTask task = task(CalendarTaskStatus.TODO);
        CalendarTask existing = task(CalendarTaskStatus.TODO);
        existing.setTaskId(UUID.randomUUID());
        existing.setStartTime(LocalTime.parse("19:30"));
        existing.setEndTime(LocalTime.parse("20:30"));
        when(calendarTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        when(calendarTaskRepository.findByWorkspaceWorkspaceIdAndUserUserIdAndTaskDateOrderByStartTimeAscCreatedAtAsc(
                workspace.getWorkspaceId(),
                "user-1",
                LocalDate.parse("2026-06-25")
        )).thenReturn(List.of(existing));

        AppException exception = assertThrows(
                AppException.class,
                () -> calendarService.updateTask("user-1", task.getTaskId(), updateRequest("19:00", "20:00"))
        );

        assertEquals(ErrorCode.CALENDAR_TASK_TIME_CONFLICT, exception.getErrorCode());
        verify(calendarTaskRepository, never()).save(any());
    }

    @Test
    void updateTaskRejectsForeignTaskAsNotFound() {
        CalendarTask task = task(CalendarTaskStatus.TODO);
        task.setUser(user("other-user"));
        when(calendarTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));

        AppException exception = assertThrows(
                AppException.class,
                () -> calendarService.updateTask("user-1", task.getTaskId(), updateRequest("19:00", "20:00"))
        );

        assertEquals(ErrorCode.CALENDAR_TASK_NOT_FOUND, exception.getErrorCode());
    }

    private void whenOwnedWorkspace(UUID workspaceId) {
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspaceId,
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));
    }

    private CreateCalendarTaskRequest createTaskRequest() {
        CreateCalendarTaskRequest request = new CreateCalendarTaskRequest();
        request.setTitle("  Read notes  ");
        request.setDescription("Practice");
        request.setTaskDate(LocalDate.parse("2026-06-25"));
        request.setStartTime(LocalTime.parse("19:00"));
        request.setEndTime(LocalTime.parse("20:00"));
        request.setDurationMinutes(60);
        request.setQuadrant(EisenhowerQuadrant.SCHEDULE);
        return request;
    }

    private UpdateCalendarTaskRequest updateRequest(String start, String end) {
        UpdateCalendarTaskRequest request = new UpdateCalendarTaskRequest();
        request.setTaskDate(LocalDate.parse("2026-06-25"));
        request.setStartTime(LocalTime.parse(start));
        request.setEndTime(LocalTime.parse(end));
        return request;
    }

    private CalendarTask task(CalendarTaskStatus status) {
        CalendarTask task = new CalendarTask();
        task.setTaskId(UUID.randomUUID());
        task.setWorkspace(workspace);
        task.setUser(user);
        task.setTitle("Task");
        task.setTaskDate(LocalDate.parse("2026-06-25"));
        task.setStartTime(LocalTime.parse("18:00"));
        task.setEndTime(LocalTime.parse("19:00"));
        task.setDurationMinutes(60);
        task.setStatus(status);
        task.setCategory(CalendarTaskCategory.PERSONAL);
        task.setPriority(CalendarTaskPriority.MEDIUM);
        return task;
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
