package com.skillsprint.controller.progress;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.progress.ProgressDashboardResponse;
import com.skillsprint.service.progress.ProgressService;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProgressController {

    ProgressService progressService;

    @GetMapping("/workspaces/{workspaceId}/progress")
    public ResponseEntity<ApiResponse<ProgressDashboardResponse>> getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        ProgressDashboardResponse response = progressService.getDashboard(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success("Lấy tiến độ học thành công", response));
    }
}
