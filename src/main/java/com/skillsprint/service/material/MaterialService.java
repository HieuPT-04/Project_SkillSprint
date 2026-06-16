package com.skillsprint.service.material;

import com.skillsprint.configuration.s3.S3Properties;
import com.skillsprint.dto.request.material.ConfirmMaterialUploadRequest;
import com.skillsprint.dto.request.material.CreateMaterialUploadUrlRequest;
import com.skillsprint.dto.response.material.MaterialUploadUrlResponse;
import com.skillsprint.dto.response.material.UploadedMaterialResponse;
import com.skillsprint.entity.MaterialProcessingJob;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.UploadedMaterial;
import com.skillsprint.entity.User;
import com.skillsprint.enums.material.FileType;
import com.skillsprint.enums.material.MaterialProcessingStatus;
import com.skillsprint.enums.material.ProcessingJobStatus;
import com.skillsprint.enums.material.UploadStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.MaterialMapper;
import com.skillsprint.repository.MaterialProcessingJobRepository;
import com.skillsprint.repository.ExtractedDocumentRepository;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.UploadedMaterialRepository;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import com.skillsprint.service.subscription.QuotaService;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaterialService {

    static Map<String, FileType> FILE_TYPE_BY_CONTENT_TYPE = Map.ofEntries(
            Map.entry("application/pdf", FileType.PDF),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", FileType.DOCX),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", FileType.PPTX),
            Map.entry("text/plain", FileType.TXT),
            Map.entry("application/zip", FileType.ZIP),
            Map.entry("application/x-zip-compressed", FileType.ZIP)
    );

    S3Presigner s3Presigner;
    S3Client s3Client;
    S3Properties s3Properties;
    StudyWorkspaceRepository workspaceRepository;
    UploadedMaterialRepository uploadedMaterialRepository;
    MaterialProcessingJobRepository materialProcessingJobRepository;
    MaterialChunkRepository materialChunkRepository;
    ExtractedDocumentRepository extractedDocumentRepository;
    MaterialMapper materialMapper;
    QuotaService quotaService;

    @Transactional(readOnly = true)
    public MaterialUploadUrlResponse createUploadUrl(
            String userId,
            UUID workspaceId,
            CreateMaterialUploadUrlRequest request
    ) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        validateWorkspaceHasNoMaterial(userId, workspaceId);
        quotaService.validateCanStartMaterialUpload(userId);
        String contentType = normalizeContentType(request.getContentType());
        FileType fileType = resolveFileType(contentType);
        String objectKey = buildMaterialObjectKey(workspace, request.getFileName(), fileType);
        Duration signatureDuration = Duration.ofMinutes(s3Properties.uploadUrlExpirationMinutes());
        Instant expiresAt = Instant.now().plus(signatureDuration);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        return MaterialUploadUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .fileUrl(buildFileUrl(objectKey))
                .objectKey(objectKey)
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public UploadedMaterialResponse confirmUpload(
            String userId,
            UUID workspaceId,
            ConfirmMaterialUploadRequest request
    ) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        validateWorkspaceHasNoMaterial(userId, workspaceId);
        String objectKey = request.getObjectKey().trim();
        validateObjectKeyOwner(userId, workspaceId, objectKey);

        String contentType = normalizeContentType(request.getContentType());
        FileType fileType = resolveFileType(contentType);
        validateFileExtension(request.getFileName(), fileType);
        HeadObjectResponse headObject = getUploadedObject(objectKey);

        quotaService.validateCanStartMaterialUpload(userId);
        quotaService.validateCanConfirmMaterialUpload(userId, workspaceId, headObject.contentLength());

        UploadedMaterial material = new UploadedMaterial();
        material.setWorkspace(workspace);
        material.setUser(workspace.getUser());
        material.setOriginalFileName(request.getFileName().trim());
        material.setFileName(request.getFileName().trim());
        material.setFileType(fileType);
        material.setFileSizeBytes(headObject.contentLength());
        material.setS3Bucket(s3Properties.bucket());
        material.setS3ObjectKey(objectKey);
        material.setUploadStatus(UploadStatus.UPLOADED);
        material.setProcessingStatus(MaterialProcessingStatus.PENDING);

        UploadedMaterial savedMaterial = uploadedMaterialRepository.save(material);
        MaterialProcessingJob job = createProcessingJob(savedMaterial, workspace);

        return materialMapper.toUploadedMaterialResponse(savedMaterial, job);
    }

    @Transactional(readOnly = true)
    public List<UploadedMaterialResponse> getWorkspaceMaterials(String userId, UUID workspaceId) {
        findOwnedWorkspace(userId, workspaceId);

        return uploadedMaterialRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdOrderByUploadedAtDesc(workspaceId, userId)
                .stream()
                .map(material -> materialMapper.toUploadedMaterialResponse(
                        material,
                        materialProcessingJobRepository
                                .findTopByMaterialMaterialIdOrderByCreatedAtDesc(material.getMaterialId())
                                .orElse(null)
                ))
                .toList();
    }

    @Transactional
    public void deleteMaterial(String userId, UUID workspaceId, UUID materialId) {
        findOwnedWorkspace(userId, workspaceId);

        UploadedMaterial material = uploadedMaterialRepository
                .findByMaterialIdAndWorkspaceWorkspaceIdAndUserUserId(materialId, workspaceId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_NOT_FOUND));

        materialChunkRepository.deleteByMaterialMaterialId(materialId);
        extractedDocumentRepository.deleteByMaterialMaterialId(materialId);
        materialProcessingJobRepository.deleteByMaterialMaterialId(materialId);
        uploadedMaterialRepository.delete(material);

        deleteS3ObjectBestEffort(material);
    }

    private StudyWorkspace findOwnedWorkspace(String userId, UUID workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private void validateWorkspaceHasNoMaterial(String userId, UUID workspaceId) {
        if (uploadedMaterialRepository.existsByWorkspaceWorkspaceIdAndUserUserId(workspaceId, userId)) {
            throw new AppException(ErrorCode.MATERIAL_WORKSPACE_LIMIT_EXCEEDED);
        }
    }

    private void deleteS3ObjectBestEffort(UploadedMaterial material) {
        String objectKey = material.getS3ObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(s3Properties.bucket())
                            .key(objectKey)
                            .build()
            );
        } catch (Exception ex) {
            log.warn("[MATERIAL] Failed to delete S3 object for material {}", material.getMaterialId(), ex);
        }
    }

    private MaterialProcessingJob createProcessingJob(UploadedMaterial material, StudyWorkspace workspace) {
        MaterialProcessingJob job = new MaterialProcessingJob();
        job.setMaterial(material);
        job.setWorkspace(workspace);
        job.setUser(workspace.getUser());
        job.setStatus(ProcessingJobStatus.PENDING);
        job.setProgressPercent(0);
        job.setRetryable(false);
        return materialProcessingJobRepository.save(job);
    }

    private HeadObjectResponse getUploadedObject(String objectKey) {
        try {
            return s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(s3Properties.bucket())
                            .key(objectKey)
                            .build()
            );
        } catch (S3Exception ex) {
            throw new AppException(ErrorCode.MATERIAL_NOT_UPLOADED);
        }
    }

    private void validateObjectKeyOwner(String userId, UUID workspaceId, String objectKey) {
        String expectedPrefix = "users/%s-".formatted(userId);
        String expectedWorkspaceSegment = "/workspaces/%s-".formatted(workspaceId);
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new AppException(ErrorCode.INVALID_MATERIAL_OBJECT_KEY);
        }
        if (!objectKey.contains(expectedWorkspaceSegment)) {
            throw new AppException(ErrorCode.INVALID_MATERIAL_OBJECT_KEY);
        }
    }

    private String buildMaterialObjectKey(StudyWorkspace workspace, String fileName, FileType fileType) {
        validateFileExtension(fileName, fileType);
        User user = workspace.getUser();
        String userLabel = sanitizeForObjectKey(user.getFullName());
        String workspaceLabel = sanitizeForObjectKey(workspace.getName());
        String safeFileName = sanitizeForObjectKey(fileName);

        return "users/%s-%s/workspaces/%s-%s/materials/%s-%s".formatted(
                user.getUserId(),
                userLabel,
                workspace.getWorkspaceId(),
                workspaceLabel,
                UUID.randomUUID(),
                safeFileName
        );
    }

    private FileType resolveFileType(String contentType) {
        FileType fileType = FILE_TYPE_BY_CONTENT_TYPE.get(contentType);
        if (fileType == null) {
            throw new AppException(ErrorCode.INVALID_MATERIAL_CONTENT_TYPE);
        }
        return fileType;
    }

    private String normalizeContentType(String contentType) {
        return contentType.trim().toLowerCase();
    }

    private void validateFileExtension(String fileName, FileType fileType) {
        String expectedExtension = fileType.name().toLowerCase();
        String normalizedFileName = fileName.trim().toLowerCase();
        if (!normalizedFileName.endsWith("." + expectedExtension)) {
            throw new AppException(ErrorCode.INVALID_MATERIAL_FILE_EXTENSION);
        }
    }

    private String buildFileUrl(String objectKey) {
        return s3Properties.publicBaseUrl().replaceAll("/+$", "") + "/" + objectKey;
    }

    private String sanitizeForObjectKey(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        String sanitized = normalized.toLowerCase()
                .replaceAll("[^a-z0-9.]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        return sanitized.isBlank() ? "unknown" : sanitized;
    }
}
