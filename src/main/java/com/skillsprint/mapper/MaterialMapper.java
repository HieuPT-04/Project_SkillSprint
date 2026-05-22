package com.skillsprint.mapper;

import com.skillsprint.configuration.s3.S3Properties;
import com.skillsprint.dto.response.material.MaterialProcessingJobResponse;
import com.skillsprint.dto.response.material.UploadedMaterialResponse;
import com.skillsprint.entity.MaterialProcessingJob;
import com.skillsprint.entity.UploadedMaterial;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaterialMapper {

    S3Properties s3Properties;

    public UploadedMaterialResponse toUploadedMaterialResponse(
            UploadedMaterial material,
            MaterialProcessingJob job
    ) {
        return UploadedMaterialResponse.builder()
                .materialId(material.getMaterialId())
                .workspaceId(material.getWorkspace().getWorkspaceId())
                .originalFileName(material.getOriginalFileName())
                .fileName(material.getFileName())
                .fileType(material.getFileType())
                .fileSizeBytes(material.getFileSizeBytes())
                .fileUrl(buildFileUrl(material.getS3ObjectKey()))
                .uploadStatus(material.getUploadStatus())
                .processingStatus(material.getProcessingStatus())
                .processingJob(toMaterialProcessingJobResponse(job))
                .uploadedAt(material.getUploadedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }

    private MaterialProcessingJobResponse toMaterialProcessingJobResponse(MaterialProcessingJob job) {
        if (job == null) {
            return null;
        }

        return MaterialProcessingJobResponse.builder()
                .jobId(job.getJobId())
                .status(job.getStatus())
                .currentStep(job.getCurrentStep())
                .progressPercent(job.getProgressPercent())
                .retryable(job.isRetryable())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private String buildFileUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return s3Properties.publicBaseUrl().replaceAll("/+$", "") + "/" + objectKey;
    }
}
