package com.skillsprint.controller.user;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.user.UpdateMeRequest;
import com.skillsprint.dto.response.user.MeResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MeController {

    UserQueryService userQueryService;

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
}
