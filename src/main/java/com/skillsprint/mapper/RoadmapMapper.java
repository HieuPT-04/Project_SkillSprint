package com.skillsprint.mapper;

import com.skillsprint.dto.response.roadmap.RoadmapResourceResponse;
import com.skillsprint.dto.response.roadmap.RoadmapResponse;
import com.skillsprint.dto.response.roadmap.RoadmapStepResponse;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.RoadmapStepResource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RoadmapMapper {

    public RoadmapResponse toResponse(
            Roadmap roadmap,
            List<RoadmapStep> steps,
            List<RoadmapStepResource> resources
    ) {
        Map<UUID, List<RoadmapStepResource>> resourcesByStepId = resources.stream()
                .collect(Collectors.groupingBy(resource -> resource.getStep().getStepId()));

        return RoadmapResponse.builder()
                .roadmapId(roadmap.getRoadmapId())
                .workspaceId(roadmap.getWorkspace().getWorkspaceId())
                .structureVersionId(roadmap.getStructureVersion().getStructureVersionId())
                .currentStepId(roadmap.getCurrentStep() == null ? null : roadmap.getCurrentStep().getStepId())
                .title(roadmap.getTitle())
                .description(roadmap.getDescription())
                .totalSteps(roadmap.getTotalSteps())
                .completedSteps(roadmap.getCompletedSteps())
                .progressPercent(roadmap.getProgressPercent())
                .versionNo(roadmap.getVersionNo())
                .status(roadmap.getStatus())
                .generatedAt(roadmap.getGeneratedAt())
                .updatedAt(roadmap.getUpdatedAt())
                .steps(steps.stream()
                        .map(step -> toStepResponse(
                                step,
                                resourcesByStepId.getOrDefault(step.getStepId(), List.of())
                        ))
                        .toList())
                .build();
    }

    private RoadmapStepResponse toStepResponse(RoadmapStep step, List<RoadmapStepResource> resources) {
        return RoadmapStepResponse.builder()
                .stepId(step.getStepId())
                .chapterId(step.getChapter() == null ? null : step.getChapter().getChapterId())
                .topicId(step.getTopic() == null ? null : step.getTopic().getTopicId())
                .title(step.getTitle())
                .subtitle(step.getSubtitle())
                .summary(step.getSummary())
                .whatToLearn(step.getWhatToLearn())
                .keyConcepts(step.getKeyConcepts())
                .learningOutcomes(step.getLearningOutcomes())
                .recommendedFocus(step.getRecommendedFocus())
                .difficulty(step.getDifficulty())
                .estimatedStudyTime(step.getEstimatedStudyTime())
                .estimatedMinutes(step.getEstimatedMinutes())
                .sequenceNo(step.getSequenceNo())
                .status(step.getStatus())
                .completedAt(step.getCompletedAt())
                .resources(resources.stream().map(this::toResourceResponse).toList())
                .build();
    }

    public RoadmapResourceResponse toResourceResponse(RoadmapStepResource resource) {
        return RoadmapResourceResponse.builder()
                .resourceId(resource.getResourceId())
                .title(resource.getTitle())
                .platform(resource.getPlatform())
                .resourceType(resource.getResourceType())
                .searchQuery(resource.getSearchQuery())
                .content(resource.getContent())
                .url(resource.getUrl())
                .reason(resource.getReason())
                .aiRecommended(resource.isAiRecommended())
                .sequenceNo(resource.getSequenceNo())
                .createdAt(resource.getCreatedAt())
                .build();
    }
}
