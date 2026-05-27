package com.skillsprint.dto.response.progress;

import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProgressStepResponse {

    UUID stepId;
    String title;
    Integer sequenceNo;
    RoadmapStepStatus status;
}
