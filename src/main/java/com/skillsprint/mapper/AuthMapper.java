package com.skillsprint.mapper;

import com.skillsprint.dto.response.auth.AuthResponse;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

@Component
public class AuthMapper {

    private static final String ATTRIBUTE_SUB = "sub";
    private static final String ATTRIBUTE_EMAIL = "email";
    private static final String ATTRIBUTE_EMAIL_VERIFIED = "email_verified";
    private static final String ATTRIBUTE_NAME = "name";

    public AuthResponse toAuthResponse(AuthenticationResultType result) {
        return toAuthResponse(result, null);
    }

    public AuthResponse toAuthResponse(AuthenticationResultType result, String sessionId) {
        return AuthResponse.builder()
                .accessToken(result.accessToken())
                .idToken(result.idToken())
                .refreshToken(result.refreshToken())
                .expiresIn(result.expiresIn())
                .tokenType(result.tokenType())
                .sessionId(sessionId)
                .build();
    }

    public AuthResponse toNewPasswordRequiredResponse(AdminInitiateAuthResponse response) {
        return AuthResponse.builder()
                .challengeName(response.challengeNameAsString())
                .session(response.session())
                .build();
    }

    public CognitoUserProfile toCognitoUserProfile(AdminGetUserResponse response, String fallbackEmail) {
        Map<String, String> attributes = toAttributeMap(response);
        String email = attributes.getOrDefault(ATTRIBUTE_EMAIL, fallbackEmail);

        return new CognitoUserProfile(
                getRequiredAttribute(attributes, ATTRIBUTE_SUB),
                email,
                Boolean.parseBoolean(attributes.getOrDefault(ATTRIBUTE_EMAIL_VERIFIED, "false")),
                attributes.getOrDefault(ATTRIBUTE_NAME, email)
        );
    }

    private Map<String, String> toAttributeMap(AdminGetUserResponse response) {
        return response.userAttributes()
                .stream()
                .collect(Collectors.toMap(AttributeType::name, AttributeType::value));
    }

    private String getRequiredAttribute(Map<String, String> attributes, String name) {
        String value = attributes.get(name);
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.COGNITO_ATTRIBUTE_MISSING);
        }
        return value;
    }

    public record CognitoUserProfile(
            String userId,
            String email,
            boolean emailVerified,
            String fullName
    ) {
    }
}
