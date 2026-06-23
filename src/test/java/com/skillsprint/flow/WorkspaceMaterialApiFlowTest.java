package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.material.ConfirmMaterialUploadRequest;
import com.skillsprint.dto.request.material.CreateMaterialUploadUrlRequest;
import com.skillsprint.dto.request.workspace.CreateWorkspaceRequest;
import com.skillsprint.dto.request.workspace.UpdateWorkspaceRequest;
import com.skillsprint.dto.response.material.MaterialProcessingJobResponse;
import com.skillsprint.dto.response.material.MaterialUploadUrlResponse;
import com.skillsprint.dto.response.material.UploadedMaterialResponse;
import com.skillsprint.dto.response.workspace.WorkspaceResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.material.FileType;
import com.skillsprint.enums.material.MaterialProcessingStatus;
import com.skillsprint.enums.material.ProcessingJobStatus;
import com.skillsprint.enums.material.UploadStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.material.MaterialProcessingService;
import com.skillsprint.service.material.MaterialService;
import com.skillsprint.service.workspace.WorkspaceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceMaterialApiFlowTest {

    private static final String USER_ID = "workspace-flow-user";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    WorkspaceService workspaceService;

    @MockBean
    MaterialService materialService;

    @MockBean
    MaterialProcessingService materialProcessingService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID workspaceId;
    UUID materialId;
    UUID jobId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        materialId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void anonymousUserCannotAccessWorkspaceEndpoints() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(workspaceService, never()).getMyWorkspaces(any());
        verify(workspaceService, never()).createWorkspace(any(), any());
    }

    @Test
    void createWorkspaceReturnsCreatedAndValidationErrorsDoNotCallService() throws Exception {
        when(workspaceService.createWorkspace(eq(USER_ID), any(CreateWorkspaceRequest.class)))
                .thenReturn(workspaceResponse("Java", "Learn Java", WorkspaceStatus.ACTIVE));

        mockMvc.perform(post("/api/workspaces")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java",
                                  "description": "Learn Java"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("Tạo workspace thành công"))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.name").value("Java"));

        mockMvc.perform(post("/api/workspaces")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void workspaceCrudEndpointsUseAuthenticatedUserAndMapErrors() throws Exception {
        when(workspaceService.getMyWorkspaces(USER_ID))
                .thenReturn(List.of(workspaceResponse("Java", "Learn Java", WorkspaceStatus.ACTIVE)));
        when(workspaceService.getWorkspace(USER_ID, workspaceId))
                .thenReturn(workspaceResponse("Java", "Learn Java", WorkspaceStatus.ACTIVE));
        when(workspaceService.updateWorkspace(eq(USER_ID), eq(workspaceId), any(UpdateWorkspaceRequest.class)))
                .thenReturn(workspaceResponse("Java Updated", null, WorkspaceStatus.ARCHIVED));

        mockMvc.perform(get("/api/workspaces").with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].workspaceId").value(workspaceId.toString()));

        mockMvc.perform(get("/api/workspaces/{workspaceId}", workspaceId).with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Java"));

        mockMvc.perform(patch("/api/workspaces/{workspaceId}", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java Updated",
                                  "status": "ARCHIVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật workspace thành công"))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(delete("/api/workspaces/{workspaceId}", workspaceId).with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa workspace thành công"));

        verify(workspaceService).deleteWorkspace(USER_ID, workspaceId);

        when(workspaceService.getWorkspace(USER_ID, workspaceId))
                .thenThrow(new AppException(ErrorCode.WORKSPACE_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/{workspaceId}", workspaceId).with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void materialUploadUrlAndConfirmEndpointsReturnExpectedShapes() throws Exception {
        when(materialService.createUploadUrl(eq(USER_ID), eq(workspaceId), any(CreateMaterialUploadUrlRequest.class)))
                .thenReturn(MaterialUploadUrlResponse.builder()
                        .uploadUrl("https://s3.example.com/upload")
                        .fileUrl("https://cdn.example.com/file.pdf")
                        .objectKey("users/user-1/workspaces/workspace/materials/file.pdf")
                        .expiresAt(Instant.parse("2026-06-23T10:10:00Z"))
                        .build());
        when(materialService.confirmUpload(eq(USER_ID), eq(workspaceId), any(ConfirmMaterialUploadRequest.class)))
                .thenReturn(materialResponse());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/materials/upload-url", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "file.pdf",
                                  "contentType": "application/pdf"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.example.com/upload"))
                .andExpect(jsonPath("$.data.fileUrl").value("https://cdn.example.com/file.pdf"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/materials/confirm", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "users/user-1/workspaces/workspace/materials/file.pdf",
                                  "fileName": "file.pdf",
                                  "contentType": "application/pdf"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xác nhận tải tài liệu thành công"))
                .andExpect(jsonPath("$.data.materialId").value(materialId.toString()))
                .andExpect(jsonPath("$.data.fileType").value("PDF"))
                .andExpect(jsonPath("$.data.processingStatus").value("PENDING"));
    }

    @Test
    void materialValidationAndQuotaErrorsDoNotFallThrough() throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/materials/upload-url", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "",
                                  "contentType": "application/pdf"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());

        when(materialService.createUploadUrl(eq(USER_ID), eq(workspaceId), any(CreateMaterialUploadUrlRequest.class)))
                .thenThrow(new AppException(ErrorCode.QUOTA_UPLOAD_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/materials/upload-url", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "file.pdf",
                                  "contentType": "application/pdf"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void materialReadProcessingAndDeleteEndpointsUseAuthenticatedUser() throws Exception {
        when(materialService.getWorkspaceMaterials(USER_ID, workspaceId)).thenReturn(List.of(materialResponse()));
        when(materialService.getMaterialDetail(USER_ID, workspaceId, materialId)).thenReturn(materialResponse());
        when(materialProcessingService.getLatestJob(USER_ID, workspaceId, materialId))
                .thenReturn(MaterialProcessingJobResponse.builder()
                        .jobId(jobId)
                        .status(ProcessingJobStatus.PENDING)
                        .progressPercent(0)
                        .retryable(false)
                        .build());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/materials", workspaceId).with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].materialId").value(materialId.toString()));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/materials/{materialId}", workspaceId, materialId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("file.pdf"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/materials/{materialId}/processing-job", workspaceId, materialId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(delete("/api/workspaces/{workspaceId}/materials/{materialId}", workspaceId, materialId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa tài liệu thành công"));

        verify(materialService).deleteMaterial(USER_ID, workspaceId, materialId);
    }

    @Test
    void materialNotFoundMapsTo404() throws Exception {
        when(materialService.getMaterialDetail(USER_ID, workspaceId, materialId))
                .thenThrow(new AppException(ErrorCode.MATERIAL_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/materials/{materialId}", workspaceId, materialId)
                        .with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private WorkspaceResponse workspaceResponse(String name, String description, WorkspaceStatus status) {
        return WorkspaceResponse.builder()
                .workspaceId(workspaceId)
                .name(name)
                .description(description)
                .status(status)
                .build();
    }

    private UploadedMaterialResponse materialResponse() {
        return UploadedMaterialResponse.builder()
                .materialId(materialId)
                .workspaceId(workspaceId)
                .originalFileName("file.pdf")
                .fileName("file.pdf")
                .fileType(FileType.PDF)
                .fileSizeBytes(2048L)
                .fileUrl("https://cdn.example.com/file.pdf")
                .uploadStatus(UploadStatus.UPLOADED)
                .processingStatus(MaterialProcessingStatus.PENDING)
                .processingJob(MaterialProcessingJobResponse.builder()
                        .jobId(jobId)
                        .status(ProcessingJobStatus.PENDING)
                        .progressPercent(0)
                        .build())
                .build();
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("workspace-flow@example.com");
        user.setFullName("Workspace Flow");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
