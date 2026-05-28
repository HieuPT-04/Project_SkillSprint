package com.skillsprint.controller.workspace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.workspace.CreateWorkspaceRequest;
import com.skillsprint.dto.request.workspace.UpdateWorkspaceRequest;
import com.skillsprint.dto.response.workspace.WorkspaceResponse;
import com.skillsprint.service.workspace.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WorkspaceController {

    WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResponse>> createWorkspace(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        WorkspaceResponse response = workspaceService.createWorkspace(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo workspace thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> getMyWorkspaces(@AuthenticationPrincipal Jwt jwt) {
        List<WorkspaceResponse> response = workspaceService.getMyWorkspaces(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> getWorkspace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        WorkspaceResponse response = workspaceService.getWorkspace(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> updateWorkspace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request
    ) {
        WorkspaceResponse response = workspaceService.updateWorkspace(jwt.getSubject(), workspaceId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật workspace thành công", response));
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkspace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        workspaceService.deleteWorkspace(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success("Xóa workspace thành công", null));
    }
}
