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
import com.skillsprint.dto.request.calendar.GenerateCalendarRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskRequest;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.OnboardingProfile;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskSource;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.calendar.ClassifiedBy;
import com.skillsprint.enums.calendar.EisenhowerQuadrant;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
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
import com.skillsprint.service.calendar.ai.AiCalendarPlanDraft;
import com.skillsprint.service.calendar.ai.AiCalendarTaskSuggestion;
import com.skillsprint.service.calendar.ai.GeminiCalendarPlannerClient;
import com.skillsprint.service.notification.NotificationService;
import com.skillsprint.service.points.PointService;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.SubscriptionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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

    @Test
    void generateSchedulesEveryTaskInsideTheSingleSelectedTimeSlot() {
        List<String> slots = List.of("08:00-10:00");
        List<CalendarTask> saved = runGenerateWithSlots(slots);

        assertEquals(4, saved.size());
        assertAllTasksWithinWindows(saved, slots);
    }

    @Test
    void generateSchedulesTasksInsideBothSelectedSlotsWithoutSpillingBetweenThem() {
        // Reproduces the original bug: with two non-contiguous windows the planner used to
        // stack sessions back-to-back from the first window (e.g. 10:00-12:00) instead of
        // using the afternoon window. Every task must now sit inside a selected window.
        List<String> slots = List.of("08:00-10:00", "14:00-16:00");
        List<CalendarTask> saved = runGenerateWithSlots(slots);

        assertEquals(4, saved.size());
        assertAllTasksWithinWindows(saved, slots);
    }

    @Test
    void generateSchedulesTasksInsideThreeSelectedSlots() {
        List<String> slots = List.of("08:00-10:00", "14:00-16:00", "19:00-21:00");
        List<CalendarTask> saved = runGenerateWithSlots(slots);

        assertEquals(4, saved.size());
        assertAllTasksWithinWindows(saved, slots);
    }

    @Test
    void generateRejectsEmptyAvailabilityInsteadOfProducingRandomSchedule() {
        prepareGenerateMocks(List.of());

        AppException exception = assertThrows(
                AppException.class,
                () -> calendarService.generate("user-1", workspace.getWorkspaceId(), generateRequest(1))
        );

        assertEquals(ErrorCode.CALENDAR_TIME_SLOT_REQUIRED, exception.getErrorCode());
        verify(calendarTaskRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void generateRejectsMalformedAvailabilityInsteadOfProducingRandomSchedule() {
        prepareGenerateMocks(List.of("not-a-time", "25:99-30:00"));

        AppException exception = assertThrows(
                AppException.class,
                () -> calendarService.generate("user-1", workspace.getWorkspaceId(), generateRequest(2))
        );

        assertEquals(ErrorCode.CALENDAR_TIME_SLOT_REQUIRED, exception.getErrorCode());
        verify(calendarTaskRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateFallsBackToRuleBasedPlanWhenAiClientReturnsNull() {
        // Simulates Gemini hitting MAX_TOKENS / truncated / blocked output: generate(...) returns null.
        List<String> slots = List.of("08:00-10:00", "14:00-16:00");
        prepareGenerateMocks(slots);
        when(scheduleRunRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarTaskRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> new ArrayList<>((List<CalendarTask>) invocation.getArgument(0)));
        when(geminiCalendarPlannerClient.isReady()).thenReturn(true);
        when(geminiCalendarPlannerClient.generate(any())).thenReturn(null);

        calendarService.generate("user-1", workspace.getWorkspaceId(), generateRequest(2));

        ArgumentCaptor<List<CalendarTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarTaskRepository).saveAllAndFlush(captor.capture());
        List<CalendarTask> saved = captor.getValue();

        assertEquals(4, saved.size());
        assertAllTasksWithinWindows(saved, slots);
        saved.forEach(task -> assertEquals(CalendarTaskSource.SYSTEM_GENERATED, task.getSource()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateRejectsAiDraftScheduledOutsideAllowedTimeWindows() {
        List<String> slots = List.of("08:00-10:00");
        prepareGenerateMocks(slots);
        when(scheduleRunRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarTaskRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> new ArrayList<>((List<CalendarTask>) invocation.getArgument(0)));
        // AI proposes a structurally valid plan whose tasks fall at 23:00, outside the 08:00-10:00 window.
        when(geminiCalendarPlannerClient.isReady()).thenReturn(true);
        when(geminiCalendarPlannerClient.generate(any())).thenReturn(outOfWindowDraft());

        calendarService.generate("user-1", workspace.getWorkspaceId(), generateRequest(1));

        ArgumentCaptor<List<CalendarTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarTaskRepository).saveAllAndFlush(captor.capture());
        List<CalendarTask> saved = captor.getValue();

        assertEquals(4, saved.size());
        assertAllTasksWithinWindows(saved, slots);
        // The AI draft was rejected, so the rule-based plan is persisted instead of the AI one.
        saved.forEach(task -> assertEquals(CalendarTaskSource.SYSTEM_GENERATED, task.getSource()));
    }

    private AiCalendarPlanDraft outOfWindowDraft() {
        List<String> dates = List.of("2026-06-22", "2026-06-23", "2026-06-24", "2026-06-25");
        List<AiCalendarTaskSuggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            suggestions.add(new AiCalendarTaskSuggestion(
                    i,
                    "Study session " + (i + 1),
                    "Focus block",
                    LocalDate.parse(dates.get(i)),
                    LocalTime.parse("23:00:00"),
                    30,
                    CalendarTaskCategory.DEEP_STUDY,
                    CalendarTaskPriority.MEDIUM,
                    "Outside allowed window"
            ));
        }
        return new AiCalendarPlanDraft(List.of(), suggestions);
    }

    @SuppressWarnings("unchecked")
    private List<CalendarTask> runGenerateWithSlots(List<String> slots) {
        prepareGenerateMocks(slots);
        when(scheduleRunRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarTaskRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> new ArrayList<>((List<CalendarTask>) invocation.getArgument(0)));

        calendarService.generate("user-1", workspace.getWorkspaceId(), generateRequest(slots.size()));

        ArgumentCaptor<List<CalendarTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarTaskRepository).saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    private void prepareGenerateMocks(List<String> slots) {
        whenOwnedWorkspace(workspace.getWorkspaceId());
        Roadmap roadmap = new Roadmap();
        roadmap.setRoadmapId(UUID.randomUUID());
        roadmap.setWorkspace(workspace);
        when(roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspace.getWorkspaceId()))
                .thenReturn(Optional.of(roadmap));
        when(roadmapStepRepository.findByRoadmapRoadmapIdOrderBySequenceNoAsc(roadmap.getRoadmapId()))
                .thenReturn(buildSteps(roadmap, 4));
        when(calendarTaskRepository.findByRoadmapRoadmapIdAndStatusNot(any(), any()))
                .thenReturn(List.of());
        when(onboardingProfileRepository.findByWorkspaceWorkspaceId(workspace.getWorkspaceId()))
                .thenReturn(Optional.of(onboardingProfile(slots)));
    }

    private List<RoadmapStep> buildSteps(Roadmap roadmap, int count) {
        List<RoadmapStep> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RoadmapStep step = new RoadmapStep();
            step.setStepId(UUID.randomUUID());
            step.setRoadmap(roadmap);
            step.setWorkspace(workspace);
            step.setTitle("Concept " + (i + 1));
            step.setSummary("Summary " + (i + 1));
            step.setDifficulty(DifficultyLevel.EASY);
            step.setEstimatedMinutes(60);
            step.setSequenceNo(i + 1);
            steps.add(step);
        }
        return steps;
    }

    private OnboardingProfile onboardingProfile(List<String> slots) {
        OnboardingProfile profile = new OnboardingProfile();
        profile.setWorkspace(workspace);
        profile.setTargetGoal("Learn Java");
        profile.setStudyHoursPerWeek(BigDecimal.valueOf(8));
        profile.setPreferredDays(toJson(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")));
        profile.setPreferredTimeSlots(toJson(slots));
        return profile;
    }

    private String toJson(List<String> values) {
        try {
            return new ObjectMapper().writeValueAsString(values);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private GenerateCalendarRequest generateRequest(int sessionsPerDay) {
        GenerateCalendarRequest request = new GenerateCalendarRequest();
        request.setStartDate(LocalDate.parse("2026-06-22")); // Monday
        request.setSessionsPerDay(Math.max(1, sessionsPerDay));
        request.setIncludeReviewSessions(false);
        return request;
    }

    private void assertAllTasksWithinWindows(List<CalendarTask> tasks, List<String> slots) {
        for (CalendarTask task : tasks) {
            boolean within = slots.stream().anyMatch(slot -> {
                String[] parts = slot.split("-");
                LocalTime start = LocalTime.parse(parts[0]);
                LocalTime end = LocalTime.parse(parts[1]);
                return !task.getStartTime().isBefore(start) && !task.getEndTime().isAfter(end);
            });
            assertTrue(
                    within,
                    "Task scheduled outside selected windows: "
                            + task.getStartTime() + "-" + task.getEndTime() + " not in " + slots
            );
        }
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
