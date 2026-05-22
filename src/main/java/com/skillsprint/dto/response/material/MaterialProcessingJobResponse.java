package com.skillsprint.dto.response.material;

import com.skillsprint.enums.material.ProcessingJobStatus;
import com.skillsprint.enums.material.ProcessingStep;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaterialProcessingJobResponse {

    UUID jobId;
    ProcessingJobStatus status;
    ProcessingStep currentStep;
    Integer progressPercent;
    boolean retryable;
    Instant createdAt;
    Instant updatedAt;
}
