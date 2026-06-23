package com.skillsprint.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skillsprint.dto.response.auth.AuthResponse;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;

class AuthMapperTest {

    AuthMapper authMapper;

    @BeforeEach
    void setUp() {
        authMapper = new AuthMapper();
    }

    @Test
    void toAuthResponseMapsTokensAndSession() {
        AuthenticationResultType result = AuthenticationResultType.builder()
                .accessToken("access-token")
                .idToken("id-token")
                .refreshToken("refresh-token")
                .expiresIn(3600)
                .tokenType("Bearer")
                .build();

        AuthResponse response = authMapper.toAuthResponse(result, "session-id");

        assertEquals("access-token", response.getAccessToken());
        assertEquals("id-token", response.getIdToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(3600, response.getExpiresIn());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("session-id", response.getSessionId());
    }

    @Test
    void toAuthResponseCanReuseExistingRefreshToken() {
        AuthenticationResultType result = AuthenticationResultType.builder()
                .accessToken("new-access-token")
                .build();

        AuthResponse response = authMapper.toAuthResponse(result, "session-id", "existing-refresh-token");

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("existing-refresh-token", response.getRefreshToken());
        assertEquals("session-id", response.getSessionId());
    }

    @Test
    void toNewPasswordRequiredResponseMapsChallengeAndSession() {
        AdminInitiateAuthResponse cognitoResponse = AdminInitiateAuthResponse.builder()
                .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                .session("challenge-session")
                .build();

        AuthResponse response = authMapper.toNewPasswordRequiredResponse(cognitoResponse);

        assertEquals("NEW_PASSWORD_REQUIRED", response.getChallengeName());
        assertEquals("challenge-session", response.getSession());
        assertNull(response.getAccessToken());
    }

    @Test
    void toCognitoUserProfileMapsAllAttributes() {
        AdminGetUserResponse cognitoResponse = cognitoUser(
                attribute("sub", "user-1"),
                attribute("email", "learner@example.com"),
                attribute("email_verified", "true"),
                attribute("name", "Learner")
        );

        AuthMapper.CognitoUserProfile profile =
                authMapper.toCognitoUserProfile(cognitoResponse, "fallback@example.com");

        assertEquals("user-1", profile.userId());
        assertEquals("learner@example.com", profile.email());
        assertTrue(profile.emailVerified());
        assertEquals("Learner", profile.fullName());
    }

    @Test
    void toCognitoUserProfileUsesFallbacksForOptionalAttributes() {
        AdminGetUserResponse cognitoResponse = cognitoUser(attribute("sub", "user-1"));

        AuthMapper.CognitoUserProfile profile =
                authMapper.toCognitoUserProfile(cognitoResponse, "fallback@example.com");

        assertEquals("fallback@example.com", profile.email());
        assertFalse(profile.emailVerified());
        assertEquals("fallback@example.com", profile.fullName());
    }

    @Test
    void toCognitoUserProfileRejectsMissingSubject() {
        AdminGetUserResponse cognitoResponse = cognitoUser(
                attribute("email", "learner@example.com")
        );

        AppException exception = assertThrows(
                AppException.class,
                () -> authMapper.toCognitoUserProfile(cognitoResponse, "fallback@example.com")
        );

        assertEquals(ErrorCode.COGNITO_ATTRIBUTE_MISSING, exception.getErrorCode());
    }

    private AdminGetUserResponse cognitoUser(AttributeType... attributes) {
        return AdminGetUserResponse.builder()
                .userAttributes(List.of(attributes))
                .build();
    }

    private AttributeType attribute(String name, String value) {
        return AttributeType.builder().name(name).value(value).build();
    }
}
