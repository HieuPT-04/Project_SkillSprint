package com.skillsprint.service.auth;

import com.skillsprint.configuration.cognito.CognitoProperties;
import com.skillsprint.configuration.cognito.CognitoSecretHashUtil;
import com.skillsprint.dto.request.auth.ConfirmRegisterRequest;
import com.skillsprint.dto.request.auth.CompleteNewPasswordRequest;
import com.skillsprint.dto.request.auth.LoginRequest;
import com.skillsprint.dto.request.auth.RegisterRequest;
import com.skillsprint.dto.request.auth.ResendConfirmationCodeRequest;
import com.skillsprint.dto.response.auth.AuthResponse;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.service.user.UserSyncService;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    CognitoIdentityProviderClient cognitoClient;
    CognitoProperties cognitoProperties;
    UserSyncService userSyncService;

    public void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        try {
            SignUpRequest.Builder signUpRequest = SignUpRequest.builder()
                    .clientId(cognitoProperties.clientId())
                    .username(email)
                    .password(request.password())
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("name").value(request.fullName()).build()
                    );
            putSecretHashIfNeeded(signUpRequest, email);

            cognitoClient.signUp(signUpRequest.build());
        } catch (UsernameExistsException ex) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_ERROR, ex.awsErrorDetails().errorMessage());
        }
    }

    public void resendConfirmationCode(ResendConfirmationCodeRequest request) {
        String email = request.email().trim().toLowerCase();

        try {
            software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest.Builder resendRequest =
                    software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest.builder()
                            .clientId(cognitoProperties.clientId())
                            .username(email);
            putSecretHashIfNeeded(resendRequest, email);

            cognitoClient.resendConfirmationCode(resendRequest.build());
        } catch (UserNotFoundException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_ERROR, ex.awsErrorDetails().errorMessage());
        }
    }

    @Transactional
    public void confirmRegister(ConfirmRegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        try {
            ConfirmSignUpRequest.Builder confirmRequest = ConfirmSignUpRequest.builder()
                    .clientId(cognitoProperties.clientId())
                    .username(email)
                    .confirmationCode(request.confirmationCode());
            putSecretHashIfNeeded(confirmRequest, email);

            cognitoClient.confirmSignUp(confirmRequest.build());

            cognitoClient.adminAddUserToGroup(
                    AdminAddUserToGroupRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .username(email)
                            .groupName(cognitoProperties.defaultGroup())
                            .build()
            );

            AdminGetUserResponse cognitoUser = cognitoClient.adminGetUser(
                    AdminGetUserRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .username(email)
                            .build()
            );
            syncLocalUser(cognitoUser, RoleName.LEARNER, email);
        } catch (CodeMismatchException | ExpiredCodeException ex) {
            throw new AppException(ErrorCode.INVALID_CONFIRMATION_CODE);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_ERROR, ex.awsErrorDetails().errorMessage());
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        try {
            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", email);
            authParams.put("PASSWORD", request.password());
            putSecretHashIfNeeded(authParams, email);

            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(
                    AdminInitiateAuthRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .clientId(cognitoProperties.clientId())
                            .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                            .authParameters(authParams)
                            .build()
            );

            if (ChallengeNameType.NEW_PASSWORD_REQUIRED.equals(response.challengeName())) {
                return AuthResponse.builder()
                        .challengeName(response.challengeNameAsString())
                        .session(response.session())
                        .build();
            }

            syncLocalUserByEmail(email);
            return toAuthResponse(response.authenticationResult());
        } catch (NotAuthorizedException | UserNotFoundException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        } catch (UserNotConfirmedException ex) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_CONFIRMED);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_ERROR, ex.awsErrorDetails().errorMessage());
        }
    }

    @Transactional
    public AuthResponse completeNewPassword(CompleteNewPasswordRequest request) {
        String email = request.email().trim().toLowerCase();

        try {
            Map<String, String> challengeResponses = new HashMap<>();
            challengeResponses.put("USERNAME", email);
            challengeResponses.put("NEW_PASSWORD", request.newPassword());
            putSecretHashIfNeeded(challengeResponses, email);

            AdminRespondToAuthChallengeResponse response = cognitoClient.adminRespondToAuthChallenge(
                    AdminRespondToAuthChallengeRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .clientId(cognitoProperties.clientId())
                            .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                            .challengeResponses(challengeResponses)
                            .session(request.session())
                            .build()
            );

            syncLocalUserByEmail(email);
            return toAuthResponse(response.authenticationResult());
        } catch (NotAuthorizedException | UserNotFoundException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_ERROR, ex.awsErrorDetails().errorMessage());
        }
    }

    private void syncLocalUserByEmail(String email) {
        AdminGetUserResponse cognitoUser = cognitoClient.adminGetUser(
                AdminGetUserRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId())
                        .username(email)
                        .build()
        );
        syncLocalUser(cognitoUser, resolveRoleName(email), email);
    }

    private AuthResponse toAuthResponse(
            software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType result
    ) {
        return AuthResponse.builder()
                .accessToken(result.accessToken())
                .idToken(result.idToken())
                .refreshToken(result.refreshToken())
                .expiresIn(result.expiresIn())
                .tokenType(result.tokenType())
                .build();
    }

    private void syncLocalUser(AdminGetUserResponse cognitoUser, RoleName roleName, String fallbackEmail) {
        String userId = getAttribute(cognitoUser.userAttributes(), "sub");
        String email = getAttributeOrDefault(cognitoUser.userAttributes(), "email", fallbackEmail);
        String fullName = getAttributeOrDefault(cognitoUser.userAttributes(), "name", email);
        String avatarUrl = getAttributeOrDefault(cognitoUser.userAttributes(), "picture", null);
        userSyncService.syncWithRole(userId, email, fullName, avatarUrl, roleName);
    }

    private void putSecretHashIfNeeded(Map<String, String> authParams, String username) {
        if (!cognitoProperties.hasClientSecret()) {
            return;
        }

        String secretHash = CognitoSecretHashUtil.calculateSecretHash(
                username,
                cognitoProperties.clientId(),
                cognitoProperties.clientSecret()
        );
        authParams.put("SECRET_HASH", secretHash);
    }

    private void putSecretHashIfNeeded(SignUpRequest.Builder builder, String username) {
        if (cognitoProperties.hasClientSecret()) {
            builder.secretHash(calculateSecretHash(username));
        }
    }

    private void putSecretHashIfNeeded(ConfirmSignUpRequest.Builder builder, String username) {
        if (cognitoProperties.hasClientSecret()) {
            builder.secretHash(calculateSecretHash(username));
        }
    }

    private void putSecretHashIfNeeded(
            software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest.Builder builder,
            String username
    ) {
        if (cognitoProperties.hasClientSecret()) {
            builder.secretHash(calculateSecretHash(username));
        }
    }

    private String calculateSecretHash(String username) {
        return CognitoSecretHashUtil.calculateSecretHash(
                username,
                cognitoProperties.clientId(),
                cognitoProperties.clientSecret()
        );
    }

    private RoleName resolveRoleName(String username) {
        var response = cognitoClient.adminListGroupsForUser(
                AdminListGroupsForUserRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId())
                        .username(username)
                        .build()
        );

        return response.groups()
                .stream()
                .map(GroupType::groupName)
                .filter(RoleName.ADMIN.name()::equals)
                .findFirst()
                .map(group -> RoleName.ADMIN)
                .orElse(RoleName.LEARNER);
    }

    private String getAttribute(Iterable<AttributeType> attributes, String name) {
        for (AttributeType attribute : attributes) {
            if (name.equals(attribute.name())) {
                return attribute.value();
            }
        }
        throw new AppException(ErrorCode.COGNITO_ERROR, "Cognito user attribute is missing: " + name);
    }

    private String getAttributeOrDefault(Iterable<AttributeType> attributes, String name, String defaultValue) {
        for (AttributeType attribute : attributes) {
            if (name.equals(attribute.name())) {
                return attribute.value();
            }
        }
        return defaultValue;
    }

}
