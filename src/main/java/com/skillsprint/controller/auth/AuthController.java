package com.skillsprint.controller.auth;

import com.skillsprint.dto.request.auth.ConfirmRegisterRequest;
import com.skillsprint.dto.request.auth.ConfirmForgotPasswordRequest;
import com.skillsprint.dto.request.auth.CompleteNewPasswordRequest;
import com.skillsprint.dto.request.auth.ForgotPasswordRequest;
import com.skillsprint.dto.request.auth.LoginRequest;
import com.skillsprint.dto.request.auth.RegisterRequest;
import com.skillsprint.dto.request.auth.ResendConfirmationCodeRequest;
import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.auth.AuthResponse;
import com.skillsprint.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.created("Register successfully. Please check your email for confirmation code.", null));
    }

    @PostMapping("/confirm-register")
    public ResponseEntity<ApiResponse<Void>> confirmRegister(@Valid @RequestBody ConfirmRegisterRequest request) {
        authService.confirmRegister(request);
        return ResponseEntity.ok(ApiResponse.success("Confirm register successfully", null));
    }

    @PostMapping("/resend-confirmation-code")
    public ResponseEntity<ApiResponse<Void>> resendConfirmationCode(
            @Valid @RequestBody ResendConfirmationCodeRequest request
    ) {
        authService.resendConfirmationCode(request);
        return ResponseEntity.ok(ApiResponse.success("Confirmation code has been sent again", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Reset code has been sent to your email", null));
    }

    @PostMapping("/confirm-forgot-password")
    public ResponseEntity<ApiResponse<Void>> confirmForgotPassword(
            @Valid @RequestBody ConfirmForgotPasswordRequest request
    ) {
        authService.confirmForgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        String message = response.getChallengeName() == null ? "Login successfully" : "New password required";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PostMapping("/complete-new-password")
    public ResponseEntity<ApiResponse<AuthResponse>> completeNewPassword(
            @Valid @RequestBody CompleteNewPasswordRequest request
    ) {
        AuthResponse response = authService.completeNewPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        authService.logout(authorizationHeader);
        return ResponseEntity.ok(ApiResponse.success("Logout successfully", null));
    }
}
