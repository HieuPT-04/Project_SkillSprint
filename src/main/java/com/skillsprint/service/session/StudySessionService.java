package com.skillsprint.service.session;

import com.skillsprint.dto.request.session.FinishStudySessionRequest;
import com.skillsprint.dto.request.session.StartStudySessionRequest;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.roadmap.RoadmapResourceResponse;
import com.skillsprint.dto.response.session.StudySessionDetailResponse;
import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.PomodoroSession;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.RoadmapStepResource;
import com.skillsprint.entity.StudySession;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.session.PomodoroPhase;
import com.skillsprint.enums.session.PomodoroSessionStatus;
import com.skillsprint.enums.session.StudySessionStatus;
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
import java.util.EnumSet;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StudySessionService {

    private static final int TITLE_LENGTH = 120;
    private static final int SUMMARY_LENGTH = 700;
    private static final int LIST_ITEM_LENGTH = 120;
    private static final int MAX_LIST_ITEMS = 6;
    private static final int DEFAULT_FOCUS_MINUTES = 25;
    private static final int DEFAULT_SHORT_BREAK_MINUTES = 5;
    private static final int DEFAULT_LONG_BREAK_MINUTES = 15;
    private static final int DEFAULT_TOTAL_CYCLES = 4;
    static final int MIN_VALID_STUDY_MINUTES = 15;
    private static final int FALLBACK_REQUIRED_STUDY_MINUTES = 5;
    // Grace window (seconds) absorbing timer/network jitter when confirming that a
    // running phase has naturally expired. Small enough that it can't be gamed.
    private static final long PHASE_EXPIRY_GRACE_SECONDS = 2;
    private static final EnumSet<PomodoroSessionStatus> ACTIVE_POMODORO_STATUSES =
            EnumSet.of(PomodoroSessionStatus.IN_PROGRESS, PomodoroSessionStatus.PAUSED);

    CalendarTaskRepository calendarTaskRepository;
    PomodoroSessionRepository pomodoroSessionRepository;
    RoadmapStepResourceRepository roadmapStepResourceRepository;
    StudySessionRepository studySessionRepository;
    CalendarMapper calendarMapper;
    RoadmapMapper roadmapMapper;
    StudySessionMapper studySessionMapper;
    CalendarService calendarService;
    QuotaService quotaService;
    SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public StudySessionDetailResponse getStudySessionDetail(String userId, UUID taskId) {
        CalendarTask task = findOwnedTask(userId, taskId);
        quotaService.validateCanAccessRoadmapStep(userId, task.getRoadmapStep());
        StudySession currentSession = studySessionRepository
                .findFirstByCalendarTaskTaskIdAndUserUserIdAndStatus(
                        taskId,
                        userId,
                        StudySessionStatus.IN_PROGRESS
                )
                .orElse(null);

        return buildStudySessionDetail(task, currentSession);
    }

    @Transactional(readOnly = true)
    public StudySessionDetailResponse getSessionDetail(String userId, UUID sessionId) {
        StudySession session = findOwnedSession(userId, sessionId);
        CalendarTask task = session.getCalendarTask();
        if (task == null) {
            throw new AppException(ErrorCode.CALENDAR_TASK_NOT_FOUND);
        }
        quotaService.validateCanAccessRoadmapStep(userId, task.getRoadmapStep());

        return buildStudySessionDetail(task, session);
    }

    private StudySessionDetailResponse buildStudySessionDetail(CalendarTask task, StudySession session) {
        RoadmapStep step = task.getRoadmapStep();
        if (step == null) {
            throw new AppException(ErrorCode.ROADMAP_NOT_FOUND);
        }

        List<RoadmapStepResource> resources = roadmapStepResourceRepository
                .findByStepStepIdOrderBySequenceNoAsc(step.getStepId());
        return StudySessionDetailResponse.builder()
                .session(toSessionResponse(session))
                .task(calendarMapper.toTaskResponse(task))
                .roadmapStep(toRoadmapStepStudyResponse(step))
                .practice(buildPracticePrompt(step))
                .resources(resources.stream().map(roadmapMapper::toResourceResponse).toList())
                .actions(buildActions(task, session))
                .build();
    }

    @Transactional
    public StudySessionResponse startSession(String userId, UUID taskId) {
        return startSession(userId, taskId, null);
    }

    @Transactional
    public StudySessionResponse finishSession(
            String userId,
            UUID sessionId,
            FinishStudySessionRequest request
    ) {
        StudySession session = studySessionRepository.findById(sessionId)
                .filter(studySession -> studySession.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.STUDY_SESSION_NOT_FOUND));

        if (session.getStatus() != StudySessionStatus.COMPLETED) {
            Instant endedAt = Instant.now();
            session.setEndedAt(endedAt);

            PomodoroSession activePomodoro = findActivePomodoro(session);
            int duration = calculateStudyDurationMinutes(session, activePomodoro, endedAt);
            session.setDurationMinutes(duration);
            session.setStatus(StudySessionStatus.COMPLETED);
            session.setNotes(request == null ? null : request.getNotes());
            session.setFocusScore(request == null ? null : request.getFocusScore());
            studySessionRepository.save(session);

            completeActivePomodoro(activePomodoro, endedAt);

            if (shouldCompleteCalendarTask(session)) {
                calendarService.completeTask(userId, session.getCalendarTask().getTaskId());
            }
        }

        PomodoroSession pomodoro = findLatestPomodoro(session);
        return studySessionMapper.toResponse(
                session,
                pomodoro,
                calculateRemainingSeconds(pomodoro),
                isCalendarTaskCompleted(session),
                calculateMinimumRequiredMinutes(session.getCalendarTask())
        );
    }

    private StudySessionDetailResponse.RoadmapStepStudyResponse toRoadmapStepStudyResponse(RoadmapStep step) {
        return StudySessionDetailResponse.RoadmapStepStudyResponse.builder()
                .stepId(step.getStepId())
                .chapterId(step.getChapter() == null ? null : step.getChapter().getChapterId())
                .topicId(step.getTopic() == null ? null : step.getTopic().getTopicId())
                .title(truncate(step.getTitle(), TITLE_LENGTH))
                .subtitle(truncate(step.getSubtitle(), TITLE_LENGTH))
                .summary(truncate(step.getSummary(), SUMMARY_LENGTH))
                .whatToLearn(compactList(step.getWhatToLearn()))
                .keyConcepts(compactList(step.getKeyConcepts()))
                .learningOutcomes(compactList(step.getLearningOutcomes()))
                .recommendedFocus(compactList(step.getRecommendedFocus()))
                .difficulty(step.getDifficulty())
                .estimatedMinutes(step.getEstimatedMinutes())
                .sequenceNo(step.getSequenceNo())
                .status(step.getStatus())
                .build();
    }

    private StudySessionDetailResponse.PracticePromptResponse buildPracticePrompt(RoadmapStep step) {
        String mainConcept = step.getKeyConcepts() == null || step.getKeyConcepts().isEmpty()
                ? step.getTitle()
                : step.getKeyConcepts().get(0);
        return StudySessionDetailResponse.PracticePromptResponse.builder()
                .prompt("Hãy học phần \"" + step.getTitle() + "\" và tự giải thích lại: " + mainConcept + ".")
                .expectedOutput("Bạn nắm được ý chính và có thể trình bày lại bằng ví dụ ngắn.")
                .build();
    }

    private StudySessionResponse toSessionResponse(StudySession session) {
        if (session == null) {
            return null;
        }
        // Fall back to the latest completed/interrupted Pomodoro so an open
        // StudySession whose timer has finished still reports its final Pomodoro
        // (exhausted state) instead of a null timer.
        PomodoroSession pomodoro = findLatestPomodoro(session);
        return studySessionMapper.toResponse(session, pomodoro, calculateRemainingSeconds(pomodoro));
    }

    private StudySessionDetailResponse.StudySessionActionsResponse buildActions(CalendarTask task, StudySession session) {
        boolean completed = task.getStatus() == CalendarTaskStatus.COMPLETED;
        boolean inProgress = session != null && session.getStatus() == StudySessionStatus.IN_PROGRESS;
        String taskId = task.getTaskId().toString();
        String sessionId = session == null ? null : session.getSessionId().toString();
        return StudySessionDetailResponse.StudySessionActionsResponse.builder()
                .canStart(!completed && !inProgress)
                .canFinish(!completed && inProgress)
                .canCompleteTask(!completed)
                .startEndpoint("/api/calendar/tasks/" + taskId + "/sessions/start")
                .finishEndpoint(sessionId == null ? null : "/api/study-sessions/" + sessionId + "/finish")
                .pausePomodoroEndpoint(sessionId == null ? null : "/api/study-sessions/" + sessionId + "/pomodoro/pause")
                .resumePomodoroEndpoint(sessionId == null ? null : "/api/study-sessions/" + sessionId + "/pomodoro/resume")
                .nextPomodoroPhaseEndpoint(sessionId == null ? null : "/api/study-sessions/" + sessionId + "/pomodoro/next-phase")
                .skipPomodoroPhaseEndpoint(sessionId == null ? null : "/api/study-sessions/" + sessionId + "/pomodoro/skip")
                .finishPomodoroEndpoint(sessionId == null ? null : "/api/study-sessions/" + sessionId + "/pomodoro/finish")
                .finishEndpointTemplate("/api/study-sessions/{sessionId}/finish")
                .pausePomodoroEndpointTemplate("/api/study-sessions/{sessionId}/pomodoro/pause")
                .resumePomodoroEndpointTemplate("/api/study-sessions/{sessionId}/pomodoro/resume")
                .nextPomodoroPhaseEndpointTemplate("/api/study-sessions/{sessionId}/pomodoro/next-phase")
                .skipPomodoroPhaseEndpointTemplate("/api/study-sessions/{sessionId}/pomodoro/skip")
                .completeTaskEndpoint("/api/calendar/tasks/" + taskId + "/complete")
                .build();
    }

    @Transactional
    public StudySessionResponse startSession(String userId, UUID taskId, StartStudySessionRequest request) {
        CalendarTask task = findOwnedTask(userId, taskId);
        quotaService.validateCanAccessRoadmapStep(userId, task.getRoadmapStep());
        if (task.getStatus() == CalendarTaskStatus.COMPLETED) {
            throw new AppException(ErrorCode.STUDY_SESSION_TASK_ALREADY_COMPLETED);
        }

        StudySession session = studySessionRepository
                .findFirstByCalendarTaskTaskIdAndUserUserIdAndStatus(
                        taskId,
                        userId,
                        StudySessionStatus.IN_PROGRESS
                )
                .orElseGet(() -> createSession(task));
        StudySession savedSession = studySessionRepository.save(session);

        PomodoroSession pomodoro = findActivePomodoro(savedSession);
        if (shouldUsePomodoro(request) && pomodoro == null) {
            pomodoro = pomodoroSessionRepository.save(createPomodoroSession(savedSession, request));
        }

        return studySessionMapper.toResponse(savedSession, pomodoro, calculateRemainingSeconds(pomodoro));
    }

    @Transactional(readOnly = true)
    public StudySessionResponse getSession(String userId, UUID sessionId) {
        StudySession session = findOwnedSession(userId, sessionId);
        // An IN_PROGRESS StudySession whose latest Pomodoro is COMPLETED/INTERRUPTED
        // must still surface that Pomodoro (not null) so the client can render the
        // exhausted "start a new cycle" state after a refresh.
        PomodoroSession pomodoro = findLatestPomodoro(session);
        return studySessionMapper.toResponse(session, pomodoro, calculateRemainingSeconds(pomodoro));
    }

    @Transactional
    public StudySessionResponse pausePomodoro(String userId, UUID sessionId) {
        StudySession session = findOwnedSession(userId, sessionId);
        PomodoroSession pomodoro = findRequiredActivePomodoro(session);
        if (pomodoro.getStatus() != PomodoroSessionStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.POMODORO_SESSION_NOT_RUNNING);
        }

        pomodoro.setStatus(PomodoroSessionStatus.PAUSED);
        pomodoro.setPausedAt(Instant.now());
        pomodoro.setRemainingSecondsWhenPaused(calculateRemainingSeconds(pomodoro));
        PomodoroSession savedPomodoro = pomodoroSessionRepository.save(pomodoro);

        return studySessionMapper.toResponse(session, savedPomodoro, calculateRemainingSeconds(savedPomodoro));
    }

    @Transactional
    public StudySessionResponse resumePomodoro(String userId, UUID sessionId) {
        StudySession session = findOwnedSession(userId, sessionId);
        PomodoroSession pomodoro = findRequiredActivePomodoro(session);
        if (pomodoro.getStatus() != PomodoroSessionStatus.PAUSED) {
            throw new AppException(ErrorCode.POMODORO_SESSION_NOT_PAUSED);
        }

        Instant now = Instant.now();
        int remainingSeconds = pomodoro.getRemainingSecondsWhenPaused() == null
                ? phaseDurationMinutes(pomodoro) * 60
                : Math.max(0, pomodoro.getRemainingSecondsWhenPaused());
        pomodoro.setStatus(PomodoroSessionStatus.IN_PROGRESS);
        pomodoro.setPausedAt(null);
        pomodoro.setRemainingSecondsWhenPaused(null);
        pomodoro.setPhaseStartedAt(now);
        pomodoro.setPhaseEndAt(now.plusSeconds(remainingSeconds));
        PomodoroSession savedPomodoro = pomodoroSessionRepository.save(pomodoro);

        return studySessionMapper.toResponse(session, savedPomodoro, calculateRemainingSeconds(savedPomodoro));
    }

    /**
     * Natural phase transition: the timer for the current phase has genuinely
     * elapsed. A completed FOCUS phase credits the full configured focus duration
     * exactly once. This is the endpoint the client calls when a phase runs out on
     * its own — never for a manual skip (use {@link #skipPomodoroPhase}).
     */
    @Transactional
    public StudySessionResponse nextPomodoroPhase(String userId, UUID sessionId) {
        StudySession session = findOwnedSession(userId, sessionId);
        PomodoroSession pomodoro = findRequiredActivePomodoro(session);
        if (pomodoro.getStatus() == PomodoroSessionStatus.COMPLETED) {
            throw new AppException(ErrorCode.POMODORO_SESSION_ALREADY_COMPLETED);
        }
        // Natural expiry is server-authoritative: reject an early call so a client
        // cannot claim a full focus credit before the phase clock actually runs out.
        // A learner who wants to end a phase early must use the manual skip endpoint.
        if (!isPhaseExpired(pomodoro)) {
            throw new AppException(ErrorCode.POMODORO_PHASE_NOT_EXPIRED);
        }
        int focusCreditMinutes = pomodoro.getCurrentPhase() == PomodoroPhase.FOCUS
                ? pomodoro.getFocusMinutes()
                : 0;
        return advancePomodoroPhase(session, pomodoro, focusCreditMinutes);
    }

    /**
     * Whether the current phase has genuinely run out according to the server
     * clock. Only a running phase can naturally expire; a small grace absorbs
     * timer/network jitter without letting an early call earn a full cycle.
     */
    private boolean isPhaseExpired(PomodoroSession pomodoro) {
        if (pomodoro.getStatus() != PomodoroSessionStatus.IN_PROGRESS) {
            return false;
        }
        Instant phaseEndAt = pomodoro.getPhaseEndAt();
        return phaseEndAt != null
                && !Instant.now().isBefore(phaseEndAt.minusSeconds(PHASE_EXPIRY_GRACE_SECONDS));
    }

    /**
     * Manual skip ("Bỏ qua"): the learner voluntarily ends the current phase early.
     * A skipped FOCUS phase must NOT credit the full focus duration — only the
     * whole minutes actually spent, measured server-side against the phase clock,
     * so an instant skip credits ~0 and cannot satisfy study-time gates. Client
     * elapsed values are never trusted.
     */
    @Transactional
    public StudySessionResponse skipPomodoroPhase(String userId, UUID sessionId) {
        StudySession session = findOwnedSession(userId, sessionId);
        PomodoroSession pomodoro = findRequiredActivePomodoro(session);
        if (pomodoro.getStatus() == PomodoroSessionStatus.COMPLETED) {
            throw new AppException(ErrorCode.POMODORO_SESSION_ALREADY_COMPLETED);
        }
        int focusCreditMinutes = pomodoro.getCurrentPhase() == PomodoroPhase.FOCUS
                ? elapsedFocusMinutes(pomodoro)
                : 0;
        return advancePomodoroPhase(session, pomodoro, focusCreditMinutes);
    }

    /**
     * Shared phase-advance mechanics for both natural completion and manual skip.
     * The only difference between the two is {@code focusCreditMinutes} — the
     * amount added to {@code completedFocusMinutes} when leaving a FOCUS phase.
     * Reaching the final cycle completes the Pomodoro but deliberately leaves the
     * parent StudySession IN_PROGRESS: the timer is done, the learning record is not.
     */
    private StudySessionResponse advancePomodoroPhase(
            StudySession session,
            PomodoroSession pomodoro,
            int focusCreditMinutes
    ) {
        Instant now = Instant.now();
        if (pomodoro.getCurrentPhase() == PomodoroPhase.FOCUS) {
            pomodoro.setCompletedFocusMinutes(safeInt(pomodoro.getCompletedFocusMinutes()) + focusCreditMinutes);
            if (pomodoro.getCurrentCycle() >= pomodoro.getTotalCycles()) {
                pomodoro.setStatus(PomodoroSessionStatus.COMPLETED);
                pomodoro.setEndedAt(now);
                pomodoro.setPhaseEndAt(now);
                pomodoro.setRemainingSecondsWhenPaused(0);
                PomodoroSession savedPomodoro = pomodoroSessionRepository.save(pomodoro);
                return studySessionMapper.toResponse(session, savedPomodoro, 0);
            }
            pomodoro.setCurrentPhase(pomodoro.getCurrentCycle() % 4 == 0
                    ? PomodoroPhase.LONG_BREAK
                    : PomodoroPhase.SHORT_BREAK);
        } else {
            pomodoro.setCurrentCycle(pomodoro.getCurrentCycle() + 1);
            pomodoro.setCurrentPhase(PomodoroPhase.FOCUS);
        }

        pomodoro.setStatus(PomodoroSessionStatus.IN_PROGRESS);
        pomodoro.setPausedAt(null);
        pomodoro.setRemainingSecondsWhenPaused(null);
        pomodoro.setPhaseStartedAt(now);
        pomodoro.setPhaseEndAt(now.plusSeconds((long) phaseDurationMinutes(pomodoro) * 60));
        PomodoroSession savedPomodoro = pomodoroSessionRepository.save(pomodoro);

        return studySessionMapper.toResponse(session, savedPomodoro, calculateRemainingSeconds(savedPomodoro));
    }

    /**
     * Whole minutes actually spent in the current FOCUS phase, derived from the
     * authoritative server-side phase clock (full duration minus remaining, which
     * already accounts for pauses). Capped at the configured focus length and
     * floored at zero so a manual skip can never over-credit.
     */
    private int elapsedFocusMinutes(PomodoroSession pomodoro) {
        int focusSeconds = safeInt(pomodoro.getFocusMinutes()) * 60;
        int remainingSeconds = calculateRemainingSeconds(pomodoro);
        int elapsedSeconds = Math.max(0, focusSeconds - remainingSeconds);
        return Math.min(safeInt(pomodoro.getFocusMinutes()), elapsedSeconds / 60);
    }

    @Transactional
    public StudySessionResponse finishPomodoro(String userId, UUID sessionId) {
        StudySession session = findOwnedSession(userId, sessionId);
        PomodoroSession pomodoro = findRequiredActivePomodoro(session);
        pomodoro.setStatus(PomodoroSessionStatus.COMPLETED);
        pomodoro.setEndedAt(Instant.now());
        pomodoro.setRemainingSecondsWhenPaused(0);
        PomodoroSession savedPomodoro = pomodoroSessionRepository.save(pomodoro);

        return studySessionMapper.toResponse(session, savedPomodoro, 0);
    }

    private List<String> compactList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(MAX_LIST_ITEMS)
                .map(value -> truncate(value, LIST_ITEM_LENGTH))
                .toList();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }

    private CalendarTask findOwnedTask(String userId, UUID taskId) {
        return calendarTaskRepository.findById(taskId)
                .filter(task -> task.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_TASK_NOT_FOUND));
    }

    private StudySession findOwnedSession(String userId, UUID sessionId) {
        return studySessionRepository.findById(sessionId)
                .filter(studySession -> studySession.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.STUDY_SESSION_NOT_FOUND));
    }

    private StudySession createSession(CalendarTask task) {
        StudySession session = new StudySession();
        session.setWorkspace(task.getWorkspace());
        session.setCalendarTask(task);
        session.setRoadmapStep(task.getRoadmapStep());
        session.setUser(task.getUser());
        session.setStatus(StudySessionStatus.IN_PROGRESS);
        return session;
    }

    private PomodoroSession createPomodoroSession(StudySession session, StartStudySessionRequest request) {
        Instant now = Instant.now();
        PomodoroSession pomodoro = new PomodoroSession();
        pomodoro.setStudySession(session);
        pomodoro.setCalendarTask(session.getCalendarTask());
        pomodoro.setRoadmapStep(session.getRoadmapStep());
        pomodoro.setUser(session.getUser());
        pomodoro.setFocusMinutes(resolveOrDefault(request.getFocusMinutes(), DEFAULT_FOCUS_MINUTES));
        pomodoro.setShortBreakMinutes(resolveOrDefault(request.getShortBreakMinutes(), DEFAULT_SHORT_BREAK_MINUTES));
        pomodoro.setLongBreakMinutes(resolveOrDefault(request.getLongBreakMinutes(), DEFAULT_LONG_BREAK_MINUTES));
        pomodoro.setTotalCycles(resolveOrDefault(request.getTotalCycles(), DEFAULT_TOTAL_CYCLES));
        pomodoro.setCurrentCycle(1);
        pomodoro.setCurrentPhase(PomodoroPhase.FOCUS);
        pomodoro.setStatus(PomodoroSessionStatus.IN_PROGRESS);
        pomodoro.setCompletedFocusMinutes(0);
        pomodoro.setPhaseStartedAt(now);
        pomodoro.setPhaseEndAt(now.plusSeconds((long) pomodoro.getFocusMinutes() * 60));
        return pomodoro;
    }

    private PomodoroSession findActivePomodoro(StudySession session) {
        if (session == null || session.getSessionId() == null) {
            return null;
        }
        return pomodoroSessionRepository
                .findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                        session.getSessionId(),
                        ACTIVE_POMODORO_STATUSES
                )
                .orElse(null);
    }

    private PomodoroSession findRequiredActivePomodoro(StudySession session) {
        PomodoroSession pomodoro = findActivePomodoro(session);
        if (pomodoro == null) {
            throw new AppException(ErrorCode.POMODORO_SESSION_NOT_FOUND);
        }
        return pomodoro;
    }

    private PomodoroSession findLatestPomodoro(StudySession session) {
        if (session == null || session.getSessionId() == null) {
            return null;
        }
        PomodoroSession activePomodoro = findActivePomodoro(session);
        if (activePomodoro != null) {
            return activePomodoro;
        }
        List<PomodoroSession> sessions = pomodoroSessionRepository.findByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.COMPLETED, PomodoroSessionStatus.INTERRUPTED)
        );
        if (sessions.isEmpty()) {
            return null;
        }
        return sessions.get(0);
    }

    private void completeActivePomodoro(PomodoroSession pomodoro, Instant endedAt) {
        if (pomodoro == null) {
            return;
        }
        pomodoro.setStatus(PomodoroSessionStatus.COMPLETED);
        pomodoro.setEndedAt(endedAt);
        pomodoro.setPhaseEndAt(endedAt);
        pomodoro.setRemainingSecondsWhenPaused(0);
        pomodoroSessionRepository.save(pomodoro);
    }

    private boolean shouldUsePomodoro(StartStudySessionRequest request) {
        return request != null && request.isUsePomodoro();
    }

    private int resolveOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int calculateRemainingSeconds(PomodoroSession pomodoro) {
        if (pomodoro == null) {
            return 0;
        }
        if (pomodoro.getStatus() == PomodoroSessionStatus.PAUSED) {
            return Math.max(0, safeInt(pomodoro.getRemainingSecondsWhenPaused()));
        }
        if (pomodoro.getPhaseEndAt() == null || pomodoro.getStatus() == PomodoroSessionStatus.COMPLETED) {
            return 0;
        }
        return Math.max(0, (int) Duration.between(Instant.now(), pomodoro.getPhaseEndAt()).getSeconds());
    }

    private int phaseDurationMinutes(PomodoroSession pomodoro) {
        if (pomodoro.getCurrentPhase() == PomodoroPhase.SHORT_BREAK) {
            return pomodoro.getShortBreakMinutes();
        }
        if (pomodoro.getCurrentPhase() == PomodoroPhase.LONG_BREAK) {
            return pomodoro.getLongBreakMinutes();
        }
        return pomodoro.getFocusMinutes();
    }

    private int calculateDurationMinutes(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) {
            return 0;
        }
        return Math.max(1, (int) Duration.between(startedAt, endedAt).toMinutes());
    }

    private int calculateStudyDurationMinutes(StudySession session, PomodoroSession activePomodoro, Instant endedAt) {
        PomodoroSession pomodoro = activePomodoro;
        if (pomodoro == null) {
            pomodoro = findLatestFinishedPomodoro(session);
        }
        if (pomodoro != null) {
            return validStudyMinutes(safeInt(pomodoro.getCompletedFocusMinutes()));
        }
        return validStudyMinutes(calculateDurationMinutes(session.getStartedAt(), endedAt));
    }

    private PomodoroSession findLatestFinishedPomodoro(StudySession session) {
        if (session == null || session.getSessionId() == null) {
            return null;
        }
        List<PomodoroSession> sessions = pomodoroSessionRepository.findByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
                session.getSessionId(),
                EnumSet.of(PomodoroSessionStatus.COMPLETED, PomodoroSessionStatus.INTERRUPTED)
        );
        return sessions.stream()
                .filter(pomodoro -> pomodoro.getStatus() == PomodoroSessionStatus.COMPLETED)
                .findFirst()
                .orElse(null);
    }

    private boolean shouldCompleteCalendarTask(StudySession session) {
        CalendarTask task = session.getCalendarTask();
        if (task == null || task.getTaskId() == null) {
            return false;
        }
        Long studiedMinutes = studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                session.getUser().getUserId(),
                task.getTaskId(),
                StudySessionStatus.COMPLETED,
                MIN_VALID_STUDY_MINUTES
        );
        return safeLong(studiedMinutes) >= calculateMinimumRequiredMinutes(task);
    }

    private int validStudyMinutes(int minutes) {
        return minutes >= MIN_VALID_STUDY_MINUTES ? minutes : 0;
    }

    private boolean isCalendarTaskCompleted(StudySession session) {
        CalendarTask task = session.getCalendarTask();
        return task != null && task.getStatus() == CalendarTaskStatus.COMPLETED;
    }

    private int calculateMinimumRequiredMinutes(CalendarTask task) {
        if (task == null) {
            return 0;
        }
        Integer taskDuration = task.getDurationMinutes();
        if (taskDuration == null || taskDuration <= 0) {
            return FALLBACK_REQUIRED_STUDY_MINUTES;
        }
        return taskDuration;
    }
}
