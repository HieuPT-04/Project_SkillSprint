package com.skillsprint.dto.response.roadmap;

import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoadmapStepResponse {

    UUID stepId;
    UUID chapterId;
    UUID topicId;
    String title;
    String subtitle;
    String summary;
    List<String> whatToLearn;
    List<String> keyConcepts;
    List<String> learningOutcomes;
    List<String> recommendedFocus;
    DifficultyLevel difficulty;
    String estimatedStudyTime;
    Integer estimatedMinutes;
    Integer sequenceNo;
    RoadmapStepStatus status;
    Instant completedAt;
    List<RoadmapResourceResponse> resources;
}
