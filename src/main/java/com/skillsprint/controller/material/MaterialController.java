package com.skillsprint.controller.material;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.material.ConfirmMaterialUploadRequest;
import com.skillsprint.dto.request.material.CreateMaterialUploadUrlRequest;
import com.skillsprint.dto.response.material.MaterialProcessingJobResponse;
import com.skillsprint.dto.response.material.MaterialUploadUrlResponse;
import com.skillsprint.dto.response.material.UploadedMaterialResponse;
import com.skillsprint.service.material.MaterialProcessingService;
import com.skillsprint.service.material.MaterialService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/materials")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaterialController {

    MaterialService materialService;
    MaterialProcessingService materialProcessingService;

    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<MaterialUploadUrlResponse>> createUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateMaterialUploadUrlRequest request
    ) {
        MaterialUploadUrlResponse response = materialService.createUploadUrl(
                jwt.getSubject(),
                workspaceId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<UploadedMaterialResponse>> confirmUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody ConfirmMaterialUploadRequest request
    ) {
        UploadedMaterialResponse response = materialService.confirmUpload(
                jwt.getSubject(),
                workspaceId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success("Xác nhận tải tài liệu thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UploadedMaterialResponse>>> getWorkspaceMaterials(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        List<UploadedMaterialResponse> response = materialService.getWorkspaceMaterials(
                jwt.getSubject(),
                workspaceId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{materialId}")
    public ResponseEntity<ApiResponse<UploadedMaterialResponse>> getMaterialDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @PathVariable UUID materialId
    ) {
        UploadedMaterialResponse response = materialService.getMaterialDetail(
                jwt.getSubject(),
                workspaceId,
                materialId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{materialId}/processing-job")
    public ResponseEntity<ApiResponse<MaterialProcessingJobResponse>> getProcessingJob(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @PathVariable UUID materialId
    ) {
        MaterialProcessingJobResponse response = materialProcessingService.getLatestJob(
                jwt.getSubject(),
                workspaceId,
                materialId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{materialId}")
    public ResponseEntity<ApiResponse<Void>> deleteMaterial(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @PathVariable UUID materialId
    ) {
        materialService.deleteMaterial(jwt.getSubject(), workspaceId, materialId);
        return ResponseEntity.ok(ApiResponse.success("Xóa tài liệu thành công", null));
    }
}
