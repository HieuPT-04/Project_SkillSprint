package com.skillsprint.controller.learningstructure;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.learningstructure.LearningStructureResponse;
import com.skillsprint.service.learningstructure.LearningStructureService;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/learning-structure")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LearningStructureController {

    LearningStructureService learningStructureService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<LearningStructureResponse>> generate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        LearningStructureResponse response = learningStructureService.generate(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success("Tạo cấu trúc học tập thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<LearningStructureResponse>> getLatest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        LearningStructureResponse response = learningStructureService.getLatest(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<LearningStructureResponse>> confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        LearningStructureResponse response = learningStructureService.confirm(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận cấu trúc học tập thành công", response));
    }
}
