package com.skillsprint.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.request.session.FinishStudySessionRequest;
import com.skillsprint.dto.request.session.StartStudySessionRequest;
import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.PomodoroSession;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.StudySession;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.session.PomodoroPhase;
import com.skillsprint.enums.session.PomodoroSessionStatus;
import com.skillsprint.enums.session.StudySessionStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.CalendarMapper;
import com.skillsprint.mapper.RoadmapMapper;
import com.skillsprint.mapper.StudySessionMapper;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.PomodoroSessionRepository;
import com.skillsprint.repository.RoadmapStepResourceRepository;
import com.skillsprint.repository.StudySessionRepository;
import com.skillsprint.service.calendar.CalendarService;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.SubscriptionService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
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
class StudySessionServiceTest {

    @Mock
    CalendarTaskRepository calendarTaskRepository;

    @Mock
    PomodoroSessionRepository pomodoroSessionRepository;

    @Mock
    RoadmapStepResourceRepository roadmapStepResourceRepository;

    @Mock
    StudySessionRepository studySessionRepository;

    @Mock
    CalendarMapper calendarMapper;

    @Mock
    RoadmapMapper roadmapMapper;

    @Mock
    StudySessionMapper studySessionMapper;

    @Mock
    CalendarService calendarService;

    @Mock
    QuotaService quotaService;

    @Mock
    SubscriptionService subscriptionService;

    StudySessionService studySessionService;
    User user;
    StudyWorkspace workspace;
    RoadmapStep step;
    CalendarTask task;

    @BeforeEach
    void setUp() {
        studySessionService = new StudySessionService(
                calendarTaskRepository,
                pomodoroSessionRepository,
                roadmapStepResourceRepository,
                studySessionRepository,
                calendarMapper,
                roadmapMapper,
                studySessionMapper,
                calendarService,
                quotaService,
                subscriptionService
        );
        user = user("user-1");
        workspace = workspace(user);
        step = roadmapStep(workspace);
        task = task(step, CalendarTaskStatus.TODO);
    }

    @Test
    void startSessionCreatesPomodoroWhenRequested() {
        StudySessionResponse expected = StudySessionResponse.builder()
                .calendarTaskId(task.getTaskId())
                .status(StudySessionStatus.IN_PROGRESS)
                .build();
        when(calendarTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        when(studySessionRepository.findFirstByCalendarTaskTaskIdAndUserUserIdAndStatus(
                task.getTaskId(),
                "user-1",
                StudySessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(invocation -> {
            StudySession session = invocation.getArgument(0);
            session.setSessionId(UUID.randomUUID());
            session.setStartedAt(Instant.now());
            return session;
        });
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                any(UUID.class),
                any()
        )).thenReturn(Optional.empty());
        when(pomodoroSessionRepository.save(any(PomodoroSession.class))).thenAnswer(invocation -> {
            PomodoroSession pomodoro = invocation.getArgument(0);
            pomodoro.setPomodoroId(UUID.randomUUID());
            return pomodoro;
        });
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                any(PomodoroSession.class),
                org.mockito.Mockito.<Integer>any()
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.startSession("user-1", task.getTaskId(), pomodoroRequest());

        assertSame(expected, response);
        verify(quotaService).validateCanAccessRoadmapStep("user-1", step);

        ArgumentCaptor<PomodoroSession> captor = ArgumentCaptor.forClass(PomodoroSession.class);
        verify(pomodoroSessionRepository).save(captor.capture());
        PomodoroSession saved = captor.getValue();
        assertEquals(task, saved.getCalendarTask());
        assertEquals(step, saved.getRoadmapStep());
        assertEquals(user, saved.getUser());
        assertEquals(30, saved.getFocusMinutes());
        assertEquals(10, saved.getShortBreakMinutes());
        assertEquals(20, saved.getLongBreakMinutes());
        assertEquals(3, saved.getTotalCycles());
        assertEquals(PomodoroPhase.FOCUS, saved.getCurrentPhase());
        assertEquals(PomodoroSessionStatus.IN_PROGRESS, saved.getStatus());
    }

    @Test
    void startSessionReusesExistingInProgressSessionAndActivePomodoro() {
        StudySession existingSession = studySession(task);
        PomodoroSession existingPomodoro = pomodoro(existingSession, PomodoroSessionStatus.IN_PROGRESS);
        StudySessionResponse expected = StudySessionResponse.builder().sessionId(existingSession.getSessionId()).build();
        when(calendarTaskRepository.findById(task.getTaskId())).thenReturn(Optional.of(task));
        when(studySessionRepository.findFirstByCalendarTaskTaskIdAndUserUserIdAndStatus(
                task.getTaskId(),
                "user-1",
                StudySessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(existingSession));
        when(studySessionRepository.save(existingSession)).thenReturn(existingSession);
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                existingSession.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.of(existingPomodoro));
        when(studySessionMapper.toResponse(
                existingSession,
                existingPomodoro,
                0
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.startSession("user-1", task.getTaskId(), pomodoroRequest());

        assertSame(expected, response);
        verify(pomodoroSessionRepository, never()).save(any());
    }

    @Test
    void finishSessionCompletesCalendarTaskWhenMinimumDurationReached() {
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(30 * 60L));
        FinishStudySessionRequest request = new FinishStudySessionRequest();
        request.setNotes("Done");
        request.setFocusScore(5);
        StudySessionResponse expected = StudySessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(StudySessionStatus.COMPLETED)
                .build();
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(studySessionRepository.save(session)).thenReturn(session);
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.empty());
        when(pomodoroSessionRepository.findByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.COMPLETED, PomodoroSessionStatus.INTERRUPTED)
        )).thenReturn(List.of());
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq(15)
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), request);

        assertSame(expected, response);
        assertEquals(StudySessionStatus.COMPLETED, session.getStatus());
        assertEquals("Done", session.getNotes());
        assertEquals(5, session.getFocusScore());
        verify(calendarService).completeTask("user-1", task.getTaskId());
    }

    @Test
    void startSessionRejectsCompletedTask() {
        CalendarTask completed = task(step, CalendarTaskStatus.COMPLETED);
        when(calendarTaskRepository.findById(completed.getTaskId())).thenReturn(Optional.of(completed));

        AppException exception = assertThrows(
                AppException.class,
                () -> studySessionService.startSession("user-1", completed.getTaskId())
        );

        assertEquals(ErrorCode.STUDY_SESSION_TASK_ALREADY_COMPLETED, exception.getErrorCode());
        verify(studySessionRepository, never()).save(any());
    }

    @Test
    void getSessionRejectsForeignSessionAsNotFound() {
        StudySession session = studySession(task);
        session.setUser(user("other-user"));
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));

        AppException exception = assertThrows(
                AppException.class,
                () -> studySessionService.getSession("user-1", session.getSessionId())
        );

        assertEquals(ErrorCode.STUDY_SESSION_NOT_FOUND, exception.getErrorCode());
    }

    private StartStudySessionRequest pomodoroRequest() {
        StartStudySessionRequest request = new StartStudySessionRequest();
        request.setUsePomodoro(true);
        request.setFocusMinutes(30);
        request.setShortBreakMinutes(10);
        request.setLongBreakMinutes(20);
        request.setTotalCycles(3);
        return request;
    }

    private StudySession studySession(CalendarTask task) {
        StudySession session = new StudySession();
        session.setSessionId(UUID.randomUUID());
        session.setWorkspace(workspace);
        session.setCalendarTask(task);
        session.setRoadmapStep(task.getRoadmapStep());
        session.setUser(task.getUser());
        session.setStartedAt(Instant.now());
        session.setStatus(StudySessionStatus.IN_PROGRESS);
        return session;
    }

    private PomodoroSession pomodoro(StudySession session, PomodoroSessionStatus status) {
        PomodoroSession pomodoro = new PomodoroSession();
        pomodoro.setPomodoroId(UUID.randomUUID());
        pomodoro.setStudySession(session);
        pomodoro.setCalendarTask(session.getCalendarTask());
        pomodoro.setRoadmapStep(session.getRoadmapStep());
        pomodoro.setUser(session.getUser());
        pomodoro.setStatus(status);
        pomodoro.setPhaseEndAt(Instant.now());
        return pomodoro;
    }

    private CalendarTask task(RoadmapStep step, CalendarTaskStatus status) {
        CalendarTask task = new CalendarTask();
        task.setTaskId(UUID.randomUUID());
        task.setWorkspace(workspace);
        task.setRoadmapStep(step);
        task.setUser(user);
        task.setTitle("Task");
        task.setTaskDate(LocalDate.parse("2026-06-25"));
        task.setDurationMinutes(60);
        task.setCategory(CalendarTaskCategory.DEEP_STUDY);
        task.setPriority(CalendarTaskPriority.MEDIUM);
        task.setStatus(status);
        return task;
    }

    private RoadmapStep roadmapStep(StudyWorkspace workspace) {
        RoadmapStep step = new RoadmapStep();
        step.setStepId(UUID.randomUUID());
        step.setWorkspace(workspace);
        step.setTitle("Step");
        step.setSequenceNo(1);
        return step;
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
