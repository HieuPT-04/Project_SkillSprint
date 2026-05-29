package com.skillsprint.dto.response.learningstructure;

import com.skillsprint.enums.learningstructure.GeneratedBy;
import com.skillsprint.enums.learningstructure.LearningStructureStatus;
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
public class LearningStructureResponse {

    UUID structureVersionId;
    UUID workspaceId;
    Integer versionNo;
    LearningStructureStatus status;
    GeneratedBy generatedBy;
    String aiModel;
    BigDecimal confidenceScore;
    String inputSummary;
    List<String> warnings;
    Instant createdAt;
    Instant confirmedAt;
    List<ChapterResponse> chapters;
}
