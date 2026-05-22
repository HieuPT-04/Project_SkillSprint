package com.skillsprint.dto.response.material;

import com.skillsprint.enums.material.FileType;
import com.skillsprint.enums.material.MaterialProcessingStatus;
import com.skillsprint.enums.material.UploadStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadedMaterialResponse {

    UUID materialId;
    UUID workspaceId;
    String originalFileName;
    String fileName;
    FileType fileType;
    Long fileSizeBytes;
    String fileUrl;
    UploadStatus uploadStatus;
    MaterialProcessingStatus processingStatus;
    MaterialProcessingJobResponse processingJob;
    Instant uploadedAt;
    Instant updatedAt;
}
