package com.skillsprint.dto.response.roadmap;

import com.skillsprint.enums.roadmap.RoadmapStatus;
import java.math.BigDecimal;
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
public class RoadmapResponse {

    UUID roadmapId;
    UUID workspaceId;
    UUID structureVersionId;
    UUID currentStepId;
    String title;
    String description;
    Integer totalSteps;
    Integer completedSteps;
    BigDecimal progressPercent;
    Integer versionNo;
    RoadmapStatus status;
    Boolean isRewardClaimed;
    Instant generatedAt;
    Instant updatedAt;
    List<RoadmapStepResponse> steps;
}

