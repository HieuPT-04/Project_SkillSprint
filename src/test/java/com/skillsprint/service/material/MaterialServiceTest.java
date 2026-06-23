package com.skillsprint.service.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.skillsprint.repository.ExtractedDocumentRepository;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.MaterialProcessingJobRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.UploadedMaterialRepository;
import com.skillsprint.service.subscription.QuotaService;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    S3Presigner s3Presigner;

    @Mock
    S3Client s3Client;

    @Mock
    StudyWorkspaceRepository workspaceRepository;

    @Mock
    UploadedMaterialRepository uploadedMaterialRepository;

    @Mock
    MaterialProcessingJobRepository materialProcessingJobRepository;

    @Mock
    MaterialChunkRepository materialChunkRepository;

    @Mock
    ExtractedDocumentRepository extractedDocumentRepository;

    @Mock
    MaterialMapper materialMapper;

    @Mock
    QuotaService quotaService;

    MaterialService materialService;
    S3Properties s3Properties;
    User user;
    StudyWorkspace workspace;

    @BeforeEach
    void setUp() {
        s3Properties = new S3Properties(
                "ap-southeast-1",
                "test-bucket",
                "https://cdn.example.com/",
                null,
                null,
                10
        );
        materialService = new MaterialService(
                s3Presigner,
                s3Client,
                s3Properties,
                workspaceRepository,
                uploadedMaterialRepository,
                materialProcessingJobRepository,
                materialChunkRepository,
                extractedDocumentRepository,
                materialMapper,
                quotaService
        );
        user = user("user-1");
        workspace = workspace(user, "Java Roadmap");
    }

    @Test
    void createUploadUrlCreatesSanitizedOwnedObjectKeyAndPresignedUrl() {
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.existsByWorkspaceWorkspaceIdAndUserUserId(workspace.getWorkspaceId(), "user-1"))
                .thenReturn(false);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPut("https://s3.example.com/upload"));

        MaterialUploadUrlResponse response = materialService.createUploadUrl(
                "user-1",
                workspace.getWorkspaceId(),
                uploadUrlRequest("  Lộ trình Java.pdf  ", " Application/PDF ")
        );

        assertEquals("https://s3.example.com/upload", response.getUploadUrl());
        assertTrue(response.getObjectKey().startsWith("users/user-1-test-user/workspaces/%s-java-roadmap/materials/"
                .formatted(workspace.getWorkspaceId())));
        assertTrue(response.getObjectKey().endsWith("-lo-trinh-java.pdf"));
        assertEquals("https://cdn.example.com/" + response.getObjectKey(), response.getFileUrl());
        assertNotNull(response.getExpiresAt());
        verify(quotaService).validateCanStartMaterialUpload("user-1");

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().putObjectRequest().bucket());
        assertEquals("application/pdf", captor.getValue().putObjectRequest().contentType());
        assertEquals(response.getObjectKey(), captor.getValue().putObjectRequest().key());
    }

    @Test
    void createUploadUrlRejectsWorkspaceThatAlreadyHasMaterial() {
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.existsByWorkspaceWorkspaceIdAndUserUserId(workspace.getWorkspaceId(), "user-1"))
                .thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> materialService.createUploadUrl(
                        "user-1",
                        workspace.getWorkspaceId(),
                        uploadUrlRequest("file.pdf", "application/pdf")
                )
        );

        assertEquals(ErrorCode.MATERIAL_WORKSPACE_LIMIT_EXCEEDED, exception.getErrorCode());
        verify(quotaService, never()).validateCanStartMaterialUpload(any());
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void createUploadUrlRejectsUnsupportedContentType() {
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.existsByWorkspaceWorkspaceIdAndUserUserId(workspace.getWorkspaceId(), "user-1"))
                .thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> materialService.createUploadUrl(
                        "user-1",
                        workspace.getWorkspaceId(),
                        uploadUrlRequest("file.exe", "application/x-msdownload")
                )
        );

        assertEquals(ErrorCode.INVALID_MATERIAL_CONTENT_TYPE, exception.getErrorCode());
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void createUploadUrlRejectsMismatchedFileExtension() {
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.existsByWorkspaceWorkspaceIdAndUserUserId(workspace.getWorkspaceId(), "user-1"))
                .thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> materialService.createUploadUrl(
                        "user-1",
                        workspace.getWorkspaceId(),
                        uploadUrlRequest("file.txt", "application/pdf")
                )
        );

        assertEquals(ErrorCode.INVALID_MATERIAL_FILE_EXTENSION, exception.getErrorCode());
    }

    @Test
    void confirmUploadCreatesUploadedMaterialAndPendingProcessingJob() {
        String objectKey = ownedObjectKey("guide.pdf");
        UploadedMaterialResponse expected = UploadedMaterialResponse.builder()
                .materialId(UUID.randomUUID())
                .workspaceId(workspace.getWorkspaceId())
                .fileType(FileType.PDF)
                .processingStatus(MaterialProcessingStatus.PENDING)
                .build();
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.existsByWorkspaceWorkspaceIdAndUserUserId(workspace.getWorkspaceId(), "user-1"))
                .thenReturn(false);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(2048L).build());
        when(uploadedMaterialRepository.save(any(UploadedMaterial.class)))
                .thenAnswer(invocation -> {
                    UploadedMaterial material = invocation.getArgument(0);
                    material.setMaterialId(UUID.randomUUID());
                    return material;
                });
        when(materialProcessingJobRepository.save(any(MaterialProcessingJob.class)))
                .thenAnswer(invocation -> {
                    MaterialProcessingJob job = invocation.getArgument(0);
                    job.setJobId(UUID.randomUUID());
                    return job;
                });
        when(materialMapper.toUploadedMaterialResponse(any(UploadedMaterial.class), any(MaterialProcessingJob.class)))
                .thenReturn(expected);

        UploadedMaterialResponse response = materialService.confirmUpload(
                "user-1",
                workspace.getWorkspaceId(),
                confirmRequest(objectKey, " guide.pdf ", " application/pdf ")
        );

        assertSame(expected, response);
        verify(quotaService).validateCanStartMaterialUpload("user-1");
        verify(quotaService).validateCanConfirmMaterialUpload("user-1", workspace.getWorkspaceId(), 2048L);

        ArgumentCaptor<UploadedMaterial> materialCaptor = ArgumentCaptor.forClass(UploadedMaterial.class);
        verify(uploadedMaterialRepository).save(materialCaptor.capture());
        UploadedMaterial savedMaterial = materialCaptor.getValue();
        assertSame(workspace, savedMaterial.getWorkspace());
        assertSame(user, savedMaterial.getUser());
        assertEquals("guide.pdf", savedMaterial.getOriginalFileName());
        assertEquals(FileType.PDF, savedMaterial.getFileType());
        assertEquals(2048L, savedMaterial.getFileSizeBytes());
        assertEquals("test-bucket", savedMaterial.getS3Bucket());
        assertEquals(objectKey, savedMaterial.getS3ObjectKey());
        assertEquals(UploadStatus.UPLOADED, savedMaterial.getUploadStatus());
        assertEquals(MaterialProcessingStatus.PENDING, savedMaterial.getProcessingStatus());

        ArgumentCaptor<MaterialProcessingJob> jobCaptor = ArgumentCaptor.forClass(MaterialProcessingJob.class);
        verify(materialProcessingJobRepository).save(jobCaptor.capture());
        MaterialProcessingJob savedJob = jobCaptor.getValue();
        assertSame(savedMaterial, savedJob.getMaterial());
        assertSame(workspace, savedJob.getWorkspace());
        assertSame(user, savedJob.getUser());
        assertEquals(ProcessingJobStatus.PENDING, savedJob.getStatus());
        assertEquals(0, savedJob.getProgressPercent());
    }

    @Test
    void confirmUploadRejectsObjectKeyOutsideUserWorkspacePrefix() {
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.existsByWorkspaceWorkspaceIdAndUserUserId(workspace.getWorkspaceId(), "user-1"))
                .thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> materialService.confirmUpload(
                        "user-1",
                        workspace.getWorkspaceId(),
                        confirmRequest("users/other-user/workspaces/%s/materials/file.pdf".formatted(workspace.getWorkspaceId()),
                                "file.pdf",
                                "application/pdf")
                )
        );

        assertEquals(ErrorCode.INVALID_MATERIAL_OBJECT_KEY, exception.getErrorCode());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void confirmUploadMapsMissingUploadedObjectToMaterialNotUploaded() {
        String objectKey = ownedObjectKey("guide.pdf");
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.existsByWorkspaceWorkspaceIdAndUserUserId(workspace.getWorkspaceId(), "user-1"))
                .thenReturn(false);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("not found").build());

        AppException exception = assertThrows(
                AppException.class,
                () -> materialService.confirmUpload(
                        "user-1",
                        workspace.getWorkspaceId(),
                        confirmRequest(objectKey, "guide.pdf", "application/pdf")
                )
        );

        assertEquals(ErrorCode.MATERIAL_NOT_UPLOADED, exception.getErrorCode());
        verify(uploadedMaterialRepository, never()).save(any());
        verify(materialProcessingJobRepository, never()).save(any());
    }

    @Test
    void getWorkspaceMaterialsMapsMaterialsWithLatestJob() {
        UploadedMaterial material = material("guide.pdf");
        MaterialProcessingJob job = job(material);
        UploadedMaterialResponse mapped = UploadedMaterialResponse.builder()
                .materialId(material.getMaterialId())
                .build();
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.findByWorkspaceWorkspaceIdAndUserUserIdOrderByUploadedAtDesc(
                workspace.getWorkspaceId(),
                "user-1"
        )).thenReturn(List.of(material));
        when(materialProcessingJobRepository.findTopByMaterialMaterialIdOrderByCreatedAtDesc(material.getMaterialId()))
                .thenReturn(Optional.of(job));
        when(materialMapper.toUploadedMaterialResponse(material, job)).thenReturn(mapped);

        List<UploadedMaterialResponse> response =
                materialService.getWorkspaceMaterials("user-1", workspace.getWorkspaceId());

        assertEquals(List.of(mapped), response);
    }

    @Test
    void getMaterialDetailChecksS3AndAddsViewUrl() {
        UploadedMaterial material = material("guide.pdf");
        MaterialProcessingJob job = job(material);
        UploadedMaterialResponse mapped = UploadedMaterialResponse.builder()
                .materialId(material.getMaterialId())
                .viewUrl("https://s3.example.com/view")
                .build();
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.findByMaterialIdAndWorkspaceWorkspaceIdAndUserUserId(
                material.getMaterialId(),
                workspace.getWorkspaceId(),
                "user-1"
        )).thenReturn(Optional.of(material));
        when(materialProcessingJobRepository.findTopByMaterialMaterialIdOrderByCreatedAtDesc(material.getMaterialId()))
                .thenReturn(Optional.of(job));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(2048L).build());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGet("https://s3.example.com/view"));
        when(materialMapper.toUploadedMaterialResponse(
                any(UploadedMaterial.class),
                any(MaterialProcessingJob.class),
                any(),
                any()
        )).thenReturn(mapped);

        UploadedMaterialResponse response =
                materialService.getMaterialDetail("user-1", workspace.getWorkspaceId(), material.getMaterialId());

        assertSame(mapped, response);
        verify(s3Client).headObject(any(HeadObjectRequest.class));
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void deleteMaterialDeletesDerivedDataMaterialAndS3Object() {
        UploadedMaterial material = material("guide.pdf");
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.findByMaterialIdAndWorkspaceWorkspaceIdAndUserUserId(
                material.getMaterialId(),
                workspace.getWorkspaceId(),
                "user-1"
        )).thenReturn(Optional.of(material));

        materialService.deleteMaterial("user-1", workspace.getWorkspaceId(), material.getMaterialId());

        verify(materialChunkRepository).deleteByMaterialMaterialId(material.getMaterialId());
        verify(extractedDocumentRepository).deleteByMaterialMaterialId(material.getMaterialId());
        verify(materialProcessingJobRepository).deleteByMaterialMaterialId(material.getMaterialId());
        verify(uploadedMaterialRepository).delete(material);

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertEquals("test-bucket", deleteCaptor.getValue().bucket());
        assertEquals(material.getS3ObjectKey(), deleteCaptor.getValue().key());
    }

    @Test
    void deleteMaterialRejectsMissingMaterial() {
        UUID materialId = UUID.randomUUID();
        whenOwnedWorkspace();
        when(uploadedMaterialRepository.findByMaterialIdAndWorkspaceWorkspaceIdAndUserUserId(
                materialId,
                workspace.getWorkspaceId(),
                "user-1"
        )).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> materialService.deleteMaterial("user-1", workspace.getWorkspaceId(), materialId)
        );

        assertEquals(ErrorCode.MATERIAL_NOT_FOUND, exception.getErrorCode());
        verify(uploadedMaterialRepository, never()).delete(any());
    }

    private void whenOwnedWorkspace() {
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspace.getWorkspaceId(),
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));
    }

    private CreateMaterialUploadUrlRequest uploadUrlRequest(String fileName, String contentType) {
        CreateMaterialUploadUrlRequest request = new CreateMaterialUploadUrlRequest();
        request.setFileName(fileName);
        request.setContentType(contentType);
        return request;
    }

    private ConfirmMaterialUploadRequest confirmRequest(String objectKey, String fileName, String contentType) {
        ConfirmMaterialUploadRequest request = new ConfirmMaterialUploadRequest();
        request.setObjectKey(objectKey);
        request.setFileName(fileName);
        request.setContentType(contentType);
        return request;
    }

    private PresignedPutObjectRequest presignedPut(String url) {
        return PresignedPutObjectRequest.builder()
                .expiration(Instant.now().plusSeconds(600))
                .isBrowserExecutable(false)
                .signedHeaders(Map.of("host", List.of("s3.example.com")))
                .httpRequest(SdkHttpFullRequest.builder()
                        .uri(URI.create(url))
                        .method(SdkHttpMethod.PUT)
                        .build())
                .build();
    }

    private PresignedGetObjectRequest presignedGet(String url) {
        return PresignedGetObjectRequest.builder()
                .expiration(Instant.now().plusSeconds(600))
                .isBrowserExecutable(true)
                .signedHeaders(Map.of("host", List.of("s3.example.com")))
                .httpRequest(SdkHttpFullRequest.builder()
                        .uri(URI.create(url))
                        .method(SdkHttpMethod.GET)
                        .build())
                .build();
    }

    private String ownedObjectKey(String fileName) {
        return "users/user-1-test-user/workspaces/%s-java-roadmap/materials/%s"
                .formatted(workspace.getWorkspaceId(), fileName);
    }

    private UploadedMaterial material(String fileName) {
        UploadedMaterial material = new UploadedMaterial();
        material.setMaterialId(UUID.randomUUID());
        material.setWorkspace(workspace);
        material.setUser(user);
        material.setOriginalFileName(fileName);
        material.setFileName(fileName);
        material.setFileType(FileType.PDF);
        material.setFileSizeBytes(2048L);
        material.setS3Bucket("test-bucket");
        material.setS3ObjectKey(ownedObjectKey(fileName));
        material.setUploadStatus(UploadStatus.UPLOADED);
        material.setProcessingStatus(MaterialProcessingStatus.PENDING);
        return material;
    }

    private MaterialProcessingJob job(UploadedMaterial material) {
        MaterialProcessingJob job = new MaterialProcessingJob();
        job.setJobId(UUID.randomUUID());
        job.setMaterial(material);
        job.setWorkspace(workspace);
        job.setUser(user);
        job.setStatus(ProcessingJobStatus.PENDING);
        job.setProgressPercent(0);
        return job;
    }

    private StudyWorkspace workspace(User user, String name) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(user);
        workspace.setName(name);
        workspace.setDescription("Description");
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }
}
