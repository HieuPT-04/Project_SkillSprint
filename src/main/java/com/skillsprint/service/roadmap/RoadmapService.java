package com.skillsprint.service.roadmap;

import com.skillsprint.dto.response.roadmap.RoadmapResponse;
import com.skillsprint.entity.Chapter;
import com.skillsprint.entity.LearningStructureVersion;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.RoadmapStepResource;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.Topic;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.learningstructure.LearningStructureStatus;
import com.skillsprint.enums.roadmap.ResourcePlatform;
import com.skillsprint.enums.roadmap.ResourceType;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.RoadmapMapper;
import com.skillsprint.repository.LearningStructureVersionRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.repository.RoadmapStepResourceRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.TopicRepository;
import com.skillsprint.service.calendar.CalendarService;
import com.skillsprint.service.learningstructure.LearningStructureService;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.points.PointService;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
public class RoadmapService {

    static int RESOURCE_TITLE_LENGTH = 120;
    static int RESOURCE_CONTENT_LENGTH = 1_200;
    static int REPORT_ROADMAP_TARGET_MAX_STEPS = 15;
    static int REPORT_ROADMAP_HARD_MAX_STEPS = 20;
    static int GENERAL_ROADMAP_HARD_MAX_STEPS = 30;
    static int TOTAL_RESOURCE_HARD_MAX = 60;
    static int GROUPED_STEP_SUMMARY_LENGTH = 2_400;
    static int GROUPED_STEP_LIST_LIMIT = 12;
    static int GROUPED_STEP_MAX_MINUTES = 120;
    static int MAX_RESOURCE_STEPS_WITH_SEARCH = 20;

    static Set<String> RAW_FRAGMENT_TITLES = Set.of(
            "tong quan",
            "van de",
            "cach sua",
            "file lien quan",
            "thay doi chinh",
            "fallback khi ai loi",
            "prompt gemini",
            "gemini request config",
            "tests da them cap nhat",
            "build / test result",
            "build test result",
            "ket luan",
            "summary",
            "affected area",
            "root cause",
            "impact",
            "fix implemented",
            "expected behavior",
            "tests added / updated",
            "tests added/updated",
            "verification",
            "final result",
            "selected slot",
            "selected slots"
    );

    StudyWorkspaceRepository workspaceRepository;
    LearningStructureVersionRepository structureVersionRepository;
    TopicRepository topicRepository;
    RoadmapRepository roadmapRepository;
    RoadmapStepRepository roadmapStepRepository;
    RoadmapStepResourceRepository roadmapStepResourceRepository;
    RoadmapMapper roadmapMapper;
    QuotaService quotaService;
    PointService pointService;
    CalendarService calendarService;
    com.skillsprint.service.notification.NotificationService notificationService;

    @Transactional
    public RoadmapResponse generate(String userId, UUID workspaceId) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        quotaService.validateCanGenerateAi(userId);
        LearningStructureVersion structureVersion = findLatestConfirmedStructure(workspaceId);
        List<Topic> topics = topicRepository
                .findByStructureVersionStructureVersionIdOrderByChapterSequenceNoAscSequenceNoAsc(
                        structureVersion.getStructureVersionId()
                );

        if (topics.isEmpty()) {
            throw new AppException(ErrorCode.ROADMAP_TOPICS_NOT_READY);
        }

        boolean reportLike = isReportLikeTopics(topics);
        List<RoadmapStepDraft> stepDrafts = createStepDrafts(workspace, topics, reportLike);
        validateStepDrafts(stepDrafts, reportLike);

        Roadmap roadmap = createRoadmap(workspace, structureVersion, stepDrafts.size());
        Roadmap savedRoadmap = roadmapRepository.saveAndFlush(roadmap);
        List<RoadmapStep> steps = roadmapStepRepository.saveAllAndFlush(createSteps(savedRoadmap, workspace, stepDrafts));

        savedRoadmap.setCurrentStep(steps.get(0));
        Roadmap updatedRoadmap = roadmapRepository.saveAndFlush(savedRoadmap);

        List<RoadmapStepResource> resources = roadmapStepResourceRepository
                .saveAllAndFlush(createResources(steps));

        notificationService.notifyRoadmapReady(workspace.getUser(), workspace);
        int unlockedStepLimit = quotaService.getUnlockedRoadmapStepLimit(userId);
        return roadmapMapper.toResponse(updatedRoadmap, steps, resources, unlockedStepLimit, false);
    }

    @Transactional(readOnly = true)
    public RoadmapResponse getCurrent(String userId, UUID workspaceId) {
        findOwnedWorkspace(userId, workspaceId);
        Roadmap roadmap = roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.ROADMAP_NOT_FOUND));
        List<RoadmapStep> steps = roadmapStepRepository
                .findByRoadmapRoadmapIdOrderBySequenceNoAsc(roadmap.getRoadmapId());
        List<RoadmapStepResource> resources = steps.stream()
                .flatMap(step -> roadmapStepResourceRepository
                        .findByStepStepIdOrderBySequenceNoAsc(step.getStepId())
                        .stream())
                .toList();

        int unlockedStepLimit = quotaService.getUnlockedRoadmapStepLimit(userId);
        boolean isRewardClaimed = pointService.hasRoadmapCompletedPoints(userId, roadmap.getRoadmapId());
        return roadmapMapper.toResponse(roadmap, steps, resources, unlockedStepLimit, isRewardClaimed);
    }


    @Transactional
    public void claimReward(String userId, UUID workspaceId) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        Roadmap roadmap = roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.ROADMAP_NOT_FOUND));

        // Heal roadmaps left ACTIVE by the old completion gate before rejecting the claim. This
        // reconciles from the persisted step tasks using the shared eligibility policy, so a stuck
        // ADMIN_DEFAULT roadmap whose tasks are already all COMPLETED is promoted here. It is
        // idempotent and leaves normal plans (with an unsatisfied study-time requirement) untouched.
        if (roadmap.getStatus() != RoadmapStatus.COMPLETED) {
            calendarService.reconcileRoadmapCompletion(roadmap);
        }

        if (roadmap.getStatus() != RoadmapStatus.COMPLETED) {
            throw new AppException(ErrorCode.ROADMAP_NOT_FOUND, "Lộ trình học chưa được hoàn thành.");
        }

        List<RoadmapStep> completedSteps = roadmapStepRepository.findByRoadmapRoadmapIdAndStatus(
                roadmap.getRoadmapId(),
                RoadmapStepStatus.COMPLETED
        );

        boolean allStepsEarnedPoints = completedSteps.size() == roadmap.getTotalSteps()
                && completedSteps.stream().allMatch(step -> pointService.hasRoadmapStepCompletedPoints(
                        userId,
                        step.getStepId()
                ));

        if (!allStepsEarnedPoints) {
            throw new AppException(ErrorCode.ROADMAP_NOT_FOUND, "Chưa đủ điều kiện nhận phần thưởng (chưa hoàn thành đủ thời gian học).");
        }

        pointService.awardRoadmapCompleted(workspace.getUser(), workspace, roadmap.getRoadmapId());
    }

    private StudyWorkspace findOwnedWorkspace(String userId, UUID workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private LearningStructureVersion findLatestConfirmedStructure(UUID workspaceId) {
        return structureVersionRepository
                .findByWorkspaceWorkspaceIdAndStatus(workspaceId, LearningStructureStatus.CONFIRMED)
                .stream()
                .max(Comparator.comparing(LearningStructureVersion::getVersionNo))
                .orElseThrow(() -> new AppException(ErrorCode.ROADMAP_CONFIRMED_STRUCTURE_REQUIRED));
    }

    private Roadmap createRoadmap(
            StudyWorkspace workspace,
            LearningStructureVersion structureVersion,
            int totalSteps
    ) {
        Roadmap roadmap = new Roadmap();
        roadmap.setWorkspace(workspace);
        roadmap.setStructureVersion(structureVersion);
        roadmap.setUser(workspace.getUser());
        roadmap.setTitle("Roadmap học " + workspace.getName());
        roadmap.setDescription("Roadmap được tạo từ learning structure đã xác nhận");
        roadmap.setTotalSteps(totalSteps);
        roadmap.setCompletedSteps(0);
        roadmap.setProgressPercent(BigDecimal.ZERO);
        roadmap.setVersionNo(nextRoadmapVersion(workspace.getWorkspaceId()));
        roadmap.setStatus(RoadmapStatus.ACTIVE);
        return roadmap;
    }

    private int nextRoadmapVersion(UUID workspaceId) {
        return roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId)
                .map(roadmap -> roadmap.getVersionNo() + 1)
                .orElse(1);
    }

    private List<RoadmapStepDraft> createStepDrafts(
            StudyWorkspace workspace,
            List<Topic> topics,
            boolean reportLike
    ) {
        if (reportLike && shouldCompactReportTopics(topics)) {
            return ensureUniqueStepDraftTitles(compactReportTopics(workspace, topics));
        }
        if (!reportLike && topics.size() > GENERAL_ROADMAP_HARD_MAX_STEPS) {
            return ensureUniqueStepDraftTitles(compactGeneralTopics(topics));
        }
        return ensureUniqueStepDraftTitles(topics.stream().map(this::topicToDraft).toList());
    }

    private boolean shouldCompactReportTopics(List<Topic> topics) {
        return topics.size() > REPORT_ROADMAP_TARGET_MAX_STEPS
                || topics.stream().anyMatch(topic -> isGenericOrTinyStepTitle(topic.getTitle()));
    }

    private List<RoadmapStep> createSteps(Roadmap roadmap, StudyWorkspace workspace, List<RoadmapStepDraft> drafts) {
        List<RoadmapStep> steps = new ArrayList<>();

        for (int i = 0; i < drafts.size(); i++) {
            RoadmapStepDraft draft = drafts.get(i);
            RoadmapStep step = new RoadmapStep();
            step.setRoadmap(roadmap);
            step.setWorkspace(workspace);
            step.setChapter(draft.chapter());
            step.setTopic(draft.topic());
            step.setTitle(draft.title());
            step.setSubtitle(draft.subtitle());
            step.setSummary(draft.summary());
            step.setWhatToLearn(draft.whatToLearn());
            step.setKeyConcepts(draft.keyConcepts());
            step.setLearningOutcomes(draft.learningOutcomes());
            step.setRecommendedFocus(draft.recommendedFocus());
            step.setDifficulty(draft.difficulty());
            step.setEstimatedMinutes(draft.estimatedMinutes());
            step.setEstimatedStudyTime(toEstimatedStudyTime(draft.estimatedMinutes()));
            step.setSequenceNo(i + 1);
            step.setStatus(i == 0 ? RoadmapStepStatus.CURRENT : RoadmapStepStatus.UPCOMING);
            steps.add(step);
        }

        return steps;
    }

    private List<RoadmapStepResource> createResources(List<RoadmapStep> steps) {
        List<RoadmapStepResource> resources = new ArrayList<>();
        Set<String> resourceKeys = new LinkedHashSet<>();
        boolean includeSearchResource = steps.size() <= MAX_RESOURCE_STEPS_WITH_SEARCH;
        for (RoadmapStep step : steps) {
            List<RoadmapStepResource> candidates = new ArrayList<>();
            if (hasUsefulDocumentContent(step)) {
                candidates.add(createDocumentSectionResource(step));
            }
            if (includeSearchResource && !isGenericOrTinyStepTitle(step.getTitle())) {
                candidates.add(createYoutubeSearchResource(step));
            }
            if (!isGenericOrTinyStepTitle(step.getTitle())) {
                candidates.add(createPracticePromptResource(step));
            }

            int sequenceNo = 1;
            for (RoadmapStepResource candidate : candidates) {
                String resourceKey = resourceKey(candidate);
                if (resourceKeys.add(resourceKey)) {
                    candidate.setSequenceNo(sequenceNo++);
                    resources.add(candidate);
                    if (resources.size() >= TOTAL_RESOURCE_HARD_MAX) {
                        return resources;
                    }
                }
            }
        }
        return resources;
    }

    private RoadmapStepResource createDocumentSectionResource(RoadmapStep step) {
        RoadmapStepResource resource = new RoadmapStepResource();
        resource.setStep(step);
        resource.setTitle("Tài liệu gốc liên quan");
        resource.setPlatform(ResourcePlatform.SkillSprint);
        resource.setResourceType(ResourceType.DOCUMENT_SECTION);
        resource.setContent(buildDocumentSectionContent(step));
        resource.setReason("Đoạn nội dung được dùng làm nền để tạo bài học này");
        resource.setAiRecommended(false);
        resource.setSequenceNo(1);
        return resource;
    }

    private RoadmapStepResource createYoutubeSearchResource(RoadmapStep step) {
        String searchQuery = step.getTitle() + " tutorial";
        RoadmapStepResource resource = new RoadmapStepResource();
        resource.setStep(step);
        resource.setTitle(truncate("Tìm video: " + step.getTitle(), RESOURCE_TITLE_LENGTH));
        resource.setPlatform(ResourcePlatform.YouTube);
        resource.setResourceType(ResourceType.SEARCH_QUERY);
        resource.setSearchQuery(searchQuery);
        resource.setUrl("https://www.youtube.com/results?search_query=" + encode(searchQuery));
        resource.setReason("Gợi ý video để học thêm về " + step.getTitle());
        resource.setAiRecommended(false);
        resource.setSequenceNo(2);
        return resource;
    }

    private RoadmapStepResource createPracticePromptResource(RoadmapStep step) {
        RoadmapStepResource resource = new RoadmapStepResource();
        resource.setStep(step);
        resource.setTitle("Bài tập thực hành");
        resource.setPlatform(ResourcePlatform.SkillSprint);
        resource.setResourceType(ResourceType.PRACTICE_PROMPT);
        resource.setContent(buildPracticePrompt(step));
        resource.setReason("Giúp bạn kiểm tra nhanh mức độ hiểu bài sau khi học xong step này");
        resource.setAiRecommended(false);
        resource.setSequenceNo(3);
        return resource;
    }

    private String buildDocumentSectionContent(RoadmapStep step) {
        List<String> parts = new ArrayList<>();
        if (step.getSummary() != null && !step.getSummary().isBlank()) {
            parts.add(step.getSummary());
        }
        if (step.getWhatToLearn() != null && !step.getWhatToLearn().isEmpty()) {
            parts.add("Cần học: " + String.join("; ", step.getWhatToLearn()));
        }
        if (step.getKeyConcepts() != null && !step.getKeyConcepts().isEmpty()) {
            parts.add("Khái niệm chính: " + String.join(", ", step.getKeyConcepts()));
        }

        String content = parts.isEmpty()
                ? "Đọc lại phần tài liệu liên quan tới: " + step.getTitle()
                : String.join(System.lineSeparator(), parts);
        return truncate(content, RESOURCE_CONTENT_LENGTH);
    }

    private String buildPracticePrompt(RoadmapStep step) {
        StringBuilder prompt = new StringBuilder()
                .append("Sau khi học xong \"")
                .append(step.getTitle())
                .append("\", hãy tự làm một bài luyện tập ngắn:")
                .append(System.lineSeparator())
                .append("- Tóm tắt lại nội dung chính bằng 3-5 gạch đầu dòng.")
                .append(System.lineSeparator())
                .append("- Giải thích lại các khái niệm chính bằng lời của bạn.");

        if (step.getLearningOutcomes() != null && !step.getLearningOutcomes().isEmpty()) {
            prompt.append(System.lineSeparator())
                    .append("- Kiểm tra kết quả: ")
                    .append(String.join("; ", step.getLearningOutcomes()));
        }
        if (step.getRecommendedFocus() != null && !step.getRecommendedFocus().isEmpty()) {
            prompt.append(System.lineSeparator())
                    .append("- Tập trung vào: ")
                    .append(String.join("; ", step.getRecommendedFocus()));
        }

        return truncate(prompt.toString(), RESOURCE_CONTENT_LENGTH);
    }

    private RoadmapStepDraft topicToDraft(Topic topic) {
        return new RoadmapStepDraft(
                topic.getChapter(),
                topic,
                topic.getTitle(),
                topic.getChapter() == null ? null : topic.getChapter().getTitle(),
                topic.getSummaryContent(),
                safeList(topic.getWhatToLearn()),
                safeList(topic.getKeyConcepts()),
                safeList(topic.getLearningOutcomes()),
                safeList(topic.getRecommendedFocus()),
                topic.getDifficulty(),
                topic.getEstimatedMinutes()
        );
    }

    private List<RoadmapStepDraft> compactReportTopics(StudyWorkspace workspace, List<Topic> topics) {
        int groupSize = Math.max(1, (int) Math.ceil((double) topics.size() / REPORT_ROADMAP_TARGET_MAX_STEPS));
        List<ReportTopicGroup> groups;
        do {
            groups = buildReportTopicGroups(topics, groupSize);
            groupSize++;
        } while (groups.size() > REPORT_ROADMAP_TARGET_MAX_STEPS && groupSize <= topics.size());

        return groups.stream()
                .map(group -> groupToReportDraft(workspace, group))
                .toList();
    }

    private List<ReportTopicGroup> buildReportTopicGroups(List<Topic> topics, int groupSize) {
        Map<ReportRoadmapPhase, List<Topic>> topicsByPhase = new EnumMap<>(ReportRoadmapPhase.class);
        for (ReportRoadmapPhase phase : ReportRoadmapPhase.values()) {
            topicsByPhase.put(phase, new ArrayList<>());
        }

        for (Topic topic : topics) {
            topicsByPhase.get(classifyReportRoadmapPhase(topic)).add(topic);
        }

        List<ReportTopicGroup> groups = new ArrayList<>();
        for (ReportRoadmapPhase phase : ReportRoadmapPhase.values()) {
            List<Topic> phaseTopics = topicsByPhase.get(phase);
            if (phaseTopics.isEmpty()) {
                continue;
            }
            for (int i = 0; i < phaseTopics.size(); i += groupSize) {
                groups.add(new ReportTopicGroup(
                        phase,
                        phaseTopics.subList(i, Math.min(i + groupSize, phaseTopics.size()))
                ));
            }
        }
        return groups;
    }

    private RoadmapStepDraft groupToReportDraft(StudyWorkspace workspace, ReportTopicGroup group) {
        List<Topic> topics = group.topics();
        Topic firstTopic = topics.get(0);
        String subject = inferReportSubject(workspace, topics);
        String focus = inferReportFocus(group.phase(), topics, subject);
        return new RoadmapStepDraft(
                firstTopic.getChapter(),
                null,
                buildReportRoadmapTitle(group.phase(), focus, subject),
                group.phase().subtitle(),
                buildGroupedSummary(topics),
                mergeTopicLists(topics, TopicListField.WHAT_TO_LEARN),
                mergeTopicLists(topics, TopicListField.KEY_CONCEPTS),
                mergeTopicLists(topics, TopicListField.LEARNING_OUTCOMES),
                mergeTopicLists(topics, TopicListField.RECOMMENDED_FOCUS),
                maxDifficulty(topics),
                sumEstimatedMinutes(topics)
        );
    }

    private List<RoadmapStepDraft> compactGeneralTopics(List<Topic> topics) {
        int groupSize = Math.max(2, (int) Math.ceil((double) topics.size() / GENERAL_ROADMAP_HARD_MAX_STEPS));
        List<RoadmapStepDraft> drafts = new ArrayList<>();
        for (int i = 0; i < topics.size(); i += groupSize) {
            List<Topic> group = topics.subList(i, Math.min(i + groupSize, topics.size()));
            if (group.size() == 1) {
                drafts.add(topicToDraft(group.get(0)));
                continue;
            }
            Topic firstTopic = group.get(0);
            Topic lastTopic = group.get(group.size() - 1);
            String chapterTitle = firstTopic.getChapter() == null ? "" : firstTopic.getChapter().getTitle();
            drafts.add(new RoadmapStepDraft(
                    firstTopic.getChapter(),
                    null,
                    truncate("H\u1ecdc nh\u00f3m: " + firstTopic.getTitle() + " - " + lastTopic.getTitle(), 120),
                    chapterTitle,
                    buildGroupedSummary(group),
                    mergeTopicLists(group, TopicListField.WHAT_TO_LEARN),
                    mergeTopicLists(group, TopicListField.KEY_CONCEPTS),
                    mergeTopicLists(group, TopicListField.LEARNING_OUTCOMES),
                    mergeTopicLists(group, TopicListField.RECOMMENDED_FOCUS),
                    maxDifficulty(group),
                    sumEstimatedMinutes(group)
            ));
        }
        return drafts;
    }

    private boolean isReportLikeTopics(List<Topic> topics) {
        List<String> titles = topics.stream()
                .map(Topic::getTitle)
                .filter(Objects::nonNull)
                .toList();
        if (LearningStructureService.isReportLikeHeadings(titles)) {
            return true;
        }

        List<String> chapterTitles = topics.stream()
                .map(Topic::getChapter)
                .filter(Objects::nonNull)
                .map(Chapter::getTitle)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (LearningStructureService.isReportLikeHeadings(chapterTitles)) {
            return true;
        }

        long rawLabels = titles.stream()
                .filter(title -> RAW_FRAGMENT_TITLES.contains(normalize(title)))
                .count();
        return rawLabels >= 4 && rawLabels * 2 >= Math.min(topics.size(), 12);
    }

    private ReportRoadmapPhase classifyReportRoadmapPhase(Topic topic) {
        String text = normalize(String.join(" ",
                safeText(topic.getTitle()),
                topic.getChapter() == null ? "" : safeText(topic.getChapter().getTitle()),
                safeText(topic.getSummaryContent()),
                String.join(" ", safeList(topic.getKeyConcepts())),
                String.join(" ", safeList(topic.getRecommendedFocus()))
        ));

        if (containsAny(text, "test", "kiem thu", "verification", "verify", "xac minh", "qa", "validation", "build")) {
            return ReportRoadmapPhase.VALIDATION;
        }
        if (containsAny(text, "result", "ket qua", "conclusion", "ket luan", "outcome", "final")) {
            return ReportRoadmapPhase.RESULT;
        }
        if (containsAny(text, "fallback", "prompt gemini", "gemini request", "gemini config", "max_tokens",
                "max tokens", "json cat", "json truncated", "ai calendar output")) {
            return ReportRoadmapPhase.AI_CONTROL;
        }
        if (containsAny(text, "fix", "khac phuc", "cach sua", "solution", "giai phap", "implementation",
                "implemented", "trien khai", "expected behavior", "expected behaviour", "change",
                "thay doi", "file lien quan", "files changed", "parse", "planner", "validate")) {
            return ReportRoadmapPhase.IMPLEMENTATION;
        }
        if (containsAny(text, "root cause", "nguyen nhan", "why", "vi sao", "impact", "tac dong",
                "analysis", "phan tich", "van de", "loi ", "bug", "availability", "selected slot")) {
            return ReportRoadmapPhase.PROBLEM;
        }
        return ReportRoadmapPhase.OVERVIEW;
    }

    private String inferReportSubject(StudyWorkspace workspace, List<Topic> topics) {
        String combined = normalize(collectTopicText(topics));
        if (containsAny(combined, "calendar", "lich")) {
            return "Calendar AI";
        }
        if (workspace.getName() != null && !workspace.getName().isBlank()) {
            return workspace.getName().trim();
        }
        return "b\u00e1o c\u00e1o k\u1ef9 thu\u1eadt";
    }

    private String inferReportFocus(ReportRoadmapPhase phase, List<Topic> topics, String subject) {
        String text = normalize(collectTopicText(topics));
        if (containsAny(text, "selected slot", "selected slots", "slot dau tien", "first slot")) {
            return "l\u1ed7i ch\u1ec9 d\u00f9ng slot \u0111\u1ea7u ti\u00ean";
        }
        if (containsAny(text, "ngoai availability", "outside availability")) {
            return "nguy\u00ean nh\u00e2n sinh l\u1ecbch ngo\u00e0i availability";
        }
        if (containsAny(text, "availability")) {
            return "logic availability";
        }
        if (containsAny(text, "time window", "time windows", "parse")) {
            return "logic \u0111\u1ecdc to\u00e0n b\u1ed9 time windows";
        }
        if (containsAny(text, "rule based", "rule-based", "planner")) {
            return "rule-based planner";
        }
        if (containsAny(text, "gemini", "max_tokens", "max tokens", "json cat", "json truncated")) {
            return "Gemini MAX_TOKENS v\u00e0 JSON c\u1ee5t";
        }
        if (containsAny(text, "fallback")) {
            return "fallback an to\u00e0n";
        }
        if (containsAny(text, "regression", "1 slot", "2 slot", "3 slot")) {
            return "c\u00e1c case slot v\u00e0 regression";
        }
        if (containsAny(text, "build")) {
            return "build v\u00e0 regression tests";
        }
        return switch (phase) {
            case OVERVIEW -> subject;
            case PROBLEM -> "v\u1ea5n \u0111\u1ec1 v\u00e0 nguy\u00ean nh\u00e2n";
            case IMPLEMENTATION -> "thay \u0111\u1ed5i ch\u00ednh trong m\u00e3";
            case AI_CONTROL -> "AI validation v\u00e0 fallback";
            case VALIDATION -> "ki\u1ec3m th\u1eed v\u00e0 x\u00e1c minh";
            case RESULT -> "k\u1ebft qu\u1ea3 s\u1eeda l\u1ed7i";
        };
    }

    private String buildReportRoadmapTitle(ReportRoadmapPhase phase, String focus, String subject) {
        return switch (phase) {
            case OVERVIEW -> "Hi\u1ec3u t\u1ed5ng quan " + subject;
            case PROBLEM -> "Ph\u00e2n t\u00edch " + focus;
            case IMPLEMENTATION -> "Tri\u1ec3n khai " + focus;
            case AI_CONTROL -> "Ki\u1ec3m so\u00e1t " + focus;
            case VALIDATION -> "Ki\u1ec3m th\u1eed " + focus;
            case RESULT -> "T\u1ed5ng k\u1ebft " + focus;
        };
    }

    private String buildGroupedSummary(List<Topic> topics) {
        List<String> parts = new ArrayList<>();
        List<String> titles = topics.stream()
                .map(Topic::getTitle)
                .filter(Objects::nonNull)
                .filter(title -> !isGenericOrTinyStepTitle(title))
                .distinct()
                .limit(6)
                .toList();
        if (!titles.isEmpty()) {
            parts.add("C\u00e1c ph\u1ea7n \u0111\u01b0\u1ee3c g\u1ed9p: " + String.join("; ", titles) + ".");
        }
        topics.stream()
                .map(Topic::getSummaryContent)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(summary -> !summary.isBlank())
                .distinct()
                .limit(4)
                .forEach(parts::add);

        String summary = parts.isEmpty()
                ? "\u0110\u1ecdc c\u00e1c ph\u1ea7n li\u00ean quan trong learning structure \u0111\u00e3 x\u00e1c nh\u1eadn."
                : String.join(System.lineSeparator(), parts);
        return truncate(summary, GROUPED_STEP_SUMMARY_LENGTH);
    }

    private List<String> mergeTopicLists(List<Topic> topics, TopicListField field) {
        Map<String, String> valuesByNormalizedText = new LinkedHashMap<>();
        for (Topic topic : topics) {
            List<String> values = switch (field) {
                case WHAT_TO_LEARN -> safeList(topic.getWhatToLearn());
                case KEY_CONCEPTS -> safeList(topic.getKeyConcepts());
                case LEARNING_OUTCOMES -> safeList(topic.getLearningOutcomes());
                case RECOMMENDED_FOCUS -> safeList(topic.getRecommendedFocus());
            };
            for (String value : values) {
                String cleaned = value == null ? "" : value.trim();
                String normalized = normalize(cleaned);
                if (!cleaned.isBlank()
                        && !isGenericOrTinyStepTitle(cleaned)
                        && !valuesByNormalizedText.containsKey(normalized)) {
                    valuesByNormalizedText.put(normalized, truncate(cleaned, 140));
                }
                if (valuesByNormalizedText.size() >= GROUPED_STEP_LIST_LIMIT) {
                    return new ArrayList<>(valuesByNormalizedText.values());
                }
            }
        }
        return new ArrayList<>(valuesByNormalizedText.values());
    }

    private DifficultyLevel maxDifficulty(List<Topic> topics) {
        return topics.stream()
                .map(Topic::getDifficulty)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(null);
    }

    private Integer sumEstimatedMinutes(List<Topic> topics) {
        int total = topics.stream()
                .map(Topic::getEstimatedMinutes)
                .filter(Objects::nonNull)
                .filter(minutes -> minutes > 0)
                .mapToInt(Integer::intValue)
                .sum();
        if (total <= 0) {
            return null;
        }
        return Math.min(GROUPED_STEP_MAX_MINUTES, total);
    }

    private void validateStepDrafts(List<RoadmapStepDraft> drafts, boolean reportLike) {
        if (drafts.isEmpty()) {
            throw new AppException(ErrorCode.ROADMAP_GENERATION_FAILED, "Roadmap kh\u00f4ng c\u00f3 milestone h\u1ee3p l\u1ec7.");
        }
        if (reportLike && drafts.size() > REPORT_ROADMAP_HARD_MAX_STEPS) {
            throw new AppException(ErrorCode.ROADMAP_GENERATION_FAILED, "Roadmap b\u00e1o c\u00e1o v\u01b0\u1ee3t gi\u1edbi h\u1ea1n milestone.");
        }
        if (!reportLike && drafts.size() > GENERAL_ROADMAP_HARD_MAX_STEPS) {
            throw new AppException(ErrorCode.ROADMAP_GENERATION_FAILED, "Roadmap v\u01b0\u1ee3t gi\u1edbi h\u1ea1n milestone.");
        }

        Set<String> normalizedTitles = new LinkedHashSet<>();
        for (RoadmapStepDraft draft : drafts) {
            if (reportLike && isGenericOrTinyStepTitle(draft.title())) {
                throw new AppException(ErrorCode.ROADMAP_GENERATION_FAILED, "Roadmap c\u00f3 milestone qu\u00e1 chung chung.");
            }
            String normalizedTitle = normalize(draft.title());
            if (!normalizedTitles.add(normalizedTitle)) {
                throw new AppException(ErrorCode.ROADMAP_GENERATION_FAILED, "Roadmap c\u00f3 milestone b\u1ecb tr\u00f9ng ti\u00eau \u0111\u1ec1.");
            }
        }
    }

    private List<RoadmapStepDraft> ensureUniqueStepDraftTitles(List<RoadmapStepDraft> drafts) {
        Map<String, Integer> titleCounts = new LinkedHashMap<>();
        List<RoadmapStepDraft> uniqueDrafts = new ArrayList<>();
        for (RoadmapStepDraft draft : drafts) {
            String normalizedTitle = normalize(draft.title());
            int count = titleCounts.getOrDefault(normalizedTitle, 0) + 1;
            titleCounts.put(normalizedTitle, count);
            String title = count == 1 ? draft.title() : draft.title() + " - ph\u1ea7n " + count;
            uniqueDrafts.add(new RoadmapStepDraft(
                    draft.chapter(),
                    draft.topic(),
                    title,
                    draft.subtitle(),
                    draft.summary(),
                    draft.whatToLearn(),
                    draft.keyConcepts(),
                    draft.learningOutcomes(),
                    draft.recommendedFocus(),
                    draft.difficulty(),
                    draft.estimatedMinutes()
            ));
        }
        return uniqueDrafts;
    }

    private boolean hasUsefulDocumentContent(RoadmapStep step) {
        return (step.getSummary() != null && !step.getSummary().isBlank())
                || (step.getWhatToLearn() != null && !step.getWhatToLearn().isEmpty())
                || (step.getKeyConcepts() != null && !step.getKeyConcepts().isEmpty());
    }

    private String resourceKey(RoadmapStepResource resource) {
        return String.join("|",
                resource.getResourceType() == null ? "" : resource.getResourceType().name(),
                normalize(resource.getTitle()),
                normalize(resource.getUrl()),
                normalize(resource.getSearchQuery()),
                normalize(resource.getContent())
        );
    }

    private boolean isGenericOrTinyStepTitle(String title) {
        String normalized = normalize(title);
        if (normalized.isBlank() || normalized.length() < 3) {
            return true;
        }
        if (normalized.matches("\\d{1,4}")
                || normalized.matches("\\d{1,2}:\\d{2}(?::\\d{2})?")
                || normalized.matches("\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[-\u2013\u2014]\\s*\\d{1,2}:\\d{2}(?::\\d{2})?")) {
            return true;
        }
        return RAW_FRAGMENT_TITLES.contains(normalized);
    }

    private String collectTopicText(List<Topic> topics) {
        return topics.stream()
                .map(topic -> String.join(" ",
                        safeText(topic.getTitle()),
                        topic.getChapter() == null ? "" : safeText(topic.getChapter().getTitle()),
                        safeText(topic.getSummaryContent()),
                        String.join(" ", safeList(topic.getKeyConcepts())),
                        String.join(" ", safeList(topic.getRecommendedFocus()))
                ))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return LearningStructureService.normalizeForMatch(value)
                .replaceAll("[^a-z0-9_:/\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String toEstimatedStudyTime(Integer estimatedMinutes) {
        if (estimatedMinutes == null || estimatedMinutes <= 0) {
            return null;
        }
        return estimatedMinutes + " phút";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }

    private List<String> safeList(List<String> value) {
        return value == null ? List.of() : value;
    }

    private enum ReportRoadmapPhase {
        OVERVIEW("T\u1ed5ng quan v\u00e0 b\u1ed1i c\u1ea3nh"),
        PROBLEM("V\u1ea5n \u0111\u1ec1 v\u00e0 nguy\u00ean nh\u00e2n"),
        IMPLEMENTATION("C\u00e1ch s\u1eeda v\u00e0 tri\u1ec3n khai"),
        AI_CONTROL("AI validation v\u00e0 fallback"),
        VALIDATION("Ki\u1ec3m th\u1eed v\u00e0 x\u00e1c minh"),
        RESULT("K\u1ebft qu\u1ea3 cu\u1ed1i c\u00f9ng");

        private final String subtitle;

        ReportRoadmapPhase(String subtitle) {
            this.subtitle = subtitle;
        }

        String subtitle() {
            return subtitle;
        }
    }

    private enum TopicListField {
        WHAT_TO_LEARN,
        KEY_CONCEPTS,
        LEARNING_OUTCOMES,
        RECOMMENDED_FOCUS
    }

    private record ReportTopicGroup(ReportRoadmapPhase phase, List<Topic> topics) {
    }

    private record RoadmapStepDraft(
            Chapter chapter,
            Topic topic,
            String title,
            String subtitle,
            String summary,
            List<String> whatToLearn,
            List<String> keyConcepts,
            List<String> learningOutcomes,
            List<String> recommendedFocus,
            DifficultyLevel difficulty,
            Integer estimatedMinutes
    ) {
    }
}
