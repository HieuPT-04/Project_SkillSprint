package com.skillsprint.controller.workspace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.workspace.UpsertOnboardingProfileRequest;
import com.skillsprint.dto.response.workspace.OnboardingProfileResponse;
import com.skillsprint.service.workspace.OnboardingProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/onboarding")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OnboardingProfileController {

    OnboardingProfileService onboardingProfileService;

    @PutMapping
    public ResponseEntity<ApiResponse<OnboardingProfileResponse>> upsertOnboardingProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpsertOnboardingProfileRequest request
    ) {
        OnboardingProfileResponse response = onboardingProfileService.upsertOnboardingProfile(
                jwt.getSubject(),
                workspaceId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success("Lưu thiết lập học tập thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OnboardingProfileResponse>> getOnboardingProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        OnboardingProfileResponse response = onboardingProfileService.getOnboardingProfile(
                jwt.getSubject(),
                workspaceId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
