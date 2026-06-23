package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.auth.ConfirmForgotPasswordRequest;
import com.skillsprint.dto.request.auth.ConfirmRegisterRequest;
import com.skillsprint.dto.request.auth.CompleteNewPasswordRequest;
import com.skillsprint.dto.request.auth.ForgotPasswordRequest;
import com.skillsprint.dto.request.auth.LoginRequest;
import com.skillsprint.dto.request.auth.RefreshTokenRequest;
import com.skillsprint.dto.request.auth.RegisterRequest;
import com.skillsprint.dto.request.auth.ResendConfirmationCodeRequest;
import com.skillsprint.dto.response.auth.AuthResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.auth.AuthService;
import com.skillsprint.service.ratelimit.RateLimitService;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiFlowTest {

    private static final String USER_ID = "auth-flow-user";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    AuthService authService;

    @MockBean
    RateLimitService rateLimitService;

    @MockBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
        when(jwtDecoder.decode("access-token")).thenReturn(decodedJwt());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void registerIsPublicAndReturnsCreatedResponse() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "password123",
                                  "fullName": "Learner"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(201));

        verify(rateLimitService).checkRegister(eq("learner@example.com"), any());
        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void invalidRegisterPayloadReturnsValidationDetailsWithoutCallingServices() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short",
                                  "fullName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        verify(rateLimitService, never()).checkRegister(any(), any());
        verify(authService, never()).register(any());
    }

    @Test
    void registerMapsDuplicateUserAndCognitoOutageStatuses() throws Exception {
        doThrow(new AppException(ErrorCode.USER_ALREADY_EXISTS))
                .doThrow(new AppException(ErrorCode.COGNITO_SERVICE_ERROR))
                .when(authService)
                .register(any(RegisterRequest.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegisterBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegisterBody()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
    }

    @Test
    void confirmationAndPasswordRecoveryEndpointsArePublic() throws Exception {
        mockMvc.perform(post("/api/auth/confirm-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "confirmationCode": "123456"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/resend-confirmation-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/confirm-forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "confirmationCode": "123456",
                                  "newPassword": "newPassword123"
                                }
                                """))
                .andExpect(status().isOk());

        verify(authService).confirmRegister(any(ConfirmRegisterRequest.class));
        verify(authService).resendConfirmationCode(any(ResendConfirmationCodeRequest.class));
        verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
        verify(authService).confirmForgotPassword(any(ConfirmForgotPasswordRequest.class));
    }

    @Test
    void invalidConfirmationCodeReturnsBadRequest() throws Exception {
        doThrow(new AppException(ErrorCode.INVALID_CONFIRMATION_CODE))
                .when(authService)
                .confirmRegister(any(ConfirmRegisterRequest.class));

        mockMvc.perform(post("/api/auth/confirm-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "confirmationCode": "bad"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.path").value("/api/auth/confirm-register"));
    }

    @Test
    void loginReturnsTokensAndChallengeMessages() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(AuthResponse.builder()
                        .accessToken("access-token")
                        .sessionId("session-1")
                        .build())
                .thenReturn(AuthResponse.builder()
                        .challengeName("NEW_PASSWORD_REQUIRED")
                        .session("challenge-session")
                        .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cần đổi mật khẩu mới"))
                .andExpect(jsonPath("$.data.challengeName").value("NEW_PASSWORD_REQUIRED"));
    }

    @Test
    void completeNewPasswordReturnsMappedResponse() throws Exception {
        when(authService.completeNewPassword(any(CompleteNewPasswordRequest.class)))
                .thenReturn(AuthResponse.builder()
                        .accessToken("completed-access-token")
                        .sessionId("completed-session")
                        .build());

        mockMvc.perform(post("/api/auth/complete-new-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "newPassword": "newPassword123",
                                  "session": "challenge-session"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công"))
                .andExpect(jsonPath("$.data.accessToken").value("completed-access-token"))
                .andExpect(jsonPath("$.data.sessionId").value("completed-session"));

        verify(authService).completeNewPassword(any(CompleteNewPasswordRequest.class));
    }

    @Test
    void loginMapsCredentialsAccountMaintenanceAndRateLimitErrors() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AppException(ErrorCode.INVALID_CREDENTIALS))
                .thenThrow(new AppException(ErrorCode.ACCOUNT_NOT_CONFIRMED))
                .thenThrow(new AppException(ErrorCode.MAINTENANCE_MODE, "Maintenance"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Maintenance"));

        doThrow(new AppException(ErrorCode.RATE_LIMIT_EXCEEDED))
                .when(rateLimitService)
                .checkLogin(eq("learner@example.com"), any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    void refreshTokenRequiresSessionHeaderAndReturnsMappedResponse() throws Exception {
        when(authService.refreshToken(any(RefreshTokenRequest.class), eq("session-1")))
                .thenReturn(AuthResponse.builder()
                        .accessToken("new-access-token")
                        .refreshToken("refresh-token")
                        .sessionId("session-1")
                        .build());

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRefreshBody()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("X-Session-Id", "session-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRefreshBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"));
    }

    @Test
    void refreshTokenMapsExpiredSessionToUnauthorized() throws Exception {
        when(authService.refreshToken(any(RefreshTokenRequest.class), eq("stale-session")))
                .thenThrow(new AppException(ErrorCode.SESSION_EXPIRED));

        mockMvc.perform(post("/api/auth/refresh-token")
                        .header("X-Session-Id", "stale-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRefreshBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void oauthSessionRequiresJwtAndUsesAuthenticatedPrincipal() throws Exception {
        when(authService.createOAuthSession(any()))
                .thenReturn(AuthResponse.builder().sessionId("oauth-session").build());

        mockMvc.perform(post("/api/auth/oauth/session"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/oauth/session").with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("oauth-session"));

        verify(authService).createOAuthSession(any());
    }

    @Test
    void logoutRequiresJwtAndHeadersThenReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Session-Id", "session-1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                        .with(userJwt())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .header("X-Session-Id", "session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout("Bearer access-token", USER_ID, "session-1");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor userJwt() {
        return jwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID)
                        .claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("auth-flow-user@example.com");
        user.setEmailVerified(true);
        user.setFullName("Auth Flow User");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private Jwt decodedJwt() {
        return Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(USER_ID)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("cognito:groups", List.of("LEARNER"))
                .build();
    }

    private String validRegisterBody() {
        return """
                {
                  "email": "learner@example.com",
                  "password": "password123",
                  "fullName": "Learner"
                }
                """;
    }

    private String validLoginBody() {
        return """
                {
                  "email": "learner@example.com",
                  "password": "password123"
                }
                """;
    }

    private String validRefreshBody() {
        return """
                {
                  "email": "learner@example.com",
                  "refreshToken": "refresh-token"
                }
                """;
    }
}
