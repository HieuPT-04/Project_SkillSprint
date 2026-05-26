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

    StudyWorkspaceRepository workspaceRepository;
    LearningStructureVersionRepository structureVersionRepository;
    TopicRepository topicRepository;
    RoadmapRepository roadmapRepository;
    RoadmapStepRepository roadmapStepRepository;
    RoadmapStepResourceRepository roadmapStepResourceRepository;
    RoadmapMapper roadmapMapper;

    @Transactional
    public RoadmapResponse generate(String userId, UUID workspaceId) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
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

        return roadmapMapper.toResponse(updatedRoadmap, steps, resources);
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

        return roadmapMapper.toResponse(roadmap, steps, resources);
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
        return steps.stream()
                .map(this::createYoutubeSearchResource)
                .toList();
    }

    private RoadmapStepResource createYoutubeSearchResource(RoadmapStep step) {
        String searchQuery = step.getTitle() + " tutorial";
        RoadmapStepResource resource = new RoadmapStepResource();
        resource.setStep(step);
        resource.setTitle("Tìm video: " + step.getTitle());
        resource.setPlatform(ResourcePlatform.YouTube);
        resource.setResourceType(ResourceType.SEARCH_QUERY);
        resource.setSearchQuery(searchQuery);
        resource.setUrl("https://www.youtube.com/results?search_query=" + encode(searchQuery));
        resource.setReason("Gợi ý video để học thêm về " + step.getTitle());
        resource.setAiRecommended(false);
        resource.setSequenceNo(1);
        return resource;
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

    private List<String> safeList(List<String> value) {
        return value == null ? List.of() : value;
    }
}
