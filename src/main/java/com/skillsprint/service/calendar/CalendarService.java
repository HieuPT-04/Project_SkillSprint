package com.skillsprint.service.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.calendar.GenerateCalendarRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskRequest;
import com.skillsprint.dto.response.calendar.CalendarScheduleRunResponse;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
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
import com.skillsprint.enums.calendar.WeekDay;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.roadmap.RoadmapProgressActionType;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
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
import com.skillsprint.repository.StudyWorkspaceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
    static int TITLE_LENGTH = 90;
    static int SAFE_VARCHAR_LENGTH = 250;

    StudyWorkspaceRepository workspaceRepository;
    RoadmapRepository roadmapRepository;
    RoadmapStepRepository roadmapStepRepository;
    RoadmapProgressLogRepository roadmapProgressLogRepository;
    OnboardingProfileRepository onboardingProfileRepository;
    CalendarScheduleRunRepository scheduleRunRepository;
    CalendarTaskRepository calendarTaskRepository;
    CalendarMapper calendarMapper;
    ObjectMapper objectMapper;

    @Transactional
    public CalendarScheduleRunResponse generate(
            String userId,
            UUID workspaceId,
            GenerateCalendarRequest request
    ) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
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

        CalendarScheduleRun run = createScheduleRun(workspace, roadmap, finalConfig);
        CalendarScheduleRun savedRun = scheduleRunRepository.saveAndFlush(run);
        List<CalendarTask> tasks = calendarTaskRepository.saveAllAndFlush(createTasks(
                workspace,
                roadmap,
                drafts,
                finalConfig
        ));

        savedRun.setEndDate(tasks.get(tasks.size() - 1).getTaskDate());
        CalendarScheduleRun updatedRun = scheduleRunRepository.saveAndFlush(savedRun);
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

    @Transactional
    public CalendarTaskResponse updateTask(
            String userId,
            UUID taskId,
            UpdateCalendarTaskRequest request
    ) {
        CalendarTask task = findOwnedTask(userId, taskId);

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

        return calendarMapper.toTaskResponse(calendarTaskRepository.save(task));
    }

    @Transactional
    public CalendarTaskResponse completeTask(String userId, UUID taskId) {
        CalendarTask task = findOwnedTask(userId, taskId);

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

    private ScheduleConfig resolveScheduleConfig(UUID workspaceId, GenerateCalendarRequest request) {
        OnboardingProfile onboarding = onboardingProfileRepository.findByWorkspaceWorkspaceId(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_ONBOARDING_REQUIRED));

        List<WeekDay> onboardingDays = readList(onboarding.getPreferredDays(), WEEK_DAY_LIST_TYPE);
        List<String> onboardingTimeSlots = readList(onboarding.getPreferredTimeSlots(), STRING_LIST_TYPE);
        TimeWindow firstTimeWindow = resolveFirstTimeWindow(onboardingTimeSlots);

        Set<WeekDay> studyDays = validateStudyDays(
                request.getStudyDays() == null || request.getStudyDays().isEmpty()
                        ? onboardingDays
                        : request.getStudyDays()
        );
        LocalDate startDate = request.getStartDate() == null ? LocalDate.now() : request.getStartDate();
        LocalTime dailyStartTime = request.getDailyStartTime() == null ? firstTimeWindow.startTime() : request.getDailyStartTime();
        int sessionMinutes = resolveSessionMinutes(request, firstTimeWindow);
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
                Math.min(sessionsPerDay, MAX_SESSIONS_PER_DAY),
                includeReviewSessions,
                onboarding.getTargetDeadline(),
                onboarding.getPreferredTimeSlots()
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

    private String buildLearningTaskTitle(RoadmapStep step, int part, int totalParts) {
        String suffix = totalParts == 1 ? "" : " (" + part + "/" + totalParts + ")";
        int maxBaseLength = TITLE_LENGTH - "Học: ".length() - suffix.length();
        return "Học: " + truncate(step.getTitle(), Math.max(20, maxBaseLength)) + suffix;
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
        return "Ôn tập: " + truncate(resolveChapterTitle(step), TITLE_LENGTH - "Ôn tập: ".length());
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
        if (config.targetDeadline() == null || config.targetDeadline().isBefore(config.startDate())) {
            return config.sessionsPerDay();
        }

        int studyDayCount = countStudyDays(config.startDate(), config.targetDeadline(), config.studyDays());
        if (studyDayCount == 0) {
            return config.sessionsPerDay();
        }

        int requiredSessionsPerDay = (int) Math.ceil((double) taskCount / studyDayCount);
        return Math.min(Math.max(config.sessionsPerDay(), requiredSessionsPerDay), MAX_SESSIONS_PER_DAY);
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

    private List<CalendarTask> createTasks(
            StudyWorkspace workspace,
            Roadmap roadmap,
            List<TaskDraft> drafts,
            ScheduleConfig config
    ) {
        List<CalendarTask> tasks = new ArrayList<>();
        LocalDate currentDate = config.startDate();
        int sessionsUsedToday = 0;

        for (TaskDraft draft : drafts) {
            while (!config.studyDays().contains(toWeekDay(currentDate))
                    || sessionsUsedToday >= config.sessionsPerDay()) {
                currentDate = currentDate.plusDays(1);
                sessionsUsedToday = 0;
            }

            LocalTime startTime = config.dailyStartTime().plusMinutes((long) sessionsUsedToday * config.sessionMinutes());
            LocalTime endTime = startTime.plusMinutes(config.sessionMinutes());

            tasks.add(createTask(workspace, roadmap, draft, currentDate, startTime, endTime, config.sessionMinutes()));
            sessionsUsedToday++;
        }

        return tasks;
    }

    private CalendarTask createTask(
            StudyWorkspace workspace,
            Roadmap roadmap,
            TaskDraft draft,
            LocalDate taskDate,
            LocalTime startTime,
            LocalTime endTime,
            int duration
    ) {
        CalendarTask task = new CalendarTask();
        task.setWorkspace(workspace);
        task.setRoadmap(roadmap);
        task.setRoadmapStep(draft.step());
        task.setUser(workspace.getUser());
        task.setTitle(truncate(draft.title(), TITLE_LENGTH));
        task.setDescription(truncate(draft.description(), SAFE_VARCHAR_LENGTH));
        task.setTaskDate(taskDate);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setDurationMinutes(duration);
        task.setCategory(draft.category());
        task.setPriority(draft.priority());
        task.setStatus(CalendarTaskStatus.TODO);
        task.setSource(CalendarTaskSource.SYSTEM_GENERATED);
        task.setXpReward(draft.xpReward());
        return task;
    }

    private void completeRoadmapStepIfReady(RoadmapStep step) {
        List<CalendarTask> activeStepTasks = calendarTaskRepository.findByRoadmapStepStepIdAndStatusNot(
                step.getStepId(),
                CalendarTaskStatus.CANCELLED
        );
        boolean stepReady = activeStepTasks.stream()
                .allMatch(task -> task.getStatus() == CalendarTaskStatus.COMPLETED);

        if (!stepReady || step.getStatus() == RoadmapStepStatus.COMPLETED) {
            return;
        }

        Roadmap roadmap = step.getRoadmap();
        RoadmapStepStatus oldStatus = step.getStatus();
        boolean wasCurrentStep = oldStatus == RoadmapStepStatus.CURRENT;
        step.setStatus(RoadmapStepStatus.COMPLETED);
        step.setCompletedAt(Instant.now());
        roadmapStepRepository.save(step);
        createProgressLog(roadmap, step, RoadmapProgressActionType.STEP_COMPLETED, oldStatus, RoadmapStepStatus.COMPLETED);

        List<RoadmapStep> completedSteps = roadmapStepRepository.findByRoadmapRoadmapIdAndStatus(
                roadmap.getRoadmapId(),
                RoadmapStepStatus.COMPLETED
        );
        roadmap.setCompletedSteps(completedSteps.size());
        roadmap.setProgressPercent(calculateProgressPercent(completedSteps.size(), roadmap.getTotalSteps()));

        if (wasCurrentStep) {
            activateNextStepIfNeeded(roadmap);
        }
        roadmapRepository.save(roadmap);
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

    private TimeWindow resolveFirstTimeWindow(List<String> timeSlots) {
        if (timeSlots == null || timeSlots.isEmpty()) {
            throw new AppException(ErrorCode.CALENDAR_TIME_SLOT_REQUIRED);
        }

        String firstSlot = timeSlots.get(0);
        String[] parts = firstSlot.split("-");
        try {
            LocalTime startTime = LocalTime.parse(parts[0].trim());
            LocalTime endTime = parts.length > 1 ? LocalTime.parse(parts[1].trim()) : startTime.plusHours(1);
            validateTimeRange(startTime, endTime);
            return new TimeWindow(startTime, endTime);
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.CALENDAR_TIME_SLOT_REQUIRED);
        }
    }

    private int resolveSessionMinutes(GenerateCalendarRequest request, TimeWindow firstTimeWindow) {
        if (request.getSessionMinutes() != null && request.getSessionMinutes() > 0) {
            return request.getSessionMinutes();
        }

        int slotMinutes = (int) Duration.between(firstTimeWindow.startTime(), firstTimeWindow.endTime()).toMinutes();
        return defaultPositive(slotMinutes, DEFAULT_SESSION_MINUTES);
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

    private record TimeWindow(LocalTime startTime, LocalTime endTime) {
    }

    private record ScheduleConfig(
            LocalDate startDate,
            Set<WeekDay> studyDays,
            LocalTime dailyStartTime,
            int sessionMinutes,
            int sessionsPerDay,
            boolean includeReviewSessions,
            LocalDate targetDeadline,
            String timeSlotsJson
    ) {

        ScheduleConfig withSessionsPerDay(int value) {
            return new ScheduleConfig(
                    startDate,
                    studyDays,
                    dailyStartTime,
                    sessionMinutes,
                    value,
                    includeReviewSessions,
                    targetDeadline,
                    timeSlotsJson
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
}
