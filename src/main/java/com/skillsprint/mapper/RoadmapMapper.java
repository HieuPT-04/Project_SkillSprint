package com.skillsprint.mapper;

import com.skillsprint.dto.response.roadmap.RoadmapResourceResponse;
import com.skillsprint.dto.response.roadmap.RoadmapResponse;
import com.skillsprint.dto.response.roadmap.RoadmapStepResponse;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.RoadmapStepResource;
import com.skillsprint.enums.plan.ServicePlanType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RoadmapMapper {

    static int SUMMARY_LENGTH = 700;
    static int LIST_ITEM_LENGTH = 120;
    static int MAX_LIST_ITEMS = 6;
    static int RESOURCE_TEXT_LENGTH = 500;
    static int RESOURCE_REASON_LENGTH = 220;
    static int RESOURCE_TITLE_LENGTH = 120;
    static int RESOURCE_QUERY_LENGTH = 180;
    static String LOCK_REASON = "Vui lòng nâng cấp gói để học tiếp.";

    public RoadmapResponse toResponse(
            Roadmap roadmap,
            List<RoadmapStep> steps,
            List<RoadmapStepResource> resources
    ) {
        return toResponse(roadmap, steps, resources, Integer.MAX_VALUE, false);
    }

    public RoadmapResponse toResponse(
            Roadmap roadmap,
            List<RoadmapStep> steps,
            List<RoadmapStepResource> resources,
            int unlockedStepLimit,
            boolean isRewardClaimed
    ) {
        Map<UUID, List<RoadmapStepResource>> resourcesByStepId = resources.stream()
                .collect(Collectors.groupingBy(resource -> resource.getStep().getStepId()));

        return RoadmapResponse.builder()
                .roadmapId(roadmap.getRoadmapId())
                .workspaceId(roadmap.getWorkspace().getWorkspaceId())
                .structureVersionId(roadmap.getStructureVersion().getStructureVersionId())
                .currentStepId(roadmap.getCurrentStep() == null ? null : roadmap.getCurrentStep().getStepId())
                .title(roadmap.getTitle())
                .description(truncate(roadmap.getDescription(), SUMMARY_LENGTH))
                .totalSteps(roadmap.getTotalSteps())
                .completedSteps(roadmap.getCompletedSteps())
                .progressPercent(roadmap.getProgressPercent())
                .versionNo(roadmap.getVersionNo())
                .status(roadmap.getStatus())
                .isRewardClaimed(isRewardClaimed)
                .generatedAt(roadmap.getGeneratedAt())
                .updatedAt(roadmap.getUpdatedAt())
                .steps(steps.stream()
                        .map(step -> toStepResponse(
                                step,
                                resourcesByStepId.getOrDefault(step.getStepId(), List.of()),
                                unlockedStepLimit
                        ))
                        .toList())
                .build();

    }

    private RoadmapStepResponse toStepResponse(
            RoadmapStep step,
            List<RoadmapStepResource> resources,
            int unlockedStepLimit
    ) {
        boolean locked = isLocked(step, unlockedStepLimit);
        return RoadmapStepResponse.builder()
                .stepId(step.getStepId())
                .chapterId(step.getChapter() == null ? null : step.getChapter().getChapterId())
                .topicId(step.getTopic() == null ? null : step.getTopic().getTopicId())
                .title(truncate(step.getTitle(), RESOURCE_TITLE_LENGTH))
                .subtitle(truncate(step.getSubtitle(), RESOURCE_TITLE_LENGTH))
                .summary(truncate(step.getSummary(), SUMMARY_LENGTH))
                .whatToLearn(compactList(step.getWhatToLearn()))
                .keyConcepts(compactList(step.getKeyConcepts()))
                .learningOutcomes(compactList(step.getLearningOutcomes()))
                .recommendedFocus(compactList(step.getRecommendedFocus()))
                .difficulty(step.getDifficulty())
                .estimatedStudyTime(step.getEstimatedStudyTime())
                .estimatedMinutes(step.getEstimatedMinutes())
                .sequenceNo(step.getSequenceNo())
                .status(step.getStatus())
                .completedAt(step.getCompletedAt())
                .locked(locked)
                .lockReason(locked ? LOCK_REASON : null)
                .requiredPlan(locked ? ServicePlanType.SKILL_BUILDER : null)
                .resources(resources.stream().map(this::toResourceResponse).toList())
                .build();
    }

    public RoadmapResourceResponse toResourceResponse(RoadmapStepResource resource) {
        return RoadmapResourceResponse.builder()
                .resourceId(resource.getResourceId())
                .title(truncate(resource.getTitle(), RESOURCE_TITLE_LENGTH))
                .platform(resource.getPlatform())
                .resourceType(resource.getResourceType())
                .searchQuery(truncate(resource.getSearchQuery(), RESOURCE_QUERY_LENGTH))
                .content(truncate(resource.getContent(), RESOURCE_TEXT_LENGTH))
                .url(resource.getUrl())
                .reason(truncate(resource.getReason(), RESOURCE_REASON_LENGTH))
                .aiRecommended(resource.isAiRecommended())
                .sequenceNo(resource.getSequenceNo())
                .createdAt(resource.getCreatedAt())
                .build();
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

    private boolean isLocked(RoadmapStep step, int unlockedStepLimit) {
        return step.getSequenceNo() != null && step.getSequenceNo() > unlockedStepLimit;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }
}
