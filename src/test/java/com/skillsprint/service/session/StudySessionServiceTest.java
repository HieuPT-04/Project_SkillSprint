package com.skillsprint.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void finishSessionCompletesCalendarTaskWhenTaskDurationReached() {
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(60 * 60L));
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
        when(studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                "user-1",
                task.getTaskId(),
                StudySessionStatus.COMPLETED,
                15
        )).thenReturn(60L);
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq(60)
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), request);

        assertSame(expected, response);
        assertEquals(StudySessionStatus.COMPLETED, session.getStatus());
        assertEquals("Done", session.getNotes());
        assertEquals(5, session.getFocusScore());
        verify(calendarService).completeTask("user-1", task.getTaskId());
    }

    @Test
    void finishSessionCompletesCalendarTaskWhenAccumulatedDurationReached() {
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(30 * 60L));
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
        when(studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                "user-1",
                task.getTaskId(),
                StudySessionStatus.COMPLETED,
                15
        )).thenReturn(60L);
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq(60)
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(StudySessionStatus.COMPLETED, session.getStatus());
        verify(calendarService).completeTask("user-1", task.getTaskId());
    }

    @Test
    void finishSessionDoesNotCompleteCalendarTaskWhenOnlyPartialDurationStudied() {
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(30 * 60L));
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
        when(studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                "user-1",
                task.getTaskId(),
                StudySessionStatus.COMPLETED,
                15
        )).thenReturn(30L);
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq(60)
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(StudySessionStatus.COMPLETED, session.getStatus());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void finishSessionWithThreeMinutesStoresZeroProgressAndDoesNotCompleteTask() {
        task.setDurationMinutes(96);
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(3 * 60L));
        StudySessionResponse expected = stubFinishSession(session, 0L, 96);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(0, session.getDurationMinutes());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void finishSessionWithTenMinutesStoresZeroProgressAndDoesNotCompleteTask() {
        task.setDurationMinutes(96);
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(10 * 60L));
        StudySessionResponse expected = stubFinishSession(session, 0L, 96);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(0, session.getDurationMinutes());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void finishSessionWithMinimumValidMinutesCountsProgressButDoesNotCompleteLargeTask() {
        task.setDurationMinutes(96);
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(15 * 60L));
        StudySessionResponse expected = stubFinishSession(session, 15L, 96);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(15, session.getDurationMinutes());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void finishSessionWithThirtyMinutesCountsProgressButDoesNotCompleteLargeTask() {
        task.setDurationMinutes(96);
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(30 * 60L));
        StudySessionResponse expected = stubFinishSession(session, 30L, 96);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(30, session.getDurationMinutes());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void finishSessionCompletesLargeTaskWhenAccumulatedValidMinutesEqualTarget() {
        task.setDurationMinutes(96);
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(15 * 60L));
        StudySessionResponse expected = stubFinishSession(session, 96L, 96);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(15, session.getDurationMinutes());
        verify(calendarService).completeTask("user-1", task.getTaskId());
    }

    @Test
    void finishSessionCompletesLargeTaskWhenAccumulatedValidMinutesExceedTarget() {
        task.setDurationMinutes(96);
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(30 * 60L));
        StudySessionResponse expected = stubFinishSession(session, 120L, 96);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(30, session.getDurationMinutes());
        verify(calendarService).completeTask("user-1", task.getTaskId());
    }

    @Test
    void finishSessionUsesCompletedPomodoroFocusMinutesInsteadOfElapsedWallTime() {
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(90 * 60L));
        PomodoroSession pomodoro = pomodoro(session, PomodoroSessionStatus.IN_PROGRESS);
        pomodoro.setCompletedFocusMinutes(30);
        StudySessionResponse expected = StudySessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(StudySessionStatus.COMPLETED)
                .build();
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(studySessionRepository.save(session)).thenReturn(session);
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.of(pomodoro));
        when(pomodoroSessionRepository.save(pomodoro)).thenReturn(pomodoro);
        when(studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                "user-1",
                task.getTaskId(),
                StudySessionStatus.COMPLETED,
                15
        )).thenReturn(30L);
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                any(PomodoroSession.class),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq(60)
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(30, session.getDurationMinutes());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void finishSessionAcceptsAnAlreadyCompletedPomodoro() {
        StudySession session = studySession(task);
        PomodoroSession pomodoro = pomodoro(session, PomodoroSessionStatus.COMPLETED);
        pomodoro.setCompletedFocusMinutes(25);
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
        )).thenReturn(List.of(pomodoro));
        when(studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                "user-1",
                task.getTaskId(),
                StudySessionStatus.COMPLETED,
                15
        )).thenReturn(25L);
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                any(PomodoroSession.class),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq(60)
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(StudySessionStatus.COMPLETED, session.getStatus());
        assertEquals(25, session.getDurationMinutes());
    }

    @Test
    void finishSessionWithIncompletePomodoroFocusStoresZeroProgress() {
        StudySession session = studySession(task);
        session.setStartedAt(Instant.now().minusSeconds(90 * 60L));
        PomodoroSession pomodoro = pomodoro(session, PomodoroSessionStatus.IN_PROGRESS);
        pomodoro.setCompletedFocusMinutes(0);
        StudySessionResponse expected = StudySessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(StudySessionStatus.COMPLETED)
                .build();
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(studySessionRepository.save(session)).thenReturn(session);
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.of(pomodoro));
        when(pomodoroSessionRepository.save(pomodoro)).thenReturn(pomodoro);
        when(studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                "user-1",
                task.getTaskId(),
                StudySessionStatus.COMPLETED,
                15
        )).thenReturn(0L);
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                any(PomodoroSession.class),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq(60)
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        assertEquals(0, session.getDurationMinutes());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void duplicateFinishSessionDoesNotSaveOrCompleteAgain() {
        StudySession session = studySession(task);
        session.setStatus(StudySessionStatus.COMPLETED);
        session.setDurationMinutes(60);
        StudySessionResponse expected = StudySessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(StudySessionStatus.COMPLETED)
                .build();
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.empty());
        when(pomodoroSessionRepository.findByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.COMPLETED, PomodoroSessionStatus.INTERRUPTED)
        )).thenReturn(List.of());
        when(studySessionMapper.toResponse(
                session,
                null,
                0,
                false,
                60
        )).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishSession("user-1", session.getSessionId(), null);

        assertSame(expected, response);
        verify(studySessionRepository, never()).save(any());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void finishPomodoroDoesNotCompleteCalendarTaskOrStudySession() {
        StudySession session = studySession(task);
        PomodoroSession pomodoro = pomodoro(session, PomodoroSessionStatus.IN_PROGRESS);
        StudySessionResponse expected = StudySessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(StudySessionStatus.IN_PROGRESS)
                .build();
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.of(pomodoro));
        when(pomodoroSessionRepository.save(pomodoro)).thenReturn(pomodoro);
        when(studySessionMapper.toResponse(session, pomodoro, 0)).thenReturn(expected);

        StudySessionResponse response = studySessionService.finishPomodoro("user-1", session.getSessionId());

        assertSame(expected, response);
        assertEquals(StudySessionStatus.IN_PROGRESS, session.getStatus());
        assertEquals(PomodoroSessionStatus.COMPLETED, pomodoro.getStatus());
        verify(calendarService, never()).completeTask(any(), any());
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

    @Test
    void skipPomodoroPhaseDuringFocusCreditsElapsedNotFullFocusDuration() {
        StudySession session = studySession(task);
        PomodoroSession pomodoro = runningFocusPomodoro(session, 1, 4, 3 * 60);
        stubActivePomodoro(session, pomodoro);

        studySessionService.skipPomodoroPhase("user-1", session.getSessionId());

        assertEquals(3, pomodoro.getCompletedFocusMinutes());
        assertTrue(
                pomodoro.getCompletedFocusMinutes() < pomodoro.getFocusMinutes(),
                "Manual skip must not credit the full focus duration");
        assertEquals(PomodoroPhase.SHORT_BREAK, pomodoro.getCurrentPhase());
        assertEquals(PomodoroSessionStatus.IN_PROGRESS, pomodoro.getStatus());
        assertEquals(StudySessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void nextPomodoroPhaseAfterPhaseEndCreditsExactlyOneFullFocusCycle() {
        StudySession session = studySession(task);
        // phaseEndAt is 60s in the past → the focus phase has genuinely expired.
        PomodoroSession pomodoro = runningFocusPomodoro(session, 1, 4, 26 * 60);
        stubActivePomodoro(session, pomodoro);

        studySessionService.nextPomodoroPhase("user-1", session.getSessionId());

        assertEquals(25, pomodoro.getCompletedFocusMinutes());
        assertEquals(PomodoroPhase.SHORT_BREAK, pomodoro.getCurrentPhase());
        assertEquals(PomodoroSessionStatus.IN_PROGRESS, pomodoro.getStatus());
        assertEquals(StudySessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void pausePomodoroAfterFocusExpiresCreditsTheCycleAndPausesTheBreak() {
        StudySession session = studySession(task);
        PomodoroSession pomodoro = runningFocusPomodoro(session, 1, 4, 26 * 60);
        stubActivePomodoro(session, pomodoro);

        studySessionService.pausePomodoro("user-1", session.getSessionId());

        assertEquals(25, pomodoro.getCompletedFocusMinutes());
        assertEquals(PomodoroPhase.SHORT_BREAK, pomodoro.getCurrentPhase());
        assertEquals(PomodoroSessionStatus.PAUSED, pomodoro.getStatus());
        assertEquals(5 * 60, pomodoro.getRemainingSecondsWhenPaused());
        assertEquals(StudySessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void repeatedNextPomodoroPhaseDoesNotDoubleAdvanceTheSamePhase() {
        StudySession session = studySession(task);
        // phaseEndAt is 60s in the past → the focus phase has genuinely expired.
        PomodoroSession pomodoro = runningFocusPomodoro(session, 1, 4, 26 * 60);
        stubActivePomodoro(session, pomodoro);

        // First (legitimate) natural-expiry call advances FOCUS → SHORT_BREAK once.
        studySessionService.nextPomodoroPhase("user-1", session.getSessionId());
        assertEquals(25, pomodoro.getCompletedFocusMinutes());
        assertEquals(PomodoroPhase.SHORT_BREAK, pomodoro.getCurrentPhase());

        // A duplicate call (retry / second tab / racing signal) now sees the fresh,
        // not-yet-expired break phase and is rejected — no second transition, no
        // extra focus credit. The server clock is the single source of truth.
        AppException exception = assertThrows(
                AppException.class,
                () -> studySessionService.nextPomodoroPhase("user-1", session.getSessionId())
        );
        assertEquals(ErrorCode.POMODORO_PHASE_NOT_EXPIRED, exception.getErrorCode());
        assertEquals(25, pomodoro.getCompletedFocusMinutes());
        assertEquals(PomodoroPhase.SHORT_BREAK, pomodoro.getCurrentPhase());
        assertEquals(1, pomodoro.getCurrentCycle());
    }

    @Test
    void nextPomodoroPhaseBeforePhaseEndRejectsAndLeavesStateUnchanged() {
        StudySession session = studySession(task);
        // 22 minutes still remaining → an early natural-expiry call must be rejected.
        PomodoroSession pomodoro = runningFocusPomodoro(session, 1, 4, 3 * 60);
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.of(pomodoro));

        AppException exception = assertThrows(
                AppException.class,
                () -> studySessionService.nextPomodoroPhase("user-1", session.getSessionId())
        );

        assertEquals(ErrorCode.POMODORO_PHASE_NOT_EXPIRED, exception.getErrorCode());
        assertEquals(0, pomodoro.getCompletedFocusMinutes());
        assertEquals(PomodoroPhase.FOCUS, pomodoro.getCurrentPhase());
        assertEquals(PomodoroSessionStatus.IN_PROGRESS, pomodoro.getStatus());
        assertEquals(StudySessionStatus.IN_PROGRESS, session.getStatus());
        verify(pomodoroSessionRepository, never()).save(any());
        verify(studySessionRepository, never()).save(any());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void getSessionReturnsLatestCompletedPomodoroForOpenStudySession() {
        StudySession session = studySession(task);
        PomodoroSession completed = pomodoro(session, PomodoroSessionStatus.COMPLETED);
        completed.setCompletedFocusMinutes(25);
        StudySessionResponse expected = StudySessionResponse.builder().sessionId(session.getSessionId()).build();
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.empty());
        when(pomodoroSessionRepository.findByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.COMPLETED, PomodoroSessionStatus.INTERRUPTED)
        )).thenReturn(List.of(completed));
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                any(PomodoroSession.class),
                org.mockito.Mockito.<Integer>any()
        )).thenReturn(expected);

        StudySessionResponse result = studySessionService.getSession("user-1", session.getSessionId());

        assertSame(expected, result);
        ArgumentCaptor<PomodoroSession> captor = ArgumentCaptor.forClass(PomodoroSession.class);
        verify(studySessionMapper).toResponse(any(StudySession.class), captor.capture(), org.mockito.Mockito.<Integer>any());
        assertSame(completed, captor.getValue());
        assertEquals(StudySessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void finalManualSkipCompletesPomodoroButKeepsStudySessionInProgress() {
        StudySession session = studySession(task);
        PomodoroSession pomodoro = runningFocusPomodoro(session, 4, 4, 2 * 60);
        stubActivePomodoro(session, pomodoro);

        studySessionService.skipPomodoroPhase("user-1", session.getSessionId());

        assertEquals(PomodoroSessionStatus.COMPLETED, pomodoro.getStatus());
        assertEquals(StudySessionStatus.IN_PROGRESS, session.getStatus());
        assertTrue(
                pomodoro.getCompletedFocusMinutes() < pomodoro.getFocusMinutes(),
                "Final skipped cycle must not credit the full focus duration");
        verify(studySessionRepository, never()).save(any());
        verify(calendarService, never()).completeTask(any(), any());
    }

    @Test
    void repeatedInstantSkipsDoNotAccumulateValidStudyMinutes() {
        StudySession session = studySession(task);
        PomodoroSession pomodoro = runningFocusPomodoro(session, 1, 8, 0);
        stubActivePomodoro(session, pomodoro);

        // Emulate a learner mashing "Bỏ qua": before every FOCUS skip, reset the
        // phase clock to "just started" so each skip credits ~0 studied minutes.
        for (int i = 0; i < 6; i++) {
            if (pomodoro.getStatus() == PomodoroSessionStatus.COMPLETED) {
                break;
            }
            if (pomodoro.getCurrentPhase() == PomodoroPhase.FOCUS) {
                Instant now = Instant.now();
                pomodoro.setPhaseStartedAt(now);
                pomodoro.setPhaseEndAt(now.plusSeconds(25L * 60));
            }
            studySessionService.skipPomodoroPhase("user-1", session.getSessionId());
        }

        assertTrue(
                pomodoro.getCompletedFocusMinutes() < StudySessionService.MIN_VALID_STUDY_MINUTES,
                "Repeated instant skips must never reach the minimum valid study threshold");
        assertEquals(StudySessionStatus.IN_PROGRESS, session.getStatus());
        verify(calendarService, never()).completeTask(any(), any());
    }

    private PomodoroSession runningFocusPomodoro(
            StudySession session,
            int currentCycle,
            int totalCycles,
            int elapsedSeconds
    ) {
        PomodoroSession pomodoro = pomodoro(session, PomodoroSessionStatus.IN_PROGRESS);
        pomodoro.setFocusMinutes(25);
        pomodoro.setShortBreakMinutes(5);
        pomodoro.setLongBreakMinutes(15);
        pomodoro.setCurrentCycle(currentCycle);
        pomodoro.setTotalCycles(totalCycles);
        pomodoro.setCurrentPhase(PomodoroPhase.FOCUS);
        pomodoro.setCompletedFocusMinutes(0);
        Instant now = Instant.now();
        pomodoro.setPhaseStartedAt(now.minusSeconds(elapsedSeconds));
        pomodoro.setPhaseEndAt(now.plusSeconds(25L * 60 - elapsedSeconds));
        return pomodoro;
    }

    private void stubActivePomodoro(StudySession session, PomodoroSession pomodoro) {
        when(studySessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(pomodoroSessionRepository.findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED)
        )).thenReturn(Optional.of(pomodoro));
        when(pomodoroSessionRepository.save(pomodoro)).thenReturn(pomodoro);
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                any(PomodoroSession.class),
                org.mockito.Mockito.<Integer>any()
        )).thenReturn(StudySessionResponse.builder().sessionId(session.getSessionId()).build());
    }

    private StudySessionResponse stubFinishSession(StudySession session, long accumulatedValidMinutes, int requiredMinutes) {
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
        when(studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                "user-1",
                session.getCalendarTask().getTaskId(),
                StudySessionStatus.COMPLETED,
                15
        )).thenReturn(accumulatedValidMinutes);
        when(studySessionMapper.toResponse(
                any(StudySession.class),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.eq(0),
                org.mockito.Mockito.eq(false),
                org.mockito.Mockito.eq(requiredMinutes)
        )).thenReturn(expected);
        return expected;
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
