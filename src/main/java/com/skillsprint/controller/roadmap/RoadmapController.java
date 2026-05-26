package com.skillsprint.controller.roadmap;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.roadmap.RoadmapResponse;
import com.skillsprint.service.roadmap.RoadmapService;
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
@RequestMapping("/api/workspaces/{workspaceId}/roadmaps")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoadmapController {

    RoadmapService roadmapService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<RoadmapResponse>> generate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        RoadmapResponse response = roadmapService.generate(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success("Generate roadmap successfully", response));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<RoadmapResponse>> getCurrent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        RoadmapResponse response = roadmapService.getCurrent(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
