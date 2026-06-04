package com.skillsprint.service.roadmap;

import com.skillsprint.dto.response.roadmap.RoadmapResponse;
import com.skillsprint.entity.LearningStructureVersion;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.RoadmapStepResource;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.Topic;
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
import com.skillsprint.service.subscription.QuotaService;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    StudyWorkspaceRepository workspaceRepository;
    LearningStructureVersionRepository structureVersionRepository;
    TopicRepository topicRepository;
    RoadmapRepository roadmapRepository;
    RoadmapStepRepository roadmapStepRepository;
    RoadmapStepResourceRepository roadmapStepResourceRepository;
    RoadmapMapper roadmapMapper;
    QuotaService quotaService;
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

        Roadmap roadmap = createRoadmap(workspace, structureVersion, topics.size());
        Roadmap savedRoadmap = roadmapRepository.saveAndFlush(roadmap);
        List<RoadmapStep> steps = roadmapStepRepository.saveAllAndFlush(createSteps(savedRoadmap, workspace, topics));

        savedRoadmap.setCurrentStep(steps.get(0));
        Roadmap updatedRoadmap = roadmapRepository.saveAndFlush(savedRoadmap);

        List<RoadmapStepResource> resources = roadmapStepResourceRepository
                .saveAllAndFlush(createResources(steps));

        notificationService.notifyRoadmapReady(workspace.getUser(), workspace);
        int unlockedStepLimit = quotaService.getUnlockedRoadmapStepLimit(userId);
        return roadmapMapper.toResponse(updatedRoadmap, steps, resources, unlockedStepLimit);
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
        return roadmapMapper.toResponse(roadmap, steps, resources, unlockedStepLimit);
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

    private List<RoadmapStep> createSteps(Roadmap roadmap, StudyWorkspace workspace, List<Topic> topics) {
        List<RoadmapStep> steps = new ArrayList<>();

        for (int i = 0; i < topics.size(); i++) {
            Topic topic = topics.get(i);
            RoadmapStep step = new RoadmapStep();
            step.setRoadmap(roadmap);
            step.setWorkspace(workspace);
            step.setChapter(topic.getChapter());
            step.setTopic(topic);
            step.setTitle(topic.getTitle());
            step.setSubtitle(topic.getChapter().getTitle());
            step.setSummary(topic.getSummaryContent());
            step.setWhatToLearn(safeList(topic.getWhatToLearn()));
            step.setKeyConcepts(safeList(topic.getKeyConcepts()));
            step.setLearningOutcomes(safeList(topic.getLearningOutcomes()));
            step.setRecommendedFocus(safeList(topic.getRecommendedFocus()));
            step.setDifficulty(topic.getDifficulty());
            step.setEstimatedMinutes(topic.getEstimatedMinutes());
            step.setEstimatedStudyTime(toEstimatedStudyTime(topic.getEstimatedMinutes()));
            step.setSequenceNo(i + 1);
            step.setStatus(i == 0 ? RoadmapStepStatus.CURRENT : RoadmapStepStatus.UPCOMING);
            steps.add(step);
        }

        return steps;
    }

    private List<RoadmapStepResource> createResources(List<RoadmapStep> steps) {
        List<RoadmapStepResource> resources = new ArrayList<>();
        for (RoadmapStep step : steps) {
            resources.add(createDocumentSectionResource(step));
            resources.add(createYoutubeSearchResource(step));
            resources.add(createPracticePromptResource(step));
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
}
