package com.skillsprint.controller.auth;

import com.skillsprint.dto.request.auth.ConfirmRegisterRequest;
import com.skillsprint.dto.request.auth.ConfirmForgotPasswordRequest;
import com.skillsprint.dto.request.auth.CompleteNewPasswordRequest;
import com.skillsprint.dto.request.auth.ForgotPasswordRequest;
import com.skillsprint.dto.request.auth.LoginRequest;
import com.skillsprint.dto.request.auth.RegisterRequest;
import com.skillsprint.dto.request.auth.RefreshTokenRequest;
import com.skillsprint.dto.request.auth.ResendConfirmationCodeRequest;
import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.auth.AuthResponse;
import com.skillsprint.service.auth.AuthService;
import com.skillsprint.service.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    RateLimitService rateLimitService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimitService.checkRegister(request.getEmail(), servletRequest);
        authService.register(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.created("Đăng ký thành công. Vui lòng kiểm tra email để lấy mã xác thực", null));
    }

    @PostMapping("/confirm-register")
    public ResponseEntity<ApiResponse<Void>> confirmRegister(@Valid @RequestBody ConfirmRegisterRequest request) {
        authService.confirmRegister(request);
        return ResponseEntity.ok(ApiResponse.success("Xác thực tài khoản thành công", null));
    }

    @PostMapping("/resend-confirmation-code")
    public ResponseEntity<ApiResponse<Void>> resendConfirmationCode(
            @Valid @RequestBody ResendConfirmationCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimitService.checkResendConfirmationCode(request.getEmail(), servletRequest);
        authService.resendConfirmationCode(request);
        return ResponseEntity.ok(ApiResponse.success("Mã xác thực đã được gửi lại", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimitService.checkForgotPassword(request.getEmail(), servletRequest);
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Mã đặt lại mật khẩu đã được gửi tới email", null));
    }

    @PostMapping("/confirm-forgot-password")
    public ResponseEntity<ApiResponse<Void>> confirmForgotPassword(
            @Valid @RequestBody ConfirmForgotPasswordRequest request
    ) {
        authService.confirmForgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimitService.checkLogin(request.getEmail(), servletRequest);
        AuthResponse response = authService.login(request);
        String message = response.getChallengeName() == null ? "Đăng nhập thành công" : "Cần đổi mật khẩu mới";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PostMapping("/complete-new-password")
    public ResponseEntity<ApiResponse<AuthResponse>> completeNewPassword(
            @Valid @RequestBody CompleteNewPasswordRequest request
    ) {
        AuthResponse response = authService.completeNewPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestHeader("X-Session-Id") String sessionId,
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse response = authService.refreshToken(request, sessionId);
        return ResponseEntity.ok(ApiResponse.success("Làm mới phiên đăng nhập thành công", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader("X-Session-Id") String sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        authService.logout(authorizationHeader, jwt.getSubject(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }
}
