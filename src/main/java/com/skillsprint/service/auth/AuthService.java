package com.skillsprint.service.auth;

import com.skillsprint.configuration.cognito.CognitoProperties;
import com.skillsprint.configuration.cognito.CognitoSecretHashUtil;
import com.skillsprint.dto.request.auth.ConfirmForgotPasswordRequest;
import com.skillsprint.dto.request.auth.ConfirmRegisterRequest;
import com.skillsprint.dto.request.auth.CompleteNewPasswordRequest;
import com.skillsprint.dto.request.auth.ForgotPasswordRequest;
import com.skillsprint.dto.request.auth.LoginRequest;
import com.skillsprint.dto.request.auth.RegisterRequest;
import com.skillsprint.dto.request.auth.RefreshTokenRequest;
import com.skillsprint.dto.request.auth.ResendConfirmationCodeRequest;
import com.skillsprint.dto.response.auth.AuthResponse;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.AuthMapper;
import com.skillsprint.service.user.UserSyncService;
import com.skillsprint.service.system.SystemMaintenanceService;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import com.skillsprint.service.subscription.SubscriptionService;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    CognitoIdentityProviderClient cognitoClient;
    CognitoProperties cognitoProperties;
    UserSyncService userSyncService;
    AuthMapper authMapper;
    SubscriptionService subscriptionService;
    UserSessionService userSessionService;
    SystemMaintenanceService maintenanceService;

    public void register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        try {
            SignUpRequest.Builder signUpRequest = SignUpRequest.builder()
                    .clientId(cognitoProperties.clientId())
                    .username(email)
                    .password(request.getPassword())
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("name").value(request.getFullName()).build()
                    );
            putSecretHashIfNeeded(signUpRequest, email);

            cognitoClient.signUp(signUpRequest.build());
        } catch (UsernameExistsException ex) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    public void resendConfirmationCode(ResendConfirmationCodeRequest request) {
        String email = request.getEmail().trim().toLowerCase();

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
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        try {
            software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest.Builder forgotRequest =
                    software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest.builder()
                            .clientId(cognitoProperties.clientId())
                            .username(email);
            putSecretHashIfNeeded(forgotRequest, email);

            cognitoClient.forgotPassword(forgotRequest.build());
        } catch (UserNotFoundException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    public void confirmForgotPassword(ConfirmForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        try {
            software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest.Builder confirmRequest =
                    software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest.builder()
                            .clientId(cognitoProperties.clientId())
                            .username(email)
                            .confirmationCode(request.getConfirmationCode())
                            .password(request.getNewPassword());
            putSecretHashIfNeeded(confirmRequest, email);

            cognitoClient.confirmForgotPassword(confirmRequest.build());
        } catch (NotAuthorizedException | UserNotFoundException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    @Transactional
    public void confirmRegister(ConfirmRegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        try {
            ConfirmSignUpRequest.Builder confirmRequest = ConfirmSignUpRequest.builder()
                    .clientId(cognitoProperties.clientId())
                    .username(email)
                    .confirmationCode(request.getConfirmationCode());
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
            User user = syncLocalUser(cognitoUser, RoleName.LEARNER, email);
            subscriptionService.ensureDefaultFreeSubscription(user);
        } catch (CodeMismatchException | ExpiredCodeException ex) {
            throw new AppException(ErrorCode.INVALID_CONFIRMATION_CODE);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        try {
            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", email);
            authParams.put("PASSWORD", request.getPassword());
            putSecretHashIfNeeded(authParams, email);

            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(
                    AdminInitiateAuthRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .clientId(cognitoProperties.clientId())
                            .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                            .authParameters(authParams)
                            .build()
            );

            RoleName roleName = resolveRoleName(email);
            ensureLoginAllowedDuringMaintenance(roleName);

            if (ChallengeNameType.NEW_PASSWORD_REQUIRED.equals(response.challengeName())) {
                return authMapper.toNewPasswordRequiredResponse(response);
            }

            User user = syncLocalUserByEmail(email, roleName);
            ensureActiveUser(user);
            subscriptionService.ensureDefaultFreeSubscription(user);
            String sessionId = userSessionService.createSession(user.getUserId());
            return authMapper.toAuthResponse(response.authenticationResult(), sessionId);
        } catch (NotAuthorizedException | UserNotFoundException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        } catch (UserNotConfirmedException ex) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_CONFIRMED);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    @Transactional
    public AuthResponse completeNewPassword(CompleteNewPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        try {
            Map<String, String> challengeResponses = new HashMap<>();
            challengeResponses.put("USERNAME", email);
            challengeResponses.put("NEW_PASSWORD", request.getNewPassword());
            putSecretHashIfNeeded(challengeResponses, email);

            AdminRespondToAuthChallengeResponse response = cognitoClient.adminRespondToAuthChallenge(
                    AdminRespondToAuthChallengeRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .clientId(cognitoProperties.clientId())
                            .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                            .challengeResponses(challengeResponses)
                            .session(request.getSession())
                            .build()
            );

            RoleName roleName = resolveRoleName(email);
            ensureLoginAllowedDuringMaintenance(roleName);

            User user = syncLocalUserByEmail(email, roleName);
            ensureActiveUser(user);
            subscriptionService.ensureDefaultFreeSubscription(user);
            String sessionId = userSessionService.createSession(user.getUserId());
            return authMapper.toAuthResponse(response.authenticationResult(), sessionId);
        } catch (NotAuthorizedException | UserNotFoundException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String sessionId) {
        String email = request.getEmail().trim().toLowerCase();

        try {
            RoleName roleName = resolveRoleName(email);
            ensureLoginAllowedDuringMaintenance(roleName);

            User user = syncLocalUserByEmail(email, roleName);
            ensureActiveUser(user);

            if (!userSessionService.isCurrentSession(user.getUserId(), sessionId)) {
                throw new AppException(ErrorCode.SESSION_EXPIRED);
            }

            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", email);
            authParams.put("REFRESH_TOKEN", request.getRefreshToken());
            putSecretHashIfNeeded(authParams, email);

            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(
                    AdminInitiateAuthRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .clientId(cognitoProperties.clientId())
                            .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                            .authParameters(authParams)
                            .build()
            );

            if (response.authenticationResult() == null || response.authenticationResult().accessToken() == null) {
                throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            userSessionService.refreshSession(user.getUserId(), sessionId);
            return authMapper.toAuthResponse(response.authenticationResult(), sessionId, request.getRefreshToken());
        } catch (NotAuthorizedException | UserNotFoundException ex) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    public void logout(String authorizationHeader, String userId, String sessionId) {
        String accessToken = extractAccessToken(authorizationHeader);

        try {
            cognitoClient.globalSignOut(
                    GlobalSignOutRequest.builder()
                            .accessToken(accessToken)
                            .build()
            );
            userSessionService.invalidateSession(userId, sessionId);
        } catch (NotAuthorizedException ex) {
            userSessionService.invalidateSession(userId, sessionId);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    @Transactional
    public AuthResponse createOAuthSession(Jwt jwt) {
        String username = resolveCognitoUsername(jwt);
        String fallbackEmail = jwt.getClaimAsString("email");

        try {
            AdminGetUserResponse cognitoUser = cognitoClient.adminGetUser(
                    AdminGetUserRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .username(username)
                            .build()
            );

            RoleName roleName = resolveRoleName(username);
            ensureLoginAllowedDuringMaintenance(roleName);

            User user = syncLocalUser(cognitoUser, roleName, fallbackEmail);
            ensureActiveUser(user);
            subscriptionService.ensureDefaultFreeSubscription(user);

            String sessionId = userSessionService.createSession(user.getUserId());
            return AuthResponse.builder()
                    .sessionId(sessionId)
                    .build();
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    private String extractAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return accessToken;
    }

    private String resolveCognitoUsername(Jwt jwt) {
        String username = jwt.getClaimAsString("username");
        if (username == null || username.isBlank()) {
            username = jwt.getClaimAsString("cognito:username");
        }
        if (username != null && !username.isBlank()) {
            return username;
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AppException(ErrorCode.COGNITO_ATTRIBUTE_MISSING);
        }
        return subject;
    }

    private User syncLocalUserByEmail(String email) {
        return syncLocalUserByEmail(email, resolveRoleName(email));
    }

    private User syncLocalUserByEmail(String email, RoleName roleName) {
        AdminGetUserResponse cognitoUser = cognitoClient.adminGetUser(
                AdminGetUserRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId())
                        .username(email)
                        .build()
        );
        return syncLocalUser(cognitoUser, roleName, email);
    }

    private User syncLocalUser(AdminGetUserResponse cognitoUser, RoleName roleName, String fallbackEmail) {
        AuthMapper.CognitoUserProfile profile = authMapper.toCognitoUserProfile(cognitoUser, fallbackEmail);
        return userSyncService.syncWithRole(
                profile.userId(),
                profile.email(),
                profile.emailVerified(),
                profile.fullName(),
                roleName
        );
    }

    private void ensureActiveUser(User user) {
        if (UserStatus.DISABLED.equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private void ensureLoginAllowedDuringMaintenance(RoleName roleName) {
        if (RoleName.ADMIN.equals(roleName) || !maintenanceService.isMaintenanceActive()) {
            return;
        }
        throw new AppException(ErrorCode.MAINTENANCE_MODE, maintenanceService.getActiveMessage());
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

    private void putSecretHashIfNeeded(
            software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest.Builder builder,
            String username
    ) {
        if (cognitoProperties.hasClientSecret()) {
            builder.secretHash(calculateSecretHash(username));
        }
    }

    private void putSecretHashIfNeeded(
            software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest.Builder builder,
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

}
