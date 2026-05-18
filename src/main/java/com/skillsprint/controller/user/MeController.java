package com.skillsprint.controller.user;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.user.ConfirmAvatarUploadRequest;
import com.skillsprint.dto.request.user.CreateAvatarUploadUrlRequest;
import com.skillsprint.dto.request.user.UpdateMeRequest;
import com.skillsprint.dto.response.user.AvatarUploadUrlResponse;
import com.skillsprint.dto.response.user.MeResponse;
import com.skillsprint.service.storage.S3PresignedUrlService;
import com.skillsprint.service.user.UserQueryService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MeController {

    UserQueryService userQueryService;
    S3PresignedUrlService s3PresignedUrlService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        MeResponse response = userQueryService.getMe(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMeRequest request
    ) {
        MeResponse response = userQueryService.updateMe(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Update profile successfully", response));
    }

    @PostMapping("/me/avatar/upload-url")
    public ResponseEntity<ApiResponse<AvatarUploadUrlResponse>> createAvatarUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAvatarUploadUrlRequest request
    ) {
        AvatarUploadUrlResponse response = s3PresignedUrlService.createAvatarUploadUrl(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/me/avatar/confirm")
    public ResponseEntity<ApiResponse<MeResponse>> confirmAvatarUpload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmAvatarUploadRequest request
    ) {
        String avatarUrl = s3PresignedUrlService.confirmAvatarUpload(jwt.getSubject(), request);
        MeResponse response = userQueryService.updateAvatar(jwt.getSubject(), avatarUrl);
        return ResponseEntity.ok(ApiResponse.success("Update avatar successfully", response));
    }
}
