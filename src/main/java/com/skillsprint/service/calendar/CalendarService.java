package com.skillsprint.service.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.calendar.CreateCalendarTaskRequest;
import com.skillsprint.dto.request.calendar.GenerateCalendarRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskStatusRequest;
import com.skillsprint.dto.response.calendar.CalendarScheduleRunResponse;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.calendar.EisenhowerBoardResponse;
import com.skillsprint.dto.response.calendar.EisenhowerQuadrantResponse;
import com.skillsprint.entity.CalendarScheduleRun;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.OnboardingProfile;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapProgressLog;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.enums.calendar.CalendarScheduleRunStatus;
import com.skillsprint.enums.calendar.CalendarScheduleScope;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskSource;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.calendar.ClassifiedBy;
import com.skillsprint.enums.calendar.EisenhowerQuadrant;
import com.skillsprint.enums.calendar.WeekDay;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.roadmap.RoadmapProgressActionType;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import com.skillsprint.enums.session.StudySessionStatus;
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
import com.skillsprint.service.calendar.ai.AiCalendarTaskInput;
import com.skillsprint.service.calendar.ai.AiCalendarTaskSuggestion;
import com.skillsprint.service.calendar.ai.GeminiCalendarPlannerClient;
import com.skillsprint.service.points.PointService;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.SubscriptionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CalendarService {

    static TypeReference<List<WeekDay>> WEEK_DAY_LIST_TYPE = new TypeReference<>() {};
    static TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    static int DEFAULT_SESSION_MINUTES = 60;
    static int DEFAULT_SESSIONS_PER_DAY = 1;
    static int MAX_SESSIONS_PER_DAY = 8;
    static int MIN_STEP_POINT_STUDY_MINUTES = 20;
    static int MIN_VALID_STUDY_MINUTES = 15;
    static int FALLBACK_REQUIRED_STUDY_MINUTES = 5;
    static int TITLE_LENGTH = 90;
    static int SAFE_VARCHAR_LENGTH = 250;
    static Pattern DISPLAY_STEP_PREFIX_PATTERN = Pattern.compile(
            "^(?:bước|step|topic)\\s*\\d+(?:[.\\-:/)]\\s*|\\s+)(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    StudyWorkspaceRepository workspaceRepository;
    RoadmapRepository roadmapRepository;
    RoadmapStepRepository roadmapStepRepository;
    RoadmapProgressLogRepository roadmapProgressLogRepository;
    OnboardingProfileRepository onboardingProfileRepository;
    CalendarScheduleRunRepository scheduleRunRepository;
    CalendarTaskRepository calendarTaskRepository;
    StudySessionRepository studySessionRepository;
    CalendarMapper calendarMapper;
    GeminiCalendarPlannerClient geminiCalendarPlannerClient;
    ObjectMapper objectMapper;
    QuotaService quotaService;
    com.skillsprint.service.notification.NotificationService notificationService;
    PointService pointService;
    SubscriptionService subscriptionService;

    @Transactional
    public CalendarScheduleRunResponse generate(
            String userId,
            UUID workspaceId,
            GenerateCalendarRequest request
    ) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        quotaService.validateCanGenerateAi(userId);
        Roadmap roadmap = roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_ROADMAP_REQUIRED));
        List<RoadmapStep> steps = roadmapStepRepository
                .findByRoadmapRoadmapIdOrderBySequenceNoAsc(roadmap.getRoadmapId());

        if (steps.isEmpty()) {
            throw new AppException(ErrorCode.CALENDAR_ROADMAP_REQUIRED);
        }

        ensureCalendarNotGenerated(roadmap.getRoadmapId());

        ScheduleConfig config = resolveScheduleConfig(workspaceId, request);
        List<TaskDraft> drafts = buildTaskDrafts(steps, config.sessionMinutes(), config.includeReviewSessions());
        int sessionsPerDay = resolveSessionsPerDay(config, drafts.size());
        ScheduleConfig finalConfig = config.withSessionsPerDay(sessionsPerDay);
        List<PlannedTask> plannedTasks = buildRuleBasedPlan(drafts, finalConfig);
        plannedTasks = optimizePlanWithAi(plannedTasks, finalConfig);

        CalendarScheduleRun run = createScheduleRun(workspace, roadmap, finalConfig);
        CalendarScheduleRun savedRun = scheduleRunRepository.saveAndFlush(run);
        List<CalendarTask> tasks = calendarTaskRepository.saveAllAndFlush(createTasks(
                workspace,
                roadmap,
                plannedTasks
        ));

        savedRun.setEndDate(tasks.get(tasks.size() - 1).getTaskDate());
        CalendarScheduleRun updatedRun = scheduleRunRepository.saveAndFlush(savedRun);
        notificationService.notifyCalendarReady(workspace.getUser(), workspace);
        return calendarMapper.toScheduleRunResponse(updatedRun, tasks);
    }

    @Transactional(readOnly = true)
    public List<CalendarTaskResponse> getTasks(String userId, UUID workspaceId) {
        findOwnedWorkspace(userId, workspaceId);
        return calendarTaskRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdOrderByTaskDateAscStartTimeAscCreatedAtAsc(workspaceId, userId)
                .stream()
                .map(calendarMapper::toTaskResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EisenhowerBoardResponse getEisenhowerBoard(String userId, UUID workspaceId, LocalDate date) {
        findOwnedWorkspace(userId, workspaceId);
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        List<CalendarTask> tasks = calendarTaskRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdAndTaskDateOrderByStartTimeAscCreatedAtAsc(
                        workspaceId,
                        userId,
                        targetDate
                );

        return buildEisenhowerBoard(workspaceId, targetDate, tasks);
    }

    @Transactional(readOnly = true)
    public EisenhowerBoardResponse getEisenhowerTasksForWorkspace(String userId, UUID workspaceId) {
        findOwnedWorkspace(userId, workspaceId);
        List<CalendarTask> tasks = calendarTaskRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdOrderByTaskDateAscStartTimeAscCreatedAtAsc(
                        workspaceId,
                        userId
                );
        return buildEisenhowerBoard(workspaceId, LocalDate.now(), tasks);
    }

    @Transactional
    public CalendarTaskResponse updateTask(
            String userId,
            UUID taskId,
            UpdateCalendarTaskRequest request
    ) {
        CalendarTask task = findOwnedTask(userId, taskId);
        ensureTaskCanBeRescheduled(task);

        if (request.getTaskDate() != null) {
            task.setTaskDate(request.getTaskDate());
        }
        if (request.getStartTime() != null) {
            task.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            task.setEndTime(request.getEndTime());
        }

        validateTimeRange(task.getStartTime(), task.getEndTime());
        task.setDurationMinutes(calculateDurationMinutes(task.getStartTime(), task.getEndTime(), task.getDurationMinutes()));
        ensureNoTimeConflict(task);
        reclassifyAfterReschedule(task);

        return calendarMapper.toTaskResponse(calendarTaskRepository.save(task));
    }

    @Transactional
    public CalendarTaskResponse completeTask(String userId, UUID taskId) {
        CalendarTask task = findOwnedTask(userId, taskId);
        if (task.getRoadmapStep() != null) {
            quotaService.validateCanAccessRoadmapStep(userId, task.getRoadmapStep());
            ensureTaskHasEnoughValidStudyMinutes(task);
        }

        if (task.getStatus() != CalendarTaskStatus.COMPLETED) {
            task.setStatus(CalendarTaskStatus.COMPLETED);
            task.setCompletedAt(Instant.now());
            calendarTaskRepository.save(task);
        }

        if (task.getRoadmapStep() != null) {
            completeRoadmapStepIfReady(task.getRoadmapStep());
        }

        return calendarMapper.toTaskResponse(task);
    }

    @Transactional
    public CalendarTaskResponse createTask(String userId, UUID workspaceId, CreateCalendarTaskRequest request) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);

        CalendarTask task = new CalendarTask();
        task.setWorkspace(workspace);
        task.setUser(workspace.getUser());
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setTaskDate(request.getTaskDate() != null ? request.getTaskDate() : LocalDate.now());
        task.setStartTime(request.getStartTime());
        task.setEndTime(request.getEndTime());
        task.setDurationMinutes(request.getDurationMinutes());
        task.setCategory(CalendarTaskCategory.PERSONAL);
        task.setPriority(CalendarTaskPriority.MEDIUM);
        task.setStatus(CalendarTaskStatus.TODO);
        task.setSource(CalendarTaskSource.USER_CREATED);

        EisenhowerQuadrant quadrant = request.getQuadrant() != null
                ? request.getQuadrant()
                : EisenhowerQuadrant.SCHEDULE;
        task.setEisenhowerQuadrant(quadrant);
        task.setImportant(isImportantQuadrant(quadrant));
        task.setUrgent(isUrgentQuadrant(quadrant));
        task.setImportanceScore(isImportantQuadrant(quadrant) ? BigDecimal.valueOf(0.80) : BigDecimal.valueOf(0.30));
        task.setUrgencyScore(isUrgentQuadrant(quadrant) ? BigDecimal.valueOf(0.80) : BigDecimal.valueOf(0.30));
        task.setClassificationReason("Người dùng tạo thủ công.");
        task.setClassifiedBy(ClassifiedBy.USER);
        task.setClassifiedAt(Instant.now());

        return calendarMapper.toTaskResponse(calendarTaskRepository.save(task));
    }

    @Transactional
    public CalendarTaskResponse updateTaskStatus(
            String userId,
            UUID workspaceId,
            UUID taskId,
            UpdateCalendarTaskStatusRequest request
    ) {
        findOwnedWorkspace(userId, workspaceId);
        CalendarTask task = findOwnedTask(userId, taskId);

        CalendarTaskStatus newStatus = resolveStatus(request.getStatus());
        CalendarTaskStatus oldStatus = task.getStatus();
        if (oldStatus != CalendarTaskStatus.COMPLETED && newStatus == CalendarTaskStatus.COMPLETED) {
            ensureTaskHasEnoughValidStudyMinutes(task);
        }
        task.setStatus(newStatus);
        task.setCompletedAt(newStatus == CalendarTaskStatus.COMPLETED ? Instant.now() : null);
        CalendarTask savedTask = calendarTaskRepository.save(task);

        if (oldStatus != CalendarTaskStatus.COMPLETED && newStatus == CalendarTaskStatus.COMPLETED) {
            if (savedTask.getRoadmapStep() != null) {
                completeRoadmapStepIfReady(savedTask.getRoadmapStep());
            }
        }

        return calendarMapper.toTaskResponse(savedTask);
    }

    private StudyWorkspace findOwnedWorkspace(String userId, UUID workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private CalendarTask findOwnedTask(String userId, UUID taskId) {
        return calendarTaskRepository.findById(taskId)
                .filter(calendarTask -> calendarTask.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_TASK_NOT_FOUND));
    }

    private void ensureTaskCanBeRescheduled(CalendarTask task) {
        if (task.getStatus() == CalendarTaskStatus.COMPLETED) {
            throw new AppException(ErrorCode.CALENDAR_TASK_ALREADY_COMPLETED);
        }
    }

    private void ensureNoTimeConflict(CalendarTask task) {
        if (task.getTaskDate() == null || task.getStartTime() == null || task.getEndTime() == null) {
            return;
        }

        boolean hasConflict = calendarTaskRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdAndTaskDateOrderByStartTimeAscCreatedAtAsc(
                        task.getWorkspace().getWorkspaceId(),
                        task.getUser().getUserId(),
                        task.getTaskDate()
                )
                .stream()
                .filter(existingTask -> !existingTask.getTaskId().equals(task.getTaskId()))
                .filter(existingTask -> existingTask.getStatus() != CalendarTaskStatus.CANCELLED)
                .anyMatch(existingTask -> hasTimeOverlap(task, existingTask));

        if (hasConflict) {
            throw new AppException(ErrorCode.CALENDAR_TASK_TIME_CONFLICT);
        }
    }

    private boolean hasTimeOverlap(CalendarTask currentTask, CalendarTask existingTask) {
        if (existingTask.getStartTime() == null || existingTask.getEndTime() == null) {
            return false;
        }

        return currentTask.getStartTime().isBefore(existingTask.getEndTime())
                && currentTask.getEndTime().isAfter(existingTask.getStartTime());
    }

    private void reclassifyAfterReschedule(CalendarTask task) {
        task.setEisenhowerQuadrant(null);
        task.setImportant(null);
        task.setUrgent(null);
        applyDefaultEisenhowerClassification(task);
    }

    private ScheduleConfig resolveScheduleConfig(UUID workspaceId, GenerateCalendarRequest request) {
        OnboardingProfile onboarding = onboardingProfileRepository.findByWorkspaceWorkspaceId(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_ONBOARDING_REQUIRED));

        List<WeekDay> onboardingDays = readList(onboarding.getPreferredDays(), WEEK_DAY_LIST_TYPE);
        List<String> onboardingTimeSlots = readList(onboarding.getPreferredTimeSlots(), STRING_LIST_TYPE);
        List<TimeWindow> timeWindows = resolveTimeWindows(onboardingTimeSlots);
        TimeWindow firstTimeWindow = timeWindows.get(0);

        Set<WeekDay> studyDays = validateStudyDays(
                request.getStudyDays() == null || request.getStudyDays().isEmpty()
                        ? onboardingDays
                        : request.getStudyDays()
        );
        LocalDate startDate = request.getStartDate() == null ? LocalDate.now() : request.getStartDate();
        LocalDate endDate = request.getEndDate();
        LocalTime dailyStartTime = request.getDailyStartTime() == null ? firstTimeWindow.startTime() : request.getDailyStartTime();
        int sessionMinutes = resolveSessionMinutes(request, firstTimeWindow);
        int weeklyStudyMinutes = resolveWeeklyStudyMinutes(onboarding.getStudyHoursPerWeek(), studyDays.size());
        int sessionsPerDay = defaultPositive(
                request.getSessionsPerDay(),
                sessionsPerDayFromWeeklyHours(onboarding.getStudyHoursPerWeek(), sessionMinutes, studyDays.size())
        );
        boolean includeReviewSessions = request.getIncludeReviewSessions() == null
                || Boolean.TRUE.equals(request.getIncludeReviewSessions());

        return new ScheduleConfig(
            startDate,
            studyDays,
            dailyStartTime,
            sessionMinutes,
            weeklyStudyMinutes,
            Math.min(sessionsPerDay, MAX_SESSIONS_PER_DAY),
            includeReviewSessions,
            endDate,
            onboarding.getTargetDeadline(),
            onboarding.getPreferredTimeSlots(),
            timeWindows
        );
    }

    private List<TaskDraft> buildTaskDrafts(
            List<RoadmapStep> steps,
            int sessionMinutes,
            boolean includeReviewSessions
    ) {
        List<TaskDraft> drafts = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            RoadmapStep step = steps.get(i);
            int parts = calculateSessionParts(step, sessionMinutes);

            for (int part = 1; part <= parts; part++) {
                drafts.add(new TaskDraft(
                        step,
                        buildLearningTaskTitle(step, part, parts),
                        buildLearningTaskDescription(step, part, parts),
                        CalendarTaskCategory.DEEP_STUDY,
                        step.getDifficulty() == DifficultyLevel.HARD
                                ? CalendarTaskPriority.HIGH
                                : CalendarTaskPriority.MEDIUM,
                        10
                ));
            }

            if (includeReviewSessions && isLastStepOfChapter(steps, i)) {
                drafts.add(new TaskDraft(
                        null,
                        buildReviewTaskTitle(step),
                        buildReviewTaskDescription(step),
                        CalendarTaskCategory.REVIEW,
                        CalendarTaskPriority.MEDIUM,
                        5
                ));
            }
        }

        return drafts;
    }

    private List<PlannedTask> optimizePlanWithAi(List<PlannedTask> plannedTasks, ScheduleConfig config) {
        if (plannedTasks == null || plannedTasks.isEmpty() || !geminiCalendarPlannerClient.isReady()) {
            return plannedTasks;
        }

        List<AiCalendarTaskInput> inputs = new ArrayList<>();
        for (int i = 0; i < plannedTasks.size(); i++) {
            PlannedTask plannedTask = plannedTasks.get(i);
            TaskDraft draft = plannedTask.draft();
            inputs.add(new AiCalendarTaskInput(
                    i,
                    draft.title(),
                    draft.description(),
                    draft.step() == null ? null : resolveChapterTitle(draft.step()),
                    draft.step() == null ? null : draft.step().getDifficulty(),
                    draft.step() == null ? null : draft.step().getEstimatedMinutes(),
                    draft.category(),
                    draft.priority(),
                    plannedTask.taskDate(),
                    plannedTask.startTime(),
                    plannedTask.durationMinutes()
            ));
        }

        AiCalendarPlanDraft aiDraft = geminiCalendarPlannerClient.generate(inputs);
        if (!isValidAiDraft(aiDraft, plannedTasks.size(), config)) {
            return plannedTasks;
        }

        List<PlannedTask> optimized = new ArrayList<>(plannedTasks);
        for (AiCalendarTaskSuggestion suggestion : aiDraft.tasks()) {
            PlannedTask currentPlan = optimized.get(suggestion.taskIndex());
            TaskDraft currentDraft = currentPlan.draft();
            TaskDraft optimizedDraft = new TaskDraft(
                    currentDraft.step(),
                    cleanCalendarTaskTitle(defaultText(suggestion.title(), currentDraft.title()), TITLE_LENGTH),
                    truncate(defaultText(suggestion.description(), currentDraft.description()), SAFE_VARCHAR_LENGTH),
                    suggestion.category() == null ? currentDraft.category() : suggestion.category(),
                    suggestion.priority() == null ? currentDraft.priority() : suggestion.priority(),
                    currentDraft.xpReward()
            );
            int duration = defaultPositive(suggestion.durationMinutes(), currentPlan.durationMinutes());
            LocalTime startTime = suggestion.startTime() == null ? currentPlan.startTime() : suggestion.startTime();
            optimized.set(suggestion.taskIndex(), new PlannedTask(
                    optimizedDraft,
                    suggestion.taskDate(),
                    startTime,
                    startTime.plusMinutes(duration),
                    duration,
                    CalendarTaskSource.AI_GENERATED
            ));
        }

        return optimized;
    }

    private String buildLearningTaskTitle(RoadmapStep step, int part, int totalParts) {
        String suffix = totalParts == 1 ? "" : " (" + part + "/" + totalParts + ")";
        int maxBaseLength = TITLE_LENGTH - "Học: ".length() - suffix.length();
        return "Học: " + cleanDisplayTitle(step.getTitle(), Math.max(20, maxBaseLength)) + suffix;
    }

    private String buildLearningTaskDescription(RoadmapStep step, int part, int totalParts) {
        List<String> details = new ArrayList<>();
        if (totalParts > 1) {
            details.add("Buổi " + part + "/" + totalParts + ".");
        }
        if (step.getSummary() != null && !step.getSummary().isBlank()) {
            details.add(step.getSummary());
        }
        if (step.getKeyConcepts() != null && !step.getKeyConcepts().isEmpty()) {
            details.add("Khái niệm chính: " + String.join(", ", step.getKeyConcepts()));
        }
        return truncate(String.join(" ", details), SAFE_VARCHAR_LENGTH);
    }

    private String buildReviewTaskTitle(RoadmapStep step) {
        return "Ôn tập: " + cleanDisplayTitle(resolveChapterTitle(step), TITLE_LENGTH - "Ôn tập: ".length());
    }

    private String buildReviewTaskDescription(RoadmapStep step) {
        return truncate(
                "Ôn lại nội dung chính của chapter: " + resolveChapterTitle(step),
                SAFE_VARCHAR_LENGTH
        );
    }

    private int calculateSessionParts(RoadmapStep step, int sessionMinutes) {
        int estimatedMinutes = defaultPositive(step.getEstimatedMinutes(), sessionMinutes);
        if (step.getDifficulty() == DifficultyLevel.HARD) {
            estimatedMinutes = (int) Math.ceil(estimatedMinutes * 1.3);
        }
        return Math.max(1, (int) Math.ceil((double) estimatedMinutes / sessionMinutes));
    }

    private int resolveSessionsPerDay(ScheduleConfig config, int taskCount) {
        int totalAvailableStudyDays = resolveAvailableStudyDayCount(config, taskCount);
        if (totalAvailableStudyDays <= 0) {
            return config.sessionsPerDay();
        }

        double density = (double) taskCount / totalAvailableStudyDays;
        int calculatedTasksPerDay = Math.max(1, (int) Math.ceil(density));
        return Math.min(calculatedTasksPerDay, config.sessionsPerDay());
    }

    private CalendarScheduleRun createScheduleRun(
            StudyWorkspace workspace,
            Roadmap roadmap,
            ScheduleConfig config
    ) {
        CalendarScheduleRun run = new CalendarScheduleRun();
        run.setWorkspace(workspace);
        run.setRoadmap(roadmap);
        run.setUser(workspace.getUser());
        run.setScheduleScope(CalendarScheduleScope.FULL_ROADMAP);
        run.setStartDate(config.startDate());
        run.setAvailableDays(new ArrayList<>(config.studyDays()));
        run.setAvailableTimeWindows(config.timeSlotsJson());
        run.setPreferredSessionMinutes(config.sessionMinutes());
        run.setMaxSessionsPerDay(config.sessionsPerDay());
        run.setIncludeReviewSessions(config.includeReviewSessions());
        run.setStatus(CalendarScheduleRunStatus.CONFIRMED);
        run.setConfirmedAt(Instant.now());
        return run;
    }

    private List<PlannedTask> buildRuleBasedPlan(List<TaskDraft> drafts, ScheduleConfig config) {
        List<PlannedTask> plannedTasks = new ArrayList<>();
        if (drafts.isEmpty()) {
            return plannedTasks;
        }

        List<LocalDate> studyDates = resolveStudyDates(config, drafts.size());
        if (studyDates.isEmpty()) {
            // studyDays is already validated non-empty upstream; reaching here means the selected
            // weekdays never occur inside the planning window, i.e. the availability is too tight.
            throw new AppException(ErrorCode.CALENDAR_AVAILABILITY_INSUFFICIENT);
        }

        // Never schedule more sessions in a day than there are selected time windows;
        // each session is placed inside its own window so the plan stays within the
        // user's onboarding availability.
        List<TimeWindow> windows = config.timeWindows();
        int maxSessionsPerDay = Math.min(resolveSessionsPerDay(config, drafts.size()), windows.size());
        int[] sessionsUsedByDay = new int[studyDates.size()];

        List<SessionPlacement> placements = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            TaskDraft draft = drafts.get(i);
            int preferredDayIndex = resolvePreferredStudyDayIndex(i, drafts.size(), studyDates.size());
            int dayIndex = findNextAvailableStudyDayIndex(preferredDayIndex, sessionsUsedByDay, maxSessionsPerDay);

            if (dayIndex < 0) {
                dayIndex = findNextAvailableStudyDayIndex(preferredDayIndex - 1, sessionsUsedByDay, maxSessionsPerDay);
            }
            if (dayIndex < 0) {
                // Every available study day is already filled to maxSessionsPerDay: the chosen
                // days/slots cannot hold the whole roadmap. This is a capacity problem, not a
                // "no study days selected" problem.
                throw new AppException(ErrorCode.CALENDAR_AVAILABILITY_INSUFFICIENT);
            }

            int sessionIndexInDay = sessionsUsedByDay[dayIndex];
            placements.add(new SessionPlacement(draft, studyDates.get(dayIndex), windows.get(sessionIndexInDay)));
            sessionsUsedByDay[dayIndex]++;
        }

        return sizePlacements(placements, config);
    }

    /**
     * Assigns each placed session a human-friendly duration drawn from the weekly distribution
     * computed by {@link StudySessionSizingPolicy}. The distribution is reset per calendar week
     * (Monday-anchored) so every week mirrors the same clean shape (e.g. 90/90/75/75/75/75), and
     * each value is finally clamped to the concrete window it sits in so no task spills past the
     * user's availability and every duration stays a multiple of 15.
     */
    private List<PlannedTask> sizePlacements(List<SessionPlacement> placements, ScheduleConfig config) {
        int maxWindowMinutes = placements.stream()
                .mapToInt(placement -> windowMinutes(placement.window()))
                .max()
                .orElse(config.sessionMinutes());
        List<Integer> weeklyPattern = StudySessionSizingPolicy.planWeeklyDurations(
                config.weeklyStudyMinutes(),
                config.studyDays().size(),
                maxWindowMinutes
        );

        java.util.Map<LocalDate, Integer> sessionsByWeek = new java.util.HashMap<>();
        List<PlannedTask> plannedTasks = new ArrayList<>(placements.size());
        for (SessionPlacement placement : placements) {
            LocalDate weekKey = placement.taskDate()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int indexInWeek = sessionsByWeek.merge(weekKey, 1, Integer::sum) - 1;

            int plannedMinutes = weeklyPattern.isEmpty()
                    ? config.sessionMinutes()
                    : weeklyPattern.get(indexInWeek % weeklyPattern.size());
            int duration = StudySessionSizingPolicy.fitToWindow(plannedMinutes, windowMinutes(placement.window()));

            LocalTime startTime = placement.window().startTime();
            plannedTasks.add(new PlannedTask(
                    placement.draft(),
                    placement.taskDate(),
                    startTime,
                    startTime.plusMinutes(duration),
                    duration,
                    CalendarTaskSource.SYSTEM_GENERATED
            ));
        }

        return plannedTasks;
    }

    private int windowMinutes(TimeWindow window) {
        return (int) Duration.between(window.startTime(), window.endTime()).toMinutes();
    }

    private int resolveAvailableStudyDayCount(ScheduleConfig config, int taskCount) {
        return resolveStudyDates(config, taskCount).size();
    }

    private List<LocalDate> resolveStudyDates(ScheduleConfig config, int taskCount) {
        LocalDate endDate = resolvePlanningEndDate(config, taskCount);
        List<LocalDate> studyDates = new ArrayList<>();
        LocalDate cursor = config.startDate();

        while (!cursor.isAfter(endDate)) {
            if (config.studyDays().contains(toWeekDay(cursor))) {
                studyDates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }

        return studyDates;
    }

    private LocalDate resolvePlanningEndDate(ScheduleConfig config, int taskCount) {
        if (config.endDate() != null && config.endDate().isAfter(config.startDate())) {
            return config.endDate();
        }

        if (config.targetDeadline() != null && config.targetDeadline().isAfter(config.startDate())) {
            return config.targetDeadline();
        }

        return config.startDate().plusDays(30);
    }

    private LocalDate addStudyDays(LocalDate startDate, int studyDayOffset, Set<WeekDay> studyDays) {
        LocalDate cursor = startDate;
        int matchedStudyDays = 0;

        while (matchedStudyDays < studyDayOffset) {
            cursor = cursor.plusDays(1);
            if (studyDays.contains(toWeekDay(cursor))) {
                matchedStudyDays++;
            }
        }

        return cursor;
    }

    private int resolvePreferredStudyDayIndex(int taskIndex, int totalTasks, int totalStudyDays) {
        if (totalTasks <= 1 || totalStudyDays <= 1) {
            return 0;
        }

        double normalizedPosition = (double) taskIndex / (totalTasks - 1);
        return Math.min(
                totalStudyDays - 1,
                Math.max(0, (int) Math.round(normalizedPosition * (totalStudyDays - 1)))
        );
    }

    private int findNextAvailableStudyDayIndex(int preferredDayIndex, int[] sessionsUsedByDay, int maxSessionsPerDay) {
        for (int i = Math.max(0, preferredDayIndex); i < sessionsUsedByDay.length; i++) {
            if (sessionsUsedByDay[i] < maxSessionsPerDay) {
                return i;
            }
        }

        for (int i = Math.min(preferredDayIndex - 1, sessionsUsedByDay.length - 1); i >= 0; i--) {
            if (sessionsUsedByDay[i] < maxSessionsPerDay) {
                return i;
            }
        }

        return -1;
    }

    private List<CalendarTask> createTasks(
            StudyWorkspace workspace,
            Roadmap roadmap,
            List<PlannedTask> plannedTasks
    ) {
        List<CalendarTask> tasks = new ArrayList<>();
        for (PlannedTask plannedTask : plannedTasks) {
            tasks.add(createTask(workspace, roadmap, plannedTask));
        }
        return tasks;
    }

    private CalendarTask createTask(
            StudyWorkspace workspace,
            Roadmap roadmap,
            PlannedTask plannedTask
    ) {
        TaskDraft draft = plannedTask.draft();
        CalendarTask task = new CalendarTask();
        task.setWorkspace(workspace);
        task.setRoadmap(roadmap);
        task.setRoadmapStep(draft.step());
        task.setUser(workspace.getUser());
        task.setTitle(truncate(draft.title(), TITLE_LENGTH));
        task.setDescription(truncate(draft.description(), SAFE_VARCHAR_LENGTH));
        task.setTaskDate(plannedTask.taskDate());
        task.setStartTime(plannedTask.startTime());
        task.setEndTime(plannedTask.endTime());
        task.setDurationMinutes(plannedTask.durationMinutes());
        task.setCategory(draft.category());
        task.setPriority(draft.priority());
        task.setStatus(CalendarTaskStatus.TODO);
        task.setSource(plannedTask.source());
        task.setXpReward(draft.xpReward());
        applyDefaultEisenhowerClassification(task);
        return task;
    }

    private EisenhowerBoardResponse buildEisenhowerBoard(UUID workspaceId, LocalDate date, List<CalendarTask> tasks) {
        List<EisenhowerQuadrantResponse> quadrants = List.of(
                buildQuadrantResponse(tasks, EisenhowerQuadrant.DO_NOW),
                buildQuadrantResponse(tasks, EisenhowerQuadrant.SCHEDULE),
                buildQuadrantResponse(tasks, EisenhowerQuadrant.DELAY_OR_DELEGATE),
                buildQuadrantResponse(tasks, EisenhowerQuadrant.ELIMINATE)
        );

        int completedTasks = (int) tasks.stream()
                .filter(task -> task.getStatus() == CalendarTaskStatus.COMPLETED)
                .count();

        return EisenhowerBoardResponse.builder()
                .workspaceId(workspaceId)
                .date(date)
                .totalTasks(tasks.size())
                .completedTasks(completedTasks)
                .pendingTasks(tasks.size() - completedTasks)
                .quadrants(quadrants)
                .build();
    }

    private EisenhowerQuadrantResponse buildQuadrantResponse(
            List<CalendarTask> tasks,
            EisenhowerQuadrant quadrant
    ) {
        List<CalendarTaskResponse> taskResponses = tasks.stream()
                .filter(task -> resolveEisenhowerQuadrant(task) == quadrant)
                .map(task -> calendarMapper.toTaskResponse(task, quadrant))
                .toList();

        return EisenhowerQuadrantResponse.builder()
                .quadrant(quadrant)
                .title(resolveQuadrantTitle(quadrant))
                .description(resolveQuadrantDescription(quadrant))
                .taskCount(taskResponses.size())
                .tasks(taskResponses)
                .build();
    }

    private void applyDefaultEisenhowerClassification(CalendarTask task) {
        EisenhowerQuadrant quadrant = resolveEisenhowerQuadrant(task);
        task.setEisenhowerQuadrant(quadrant);
        task.setImportant(isImportantQuadrant(quadrant));
        task.setUrgent(isUrgentQuadrant(quadrant));
        task.setImportanceScore(resolveImportanceScore(quadrant));
        task.setUrgencyScore(resolveUrgencyScore(quadrant));
        task.setClassificationReason(resolveDefaultClassificationReason(task, quadrant));
        task.setClassifiedBy(ClassifiedBy.RULE_BASED);
        task.setClassifiedAt(Instant.now());
    }

    private EisenhowerQuadrant resolveEisenhowerQuadrant(CalendarTask task) {
        if (task.getEisenhowerQuadrant() != null) {
            return task.getEisenhowerQuadrant();
        }

        Boolean important = task.getImportant();
        Boolean urgent = task.getUrgent();
        if (important != null || urgent != null) {
            return resolveQuadrant(Boolean.TRUE.equals(important), Boolean.TRUE.equals(urgent));
        }

        CalendarTaskCategory category = task.getCategory();
        DifficultyLevel difficulty = resolveTaskDifficulty(task);
        CalendarTaskPriority priority = task.getPriority() == null ? CalendarTaskPriority.MEDIUM : task.getPriority();

        if (category == CalendarTaskCategory.PERSONAL) {
            return priority == CalendarTaskPriority.HIGH
                    ? EisenhowerQuadrant.SCHEDULE
                    : EisenhowerQuadrant.ELIMINATE;
        }

        if (category == CalendarTaskCategory.REVIEW) {
            return EisenhowerQuadrant.SCHEDULE;
        }

        if (priority == CalendarTaskPriority.HIGH || difficulty == DifficultyLevel.HARD) {
            return EisenhowerQuadrant.DO_NOW;
        }

        if (priority == CalendarTaskPriority.LOW || difficulty == DifficultyLevel.EASY) {
            return EisenhowerQuadrant.DELAY_OR_DELEGATE;
        }

        return switch (priority) {
            case HIGH -> EisenhowerQuadrant.DO_NOW;
            case MEDIUM -> EisenhowerQuadrant.SCHEDULE;
            case LOW -> task.getCategory() == CalendarTaskCategory.PERSONAL
                    ? EisenhowerQuadrant.ELIMINATE
                    : EisenhowerQuadrant.DELAY_OR_DELEGATE;
        };
    }

    private EisenhowerQuadrant resolveQuadrant(boolean important, boolean urgent) {
        if (important && urgent) {
            return EisenhowerQuadrant.DO_NOW;
        }
        if (important) {
            return EisenhowerQuadrant.SCHEDULE;
        }
        if (urgent) {
            return EisenhowerQuadrant.DELAY_OR_DELEGATE;
        }
        return EisenhowerQuadrant.ELIMINATE;
    }

    private boolean isImportantQuadrant(EisenhowerQuadrant quadrant) {
        return quadrant == EisenhowerQuadrant.DO_NOW || quadrant == EisenhowerQuadrant.SCHEDULE;
    }

    private boolean isUrgentQuadrant(EisenhowerQuadrant quadrant) {
        return quadrant == EisenhowerQuadrant.DO_NOW || quadrant == EisenhowerQuadrant.DELAY_OR_DELEGATE;
    }

    private BigDecimal resolveImportanceScore(EisenhowerQuadrant quadrant) {
        return switch (quadrant) {
            case DO_NOW -> BigDecimal.valueOf(0.90);
            case SCHEDULE -> BigDecimal.valueOf(0.80);
            case DELAY_OR_DELEGATE -> BigDecimal.valueOf(0.35);
            case ELIMINATE -> BigDecimal.valueOf(0.20);
        };
    }

    private BigDecimal resolveUrgencyScore(EisenhowerQuadrant quadrant) {
        return switch (quadrant) {
            case DO_NOW -> BigDecimal.valueOf(0.90);
            case SCHEDULE -> BigDecimal.valueOf(0.35);
            case DELAY_OR_DELEGATE -> BigDecimal.valueOf(0.70);
            case ELIMINATE -> BigDecimal.valueOf(0.20);
        };
    }

    private String resolveQuadrantTitle(EisenhowerQuadrant quadrant) {
        return switch (quadrant) {
            case DO_NOW -> "Làm ngay";
            case SCHEDULE -> "Lên lịch";
            case DELAY_OR_DELEGATE -> "Để sau";
            case ELIMINATE -> "Loại bỏ";
        };
    }

    private String resolveQuadrantDescription(EisenhowerQuadrant quadrant) {
        return switch (quadrant) {
            case DO_NOW -> "Quan trọng và khẩn cấp";
            case SCHEDULE -> "Quan trọng nhưng không khẩn cấp";
            case DELAY_OR_DELEGATE -> "Khẩn cấp nhưng ít quan trọng";
            case ELIMINATE -> "Không quan trọng và không khẩn cấp";
        };
    }

    private String resolveDefaultClassificationReason(CalendarTask task, EisenhowerQuadrant quadrant) {
        CalendarTaskCategory category = task.getCategory();
        DifficultyLevel difficulty = resolveTaskDifficulty(task);

        if (category == CalendarTaskCategory.REVIEW) {
            return "Task ôn tập quan trọng nhưng nên làm theo lịch đã xếp.";
        }
        if (category == CalendarTaskCategory.PERSONAL) {
            return "Task cá nhân không ảnh hưởng trực tiếp đến tiến độ học.";
        }
        if (difficulty == DifficultyLevel.HARD) {
            return "Nội dung khó nên được ưu tiên xử lý trong buổi học.";
        }
        if (difficulty == DifficultyLevel.EASY) {
            return "Nội dung dễ hơn, có thể xử lý sau các task quan trọng.";
        }

        return switch (quadrant) {
            case DO_NOW -> "Task có độ ưu tiên cao nên cần xử lý trước.";
            case SCHEDULE -> "Task học tập quan trọng và nên được làm theo lịch.";
            case DELAY_OR_DELEGATE -> "Task ít quan trọng hơn, có thể xử lý sau.";
            case ELIMINATE -> "Task không ảnh hưởng trực tiếp đến tiến độ học.";
        };
    }

    private DifficultyLevel resolveTaskDifficulty(CalendarTask task) {
        if (task.getRoadmapStep() == null || task.getRoadmapStep().getDifficulty() == null) {
            return null;
        }
        return task.getRoadmapStep().getDifficulty();
    }

    private CalendarTaskStatus resolveStatus(String raw) {
        if (raw == null) return CalendarTaskStatus.TODO;
        return switch (raw.toUpperCase()) {
            case "COMPLETED" -> CalendarTaskStatus.COMPLETED;
            case "MISSED"    -> CalendarTaskStatus.MISSED;
            case "CANCELLED" -> CalendarTaskStatus.CANCELLED;
            default          -> CalendarTaskStatus.TODO;
        };
    }

    private void completeRoadmapStepIfReady(RoadmapStep step) {
        List<CalendarTask> activeStepTasks = calendarTaskRepository.findByRoadmapStepStepIdAndStatusNot(
                step.getStepId(),
                CalendarTaskStatus.CANCELLED
        );
        boolean stepReady = activeStepTasks.stream()
                .allMatch(task -> task.getStatus() == CalendarTaskStatus.COMPLETED)
                && hasEnoughStudyMinutesForStep(step);

        if (!stepReady) {
            return;
        }

        Roadmap roadmap = step.getRoadmap();
        if (step.getStatus() == RoadmapStepStatus.COMPLETED) {
            awardRoadmapStepPointsIfEligible(step);
            awardRoadmapPointsIfEligible(roadmap);
            return;
        }

        RoadmapStepStatus oldStatus = step.getStatus();
        boolean wasCurrentStep = oldStatus == RoadmapStepStatus.CURRENT;
        step.setStatus(RoadmapStepStatus.COMPLETED);
        step.setCompletedAt(Instant.now());
        roadmapStepRepository.save(step);
        createProgressLog(roadmap, step, RoadmapProgressActionType.STEP_COMPLETED, oldStatus, RoadmapStepStatus.COMPLETED);
        awardRoadmapStepPointsIfEligible(step);

        List<RoadmapStep> completedSteps = roadmapStepRepository.findByRoadmapRoadmapIdAndStatus(
                roadmap.getRoadmapId(),
                RoadmapStepStatus.COMPLETED
        );
        roadmap.setCompletedSteps(completedSteps.size());
        roadmap.setProgressPercent(calculateProgressPercent(completedSteps.size(), roadmap.getTotalSteps()));

        if (wasCurrentStep) {
            activateNextStepIfNeeded(roadmap);
        }
        awardRoadmapPointsIfEligible(roadmap);
        roadmapRepository.save(roadmap);
    }

    private void awardRoadmapStepPointsIfEligible(RoadmapStep step) {
        if (!hasEnoughStudyMinutesForStep(step)) {
            return;
        }
        pointService.awardRoadmapStepCompleted(step.getWorkspace().getUser(), step.getWorkspace(), step.getStepId());
    }

    private void awardRoadmapPointsIfEligible(Roadmap roadmap) {
        List<RoadmapStep> completedSteps = roadmapStepRepository.findByRoadmapRoadmapIdAndStatus(
                roadmap.getRoadmapId(),
                RoadmapStepStatus.COMPLETED
        );

        // Gate on the actual "every step is done" condition rather than on the
        // roadmap's own status flag. The status was previously only promoted to
        // COMPLETED inside activateNextStepIfNeeded(), which runs solely when the
        // step just finished was the CURRENT one. Re-completion (the early-return
        // path above) and any non-linear final-step completion therefore left the
        // roadmap stuck ACTIVE, and this guard then silently blocked the +700 award
        // forever — the symptom being a chest that never writes a ROADMAP_COMPLETED
        // ledger row for the client to verify.
        boolean allStepsCompleted = roadmap.getTotalSteps() != null
                && roadmap.getTotalSteps() > 0
                && completedSteps.size() == roadmap.getTotalSteps();
        if (!allStepsCompleted) {
            return;
        }

        // Promote the roadmap itself the moment every step is done (idempotent).
        if (roadmap.getStatus() != RoadmapStatus.COMPLETED) {
            roadmap.setStatus(RoadmapStatus.COMPLETED);
            roadmap.setCurrentStep(null);
            roadmapRepository.save(roadmap);
        }

        // Re-attempt per-step awards before the final roadmap reward. awardUnique is
        // idempotent, so already rewarded steps are no-ops.
        completedSteps.forEach(this::awardRoadmapStepPointsIfEligible);

        // Reward is now manually claimed by clicking the chest via `/claim-reward` endpoint.
    }

    private boolean hasEnoughStudyMinutesForStep(RoadmapStep step) {
        String userId = step.getWorkspace().getUser().getUserId();
        Long studiedMinutes = studySessionRepository.sumValidDurationMinutesByUserAndRoadmapStepAndStatus(
                userId,
                step.getStepId(),
                StudySessionStatus.COMPLETED,
                MIN_VALID_STUDY_MINUTES
        );
        return safeLong(studiedMinutes) >= calculateRequiredStudyMinutesForStep(step);
    }

    private void ensureTaskHasEnoughValidStudyMinutes(CalendarTask task) {
        if (task.getRoadmapStep() == null || hasEnoughValidStudyMinutesForTask(task)) {
            return;
        }
        throw new AppException(ErrorCode.CALENDAR_TASK_STUDY_TIME_REQUIRED);
    }

    private boolean hasEnoughValidStudyMinutesForTask(CalendarTask task) {
        Long studiedMinutes = studySessionRepository.sumValidDurationMinutesByUserAndCalendarTaskAndStatus(
                task.getUser().getUserId(),
                task.getTaskId(),
                StudySessionStatus.COMPLETED,
                MIN_VALID_STUDY_MINUTES
        );
        return safeLong(studiedMinutes) >= calculateRequiredStudyMinutesForTask(task);
    }

    private int calculateRequiredStudyMinutesForTask(CalendarTask task) {
        if (task == null || task.getDurationMinutes() == null || task.getDurationMinutes() <= 0) {
            return FALLBACK_REQUIRED_STUDY_MINUTES;
        }
        return task.getDurationMinutes();
    }

    private int calculateRequiredStudyMinutesForStep(RoadmapStep step) {
        if (step == null) {
            return MIN_STEP_POINT_STUDY_MINUTES;
        }
        if (step.getEstimatedMinutes() != null && step.getEstimatedMinutes() > 0) {
            return step.getEstimatedMinutes();
        }

        int scheduledMinutes = calendarTaskRepository.findByRoadmapStepStepIdAndStatusNot(
                        step.getStepId(),
                        CalendarTaskStatus.CANCELLED
                )
                .stream()
                .map(CalendarTask::getDurationMinutes)
                .filter(Objects::nonNull)
                .filter(duration -> duration > 0)
                .mapToInt(Integer::intValue)
                .sum();
        return scheduledMinutes > 0 ? scheduledMinutes : MIN_STEP_POINT_STUDY_MINUTES;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private void activateNextStepIfNeeded(Roadmap roadmap) {
        List<RoadmapStep> upcomingSteps = roadmapStepRepository.findByRoadmapRoadmapIdAndStatusOrderBySequenceNoAsc(
                roadmap.getRoadmapId(),
                RoadmapStepStatus.UPCOMING
        );

        if (upcomingSteps.isEmpty()) {
            if (Objects.equals(roadmap.getCompletedSteps(), roadmap.getTotalSteps())) {
                roadmap.setStatus(RoadmapStatus.COMPLETED);
                roadmap.setCurrentStep(null);
            }
            return;
        }

        RoadmapStep nextStep = upcomingSteps.get(0);
        RoadmapStepStatus oldStatus = nextStep.getStatus();
        nextStep.setStatus(RoadmapStepStatus.CURRENT);
        roadmapStepRepository.save(nextStep);
        roadmap.setCurrentStep(nextStep);
        createProgressLog(roadmap, nextStep, RoadmapProgressActionType.NEXT_STEP_ACTIVATED, oldStatus, RoadmapStepStatus.CURRENT);
    }

    private void createProgressLog(
            Roadmap roadmap,
            RoadmapStep step,
            RoadmapProgressActionType actionType,
            RoadmapStepStatus oldStatus,
            RoadmapStepStatus newStatus
    ) {
        RoadmapProgressLog log = new RoadmapProgressLog();
        log.setRoadmap(roadmap);
        log.setStep(step);
        log.setUser(roadmap.getUser());
        log.setActionType(actionType);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        roadmapProgressLogRepository.save(log);
    }

    private void ensureCalendarNotGenerated(UUID roadmapId) {
        boolean hasActiveCalendarTasks = !calendarTaskRepository.findByRoadmapRoadmapIdAndStatusNot(
                roadmapId,
                CalendarTaskStatus.CANCELLED
        ).isEmpty();

        if (hasActiveCalendarTasks) {
            throw new AppException(ErrorCode.CALENDAR_ALREADY_GENERATED);
        }
    }

    private Set<WeekDay> validateStudyDays(List<WeekDay> studyDays) {
        if (studyDays == null || studyDays.isEmpty()) {
            throw new AppException(ErrorCode.CALENDAR_STUDY_DAYS_REQUIRED);
        }
        return EnumSet.copyOf(studyDays);
    }

    /**
     * Parses every onboarding time slot into a validated, de-duplicated, chronologically
     * sorted list of windows. The full set is used for scheduling so the generated calendar
     * respects every selected window, not only the first one. Throws when no valid window
     * exists so we never silently fall back to an arbitrary schedule.
     */
    private List<TimeWindow> resolveTimeWindows(List<String> timeSlots) {
        if (timeSlots == null || timeSlots.isEmpty()) {
            throw new AppException(ErrorCode.CALENDAR_TIME_SLOT_REQUIRED);
        }

        List<TimeWindow> windows = new ArrayList<>();
        for (String slot : timeSlots) {
            TimeWindow window = parseTimeWindow(slot);
            if (window != null && !windows.contains(window)) {
                windows.add(window);
            }
        }

        if (windows.isEmpty()) {
            throw new AppException(ErrorCode.CALENDAR_TIME_SLOT_REQUIRED);
        }

        windows.sort(Comparator.comparing(TimeWindow::startTime).thenComparing(TimeWindow::endTime));
        return windows;
    }

    private TimeWindow parseTimeWindow(String slot) {
        if (slot == null || slot.isBlank()) {
            return null;
        }

        String[] parts = slot.split("-");
        try {
            LocalTime startTime = LocalTime.parse(parts[0].trim());
            LocalTime endTime = parts.length > 1 ? LocalTime.parse(parts[1].trim()) : startTime.plusHours(1);
            if (!endTime.isAfter(startTime)) {
                return null;
            }
            return new TimeWindow(startTime, endTime);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private int resolveSessionMinutes(GenerateCalendarRequest request, TimeWindow firstTimeWindow) {
        if (request.getSessionMinutes() != null && request.getSessionMinutes() > 0) {
            return request.getSessionMinutes();
        }

        int slotMinutes = (int) Duration.between(firstTimeWindow.startTime(), firstTimeWindow.endTime()).toMinutes();
        return defaultPositive(slotMinutes, DEFAULT_SESSION_MINUTES);
    }

    /**
     * Weekly study budget in minutes, used by {@link StudySessionSizingPolicy} to size sessions.
     * Falls back to one default-length session per selected study day when onboarding never captured
     * a weekly commitment.
     */
    private int resolveWeeklyStudyMinutes(BigDecimal studyHoursPerWeek, int studyDayCount) {
        int fallback = Math.max(1, studyDayCount) * DEFAULT_SESSION_MINUTES;
        if (studyHoursPerWeek == null || studyHoursPerWeek.compareTo(BigDecimal.ZERO) <= 0) {
            return fallback;
        }
        return studyHoursPerWeek.multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private int sessionsPerDayFromWeeklyHours(BigDecimal studyHoursPerWeek, int sessionMinutes, int studyDayCount) {
        if (studyHoursPerWeek == null || studyHoursPerWeek.compareTo(BigDecimal.ZERO) <= 0 || studyDayCount <= 0) {
            return DEFAULT_SESSIONS_PER_DAY;
        }

        int weeklyMinutes = studyHoursPerWeek.multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.CEILING)
                .intValue();
        int weeklySessions = Math.max(1, (int) Math.ceil((double) weeklyMinutes / sessionMinutes));
        return Math.max(DEFAULT_SESSIONS_PER_DAY, (int) Math.ceil((double) weeklySessions / studyDayCount));
    }

    private int countStudyDays(LocalDate startDate, LocalDate deadline, Set<WeekDay> studyDays) {
        int count = 0;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(deadline)) {
            if (studyDays.contains(toWeekDay(cursor))) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    private boolean isLastStepOfChapter(List<RoadmapStep> steps, int index) {
        if (index == steps.size() - 1) {
            return true;
        }

        UUID currentChapterId = steps.get(index).getChapter() == null
                ? null
                : steps.get(index).getChapter().getChapterId();
        UUID nextChapterId = steps.get(index + 1).getChapter() == null
                ? null
                : steps.get(index + 1).getChapter().getChapterId();

        return !Objects.equals(currentChapterId, nextChapterId);
    }

    private String resolveChapterTitle(RoadmapStep step) {
        if (step.getChapter() != null && step.getChapter().getTitle() != null) {
            return step.getChapter().getTitle();
        }
        if (step.getSubtitle() != null && !step.getSubtitle().isBlank()) {
            return step.getSubtitle();
        }
        return step.getTitle();
    }

    private boolean isValidAiDraft(AiCalendarPlanDraft draft, int taskCount, ScheduleConfig config) {
        if (draft == null || draft.tasks() == null || draft.tasks().size() != taskCount) {
            return false;
        }

        Set<Integer> indexes = new HashSet<>();
        for (AiCalendarTaskSuggestion suggestion : draft.tasks()) {
            if (suggestion == null || suggestion.taskIndex() == null) {
                return false;
            }
            int index = suggestion.taskIndex();
            if (index < 0 || index >= taskCount || !indexes.add(index)) {
                return false;
            }
            if (suggestion.taskDate() == null || suggestion.taskDate().isBefore(config.startDate())) {
                return false;
            }
            if (!config.studyDays().contains(toWeekDay(suggestion.taskDate()))) {
                return false;
            }
            if (suggestion.startTime() == null || suggestion.durationMinutes() == null || suggestion.durationMinutes() <= 0) {
                return false;
            }
            // Reject AI output that drifts back to odd, sub-minimum or over-cap durations: the planner
            // contract is human-friendly multiples of 15 within [MIN_SCHEDULED, HARD_MAX]. Anything
            // else falls back to the rule-based plan.
            int aiDuration = suggestion.durationMinutes();
            if (!StudySessionSizingPolicy.isHumanFriendly(aiDuration)
                    || aiDuration < StudySessionSizingPolicy.MIN_SCHEDULED_SESSION_MINUTES
                    || aiDuration > StudySessionSizingPolicy.HARD_MAX_SESSION_MINUTES) {
                return false;
            }
            LocalTime endTime = suggestion.startTime().plusMinutes(aiDuration);
            if (!endTime.isAfter(suggestion.startTime())) {
                return false;
            }
            if (!fitsInAnyWindow(suggestion.startTime(), endTime, config.timeWindows())) {
                return false;
            }
        }

        LocalDate previousDate = null;
        LocalTime previousEndTime = null;
        for (int i = 0; i < taskCount; i++) {
            AiCalendarTaskSuggestion suggestion = findSuggestionByIndex(draft.tasks(), i);
            LocalTime endTime = suggestion.startTime().plusMinutes(suggestion.durationMinutes());

            if (previousDate != null) {
                boolean beforePrevious = suggestion.taskDate().isBefore(previousDate)
                        || (suggestion.taskDate().equals(previousDate) && suggestion.startTime().isBefore(previousEndTime));
                if (beforePrevious) {
                    return false;
                }
            }

            previousDate = suggestion.taskDate();
            previousEndTime = endTime;
        }

        return hasValidDailyCapacity(draft.tasks(), config.sessionsPerDay());
    }

    private boolean fitsInAnyWindow(LocalTime startTime, LocalTime endTime, List<TimeWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            return false;
        }
        return windows.stream().anyMatch(window ->
                !startTime.isBefore(window.startTime()) && !endTime.isAfter(window.endTime()));
    }

    private AiCalendarTaskSuggestion findSuggestionByIndex(List<AiCalendarTaskSuggestion> suggestions, int index) {
        return suggestions.stream()
                .filter(suggestion -> Objects.equals(suggestion.taskIndex(), index))
                .findFirst()
                .orElseThrow();
    }

    private boolean hasValidDailyCapacity(List<AiCalendarTaskSuggestion> suggestions, int sessionsPerDay) {
        return suggestions.stream()
                .map(AiCalendarTaskSuggestion::taskDate)
                .distinct()
                .allMatch(date -> suggestions.stream()
                        .filter(suggestion -> date.equals(suggestion.taskDate()))
                        .count() <= sessionsPerDay);
    }

    private WeekDay toWeekDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return WeekDay.valueOf(dayOfWeek.name());
    }

    private int defaultPositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.CALENDAR_INVALID_TIME_RANGE);
        }
    }

    private Integer calculateDurationMinutes(LocalTime startTime, LocalTime endTime, Integer fallback) {
        if (startTime == null || endTime == null) {
            return fallback;
        }
        return (int) Duration.between(startTime, endTime).toMinutes();
    }

    private BigDecimal calculateProgressPercent(int completedSteps, Integer totalSteps) {
        if (totalSteps == null || totalSteps == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(completedSteps)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalSteps), 2, RoundingMode.HALF_UP);
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.ONBOARDING_READ_FAILED);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String cleanCalendarTaskTitle(String value, int maxLength) {
        String title = defaultText(value, "Nhiệm vụ học").replaceAll("\\s+", " ").trim();
        if (title.regionMatches(true, 0, "Học: ", 0, "Học: ".length())) {
            return "Học: " + cleanDisplayTitle(title.substring("Học: ".length()), maxLength - "Học: ".length());
        }
        if (title.regionMatches(true, 0, "Ôn tập: ", 0, "Ôn tập: ".length())) {
            return "Ôn tập: " + cleanDisplayTitle(title.substring("Ôn tập: ".length()), maxLength - "Ôn tập: ".length());
        }
        return cleanDisplayTitle(title, maxLength);
    }

    private String cleanDisplayTitle(String value, int maxLength) {
        String title = defaultText(value, "Nội dung học").replaceAll("\\s+", " ").trim();
        Matcher matcher = DISPLAY_STEP_PREFIX_PATTERN.matcher(title);
        if (matcher.matches()) {
            title = matcher.group(1).trim();
        }
        return truncate(title, maxLength);
    }

    private record TimeWindow(LocalTime startTime, LocalTime endTime) {
    }

    private record SessionPlacement(TaskDraft draft, LocalDate taskDate, TimeWindow window) {
    }

    private record ScheduleConfig(
            LocalDate startDate,
            Set<WeekDay> studyDays,
            LocalTime dailyStartTime,
            int sessionMinutes,
            int weeklyStudyMinutes,
            int sessionsPerDay,
            boolean includeReviewSessions,
            LocalDate endDate,
            LocalDate targetDeadline,
            String timeSlotsJson,
            List<TimeWindow> timeWindows
    ) {

        ScheduleConfig withSessionsPerDay(int value) {
            return new ScheduleConfig(
                    startDate,
                    studyDays,
                    dailyStartTime,
                    sessionMinutes,
                    weeklyStudyMinutes,
                    value,
                    includeReviewSessions,
                    endDate,
                    targetDeadline,
                    timeSlotsJson,
                    timeWindows
            );
        }
    }

    private record TaskDraft(
            RoadmapStep step,
            String title,
            String description,
            CalendarTaskCategory category,
            CalendarTaskPriority priority,
            int xpReward
    ) {
    }

    private record PlannedTask(
            TaskDraft draft,
            LocalDate taskDate,
            LocalTime startTime,
            LocalTime endTime,
            int durationMinutes,
            CalendarTaskSource source
    ) {
        }
}
