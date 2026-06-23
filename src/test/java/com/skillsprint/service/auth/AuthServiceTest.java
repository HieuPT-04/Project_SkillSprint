package com.skillsprint.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.configuration.cognito.CognitoProperties;
import com.skillsprint.dto.request.auth.ConfirmRegisterRequest;
import com.skillsprint.dto.request.auth.LoginRequest;
import com.skillsprint.dto.request.auth.RefreshTokenRequest;
import com.skillsprint.dto.request.auth.RegisterRequest;
import com.skillsprint.dto.response.auth.AuthResponse;
import com.skillsprint.dto.response.common.SystemStatusResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.AuthMapper;
import com.skillsprint.service.subscription.SubscriptionService;
import com.skillsprint.service.system.SystemMaintenanceService;
import com.skillsprint.service.user.UserSyncService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "learner@example.com";

    @Mock
    CognitoIdentityProviderClient cognitoClient;

    @Mock
    UserSyncService userSyncService;

    @Mock
    AuthMapper authMapper;

    @Mock
    SubscriptionService subscriptionService;

    @Mock
    UserSessionService userSessionService;

    @Mock
    SystemMaintenanceService maintenanceService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = service(properties(null));
    }

    @Test
    void registerNormalizesEmailAndSendsExpectedCognitoAttributes() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("  LEARNER@Example.COM ");
        request.setPassword("password123");
        request.setFullName("Learner");

        authService.register(request);

        ArgumentCaptor<SignUpRequest> captor = ArgumentCaptor.forClass(SignUpRequest.class);
        verify(cognitoClient).signUp(captor.capture());
        SignUpRequest cognitoRequest = captor.getValue();
        assertEquals(EMAIL, cognitoRequest.username());
        assertEquals("password123", cognitoRequest.password());
        assertEquals("test-client", cognitoRequest.clientId());
        assertTrue(cognitoRequest.userAttributes().stream()
                .anyMatch(attribute -> "email".equals(attribute.name()) && EMAIL.equals(attribute.value())));
        assertTrue(cognitoRequest.userAttributes().stream()
                .anyMatch(attribute -> "name".equals(attribute.name()) && "Learner".equals(attribute.value())));
    }

    @Test
    void registerAddsSecretHashWhenClientSecretIsConfigured() {
        authService = service(properties("client-secret"));
        RegisterRequest request = new RegisterRequest();
        request.setEmail(EMAIL);
        request.setPassword("password123");
        request.setFullName("Learner");

        authService.register(request);

        ArgumentCaptor<SignUpRequest> captor = ArgumentCaptor.forClass(SignUpRequest.class);
        verify(cognitoClient).signUp(captor.capture());
        assertTrue(captor.getValue().secretHash() != null && !captor.getValue().secretHash().isBlank());
    }

    @Test
    void registerMapsUsernameExistsAndGenericCognitoErrors() {
        RegisterRequest request = registerRequest();
        when(cognitoClient.signUp(any(SignUpRequest.class)))
                .thenThrow(UsernameExistsException.builder().message("exists").build())
                .thenThrow(CognitoIdentityProviderException.builder().message("down").build());

        assertError(ErrorCode.USER_ALREADY_EXISTS, () -> authService.register(request));
        assertError(ErrorCode.COGNITO_SERVICE_ERROR, () -> authService.register(request));
    }

    @Test
    void confirmRegisterConfirmsAddsLearnerGroupSyncsUserAndSubscription() {
        ConfirmRegisterRequest request = new ConfirmRegisterRequest();
        request.setEmail(" LEARNER@EXAMPLE.COM ");
        request.setConfirmationCode("123456");
        AdminGetUserResponse cognitoUser = cognitoUser("user-1", EMAIL, "Learner");
        User user = activeUser("user-1");
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(cognitoUser);
        when(authMapper.toCognitoUserProfile(cognitoUser, EMAIL))
                .thenReturn(new AuthMapper.CognitoUserProfile("user-1", EMAIL, true, "Learner"));
        when(userSyncService.syncWithRole("user-1", EMAIL, true, "Learner", RoleName.LEARNER))
                .thenReturn(user);

        authService.confirmRegister(request);

        ArgumentCaptor<ConfirmSignUpRequest> confirmCaptor = ArgumentCaptor.forClass(ConfirmSignUpRequest.class);
        verify(cognitoClient).confirmSignUp(confirmCaptor.capture());
        assertEquals(EMAIL, confirmCaptor.getValue().username());

        ArgumentCaptor<AdminAddUserToGroupRequest> groupCaptor =
                ArgumentCaptor.forClass(AdminAddUserToGroupRequest.class);
        verify(cognitoClient).adminAddUserToGroup(groupCaptor.capture());
        assertEquals("LEARNER", groupCaptor.getValue().groupName());
        verify(subscriptionService).ensureDefaultFreeSubscription(user);
    }

    @Test
    void confirmRegisterMapsInvalidCode() {
        ConfirmRegisterRequest request = new ConfirmRegisterRequest();
        request.setEmail(EMAIL);
        request.setConfirmationCode("bad");
        when(cognitoClient.confirmSignUp(any(ConfirmSignUpRequest.class)))
                .thenThrow(CodeMismatchException.builder().message("bad code").build());

        assertError(ErrorCode.INVALID_CONFIRMATION_CODE, () -> authService.confirmRegister(request));
    }

    @Test
    void loginLearnerSyncsUserCreatesSessionAndReturnsTokens() {
        AuthenticationResultType result = tokens();
        AuthResponse expected = AuthResponse.builder().accessToken("access").sessionId("session-1").build();
        User user = activeUser("user-1");
        stubLoginCognito(result, List.of(), user);
        when(userSessionService.createSession("user-1")).thenReturn("session-1");
        when(authMapper.toAuthResponse(result, "session-1")).thenReturn(expected);

        AuthResponse response = authService.login(loginRequest());

        assertEquals(expected, response);
        verify(subscriptionService).ensureDefaultFreeSubscription(user);
        ArgumentCaptor<AdminInitiateAuthRequest> captor = ArgumentCaptor.forClass(AdminInitiateAuthRequest.class);
        verify(cognitoClient).adminInitiateAuth(captor.capture());
        assertEquals(AuthFlowType.ADMIN_USER_PASSWORD_AUTH, captor.getValue().authFlow());
        assertEquals(EMAIL, captor.getValue().authParameters().get("USERNAME"));
    }

    @Test
    void loginReturnsNewPasswordChallengeWithoutSyncingUser() {
        AdminInitiateAuthResponse cognitoResponse = AdminInitiateAuthResponse.builder()
                .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                .session("challenge-session")
                .build();
        AuthResponse expected = AuthResponse.builder()
                .challengeName("NEW_PASSWORD_REQUIRED")
                .session("challenge-session")
                .build();
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class))).thenReturn(cognitoResponse);
        when(cognitoClient.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class)))
                .thenReturn(AdminListGroupsForUserResponse.builder().groups(List.of()).build());
        when(maintenanceService.getSystemStatus()).thenReturn(maintenance(false));
        when(authMapper.toNewPasswordRequiredResponse(cognitoResponse)).thenReturn(expected);

        assertEquals(expected, authService.login(loginRequest()));
        verify(cognitoClient, never()).adminGetUser(any(AdminGetUserRequest.class));
        verify(userSessionService, never()).createSession(any());
    }

    @Test
    void loginBlocksLearnerButAllowsAdminDuringMaintenance() {
        AuthenticationResultType result = tokens();
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenReturn(AdminInitiateAuthResponse.builder().authenticationResult(result).build());
        when(maintenanceService.getSystemStatus()).thenReturn(maintenance(true));
        when(cognitoClient.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class)))
                .thenReturn(AdminListGroupsForUserResponse.builder().groups(List.of()).build())
                .thenReturn(AdminListGroupsForUserResponse.builder()
                        .groups(GroupType.builder().groupName("ADMIN").build())
                        .build());

        assertError(ErrorCode.MAINTENANCE_MODE, () -> authService.login(loginRequest()));

        User admin = activeUser("admin-1");
        AdminGetUserResponse cognitoUser = cognitoUser("admin-1", EMAIL, "Admin");
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(cognitoUser);
        when(authMapper.toCognitoUserProfile(cognitoUser, EMAIL))
                .thenReturn(new AuthMapper.CognitoUserProfile("admin-1", EMAIL, true, "Admin"));
        when(userSyncService.syncWithRole("admin-1", EMAIL, true, "Admin", RoleName.ADMIN))
                .thenReturn(admin);
        when(userSessionService.createSession("admin-1")).thenReturn("admin-session");
        AuthResponse expected = AuthResponse.builder().accessToken("access").build();
        when(authMapper.toAuthResponse(result, "admin-session")).thenReturn(expected);

        assertEquals(expected, authService.login(loginRequest()));
    }

    @Test
    void loginRejectsDisabledLocalUser() {
        User disabled = activeUser("user-1");
        disabled.setStatus(UserStatus.DISABLED);
        stubLoginCognito(tokens(), List.of(), disabled);

        assertError(ErrorCode.ACCOUNT_DISABLED, () -> authService.login(loginRequest()));
        verify(userSessionService, never()).createSession(any());
    }

    @Test
    void loginMapsCredentialConfirmationAndServiceErrors() {
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("bad").build())
                .thenThrow(UserNotConfirmedException.builder().message("not confirmed").build())
                .thenThrow(CognitoIdentityProviderException.builder().message("down").build());

        assertError(ErrorCode.INVALID_CREDENTIALS, () -> authService.login(loginRequest()));
        assertError(ErrorCode.ACCOUNT_NOT_CONFIRMED, () -> authService.login(loginRequest()));
        assertError(ErrorCode.COGNITO_SERVICE_ERROR, () -> authService.login(loginRequest()));
    }

    @Test
    void refreshTokenRejectsExpiredSessionBeforeCallingRefreshFlow() {
        User user = activeUser("user-1");
        stubResolvedLocalUser(List.of(), user);
        when(userSessionService.isCurrentSession("user-1", "stale-session")).thenReturn(false);

        assertError(
                ErrorCode.SESSION_EXPIRED,
                () -> authService.refreshToken(refreshRequest(), "stale-session")
        );

        verify(cognitoClient, never()).adminInitiateAuth(any(AdminInitiateAuthRequest.class));
    }

    @Test
    void refreshTokenReturnsNewAccessTokenAndKeepsExistingRefreshToken() {
        User user = activeUser("user-1");
        stubResolvedLocalUser(List.of(), user);
        when(userSessionService.isCurrentSession("user-1", "session-1")).thenReturn(true);
        AuthenticationResultType result = AuthenticationResultType.builder()
                .accessToken("new-access")
                .expiresIn(3600)
                .build();
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenReturn(AdminInitiateAuthResponse.builder().authenticationResult(result).build());
        AuthResponse expected = AuthResponse.builder()
                .accessToken("new-access")
                .refreshToken("refresh-token")
                .sessionId("session-1")
                .build();
        when(authMapper.toAuthResponse(result, "session-1", "refresh-token")).thenReturn(expected);

        AuthResponse response = authService.refreshToken(refreshRequest(), "session-1");

        assertEquals(expected, response);
        verify(userSessionService).refreshSession("user-1", "session-1");
        ArgumentCaptor<AdminInitiateAuthRequest> captor = ArgumentCaptor.forClass(AdminInitiateAuthRequest.class);
        verify(cognitoClient).adminInitiateAuth(captor.capture());
        assertEquals(AuthFlowType.REFRESH_TOKEN_AUTH, captor.getValue().authFlow());
    }

    @Test
    void refreshTokenRejectsMissingAuthenticationResult() {
        User user = activeUser("user-1");
        stubResolvedLocalUser(List.of(), user);
        when(userSessionService.isCurrentSession("user-1", "session-1")).thenReturn(true);
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenReturn(AdminInitiateAuthResponse.builder().build());

        assertError(
                ErrorCode.INVALID_REFRESH_TOKEN,
                () -> authService.refreshToken(refreshRequest(), "session-1")
        );
    }

    @Test
    void logoutValidatesBearerHeaderAndInvalidatesSessionOnSuccess() {
        assertError(
                ErrorCode.UNAUTHORIZED,
                () -> authService.logout("Basic token", "user-1", "session-1")
        );

        authService.logout("Bearer access-token", "user-1", "session-1");

        ArgumentCaptor<GlobalSignOutRequest> captor = ArgumentCaptor.forClass(GlobalSignOutRequest.class);
        verify(cognitoClient).globalSignOut(captor.capture());
        assertEquals("access-token", captor.getValue().accessToken());
        verify(userSessionService).invalidateSession("user-1", "session-1");
    }

    @Test
    void logoutInvalidatesLocalSessionWhenCognitoTokenIsAlreadyInvalid() {
        when(cognitoClient.globalSignOut(any(GlobalSignOutRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("invalid").build());

        assertError(
                ErrorCode.UNAUTHORIZED,
                () -> authService.logout("Bearer access-token", "user-1", "session-1")
        );

        verify(userSessionService).invalidateSession("user-1", "session-1");
    }

    @Test
    void createOAuthSessionUsesUsernameClaimAndCreatesLocalSession() {
        Jwt jwt = jwt("subject-1", "cognito-user", "oauth@example.com");
        AdminGetUserResponse cognitoUser = cognitoUser("user-1", "oauth@example.com", "OAuth User");
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(cognitoUser);
        when(cognitoClient.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class)))
                .thenReturn(AdminListGroupsForUserResponse.builder().groups(List.of()).build());
        when(maintenanceService.getSystemStatus()).thenReturn(maintenance(false));
        when(authMapper.toCognitoUserProfile(cognitoUser, "oauth@example.com"))
                .thenReturn(new AuthMapper.CognitoUserProfile(
                        "user-1",
                        "oauth@example.com",
                        true,
                        "OAuth User"
                ));
        User user = activeUser("user-1");
        when(userSyncService.syncWithRole(
                "user-1",
                "oauth@example.com",
                true,
                "OAuth User",
                RoleName.LEARNER
        )).thenReturn(user);
        when(userSessionService.createSession("user-1")).thenReturn("oauth-session");

        AuthResponse response = authService.createOAuthSession(jwt);

        assertEquals("oauth-session", response.getSessionId());
        ArgumentCaptor<AdminGetUserRequest> captor = ArgumentCaptor.forClass(AdminGetUserRequest.class);
        verify(cognitoClient).adminGetUser(captor.capture());
        assertEquals("cognito-user", captor.getValue().username());
        verify(subscriptionService).ensureDefaultFreeSubscription(user);
    }

    @Test
    void createOAuthSessionFallsBackToSubjectAndRejectsMissingIdentity() {
        Jwt subjectJwt = jwt("subject-1", null, "oauth@example.com");
        AdminGetUserResponse cognitoUser = cognitoUser("user-1", "oauth@example.com", "OAuth User");
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(cognitoUser);
        when(cognitoClient.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class)))
                .thenReturn(AdminListGroupsForUserResponse.builder().groups(List.of()).build());
        when(maintenanceService.getSystemStatus()).thenReturn(maintenance(false));
        when(authMapper.toCognitoUserProfile(cognitoUser, "oauth@example.com"))
                .thenReturn(new AuthMapper.CognitoUserProfile(
                        "user-1",
                        "oauth@example.com",
                        true,
                        "OAuth User"
                ));
        when(userSyncService.syncWithRole(any(), any(), eq(true), any(), eq(RoleName.LEARNER)))
                .thenReturn(activeUser("user-1"));

        authService.createOAuthSession(subjectJwt);

        ArgumentCaptor<AdminGetUserRequest> captor = ArgumentCaptor.forClass(AdminGetUserRequest.class);
        verify(cognitoClient).adminGetUser(captor.capture());
        assertEquals("subject-1", captor.getValue().username());

        Jwt missingIdentity = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("email", "oauth@example.com")
                .build();
        assertError(
                ErrorCode.COGNITO_ATTRIBUTE_MISSING,
                () -> authService.createOAuthSession(missingIdentity)
        );
    }

    private AuthService service(CognitoProperties properties) {
        return new AuthService(
                cognitoClient,
                properties,
                userSyncService,
                authMapper,
                subscriptionService,
                userSessionService,
                maintenanceService
        );
    }

    private CognitoProperties properties(String clientSecret) {
        return new CognitoProperties(
                "ap-southeast-1",
                "test-pool",
                "test-client",
                clientSecret,
                "LEARNER",
                null,
                null
        );
    }

    private void stubLoginCognito(
            AuthenticationResultType result,
            List<GroupType> groups,
            User user
    ) {
        when(cognitoClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
                .thenReturn(AdminInitiateAuthResponse.builder().authenticationResult(result).build());
        stubResolvedLocalUser(groups, user);
    }

    private void stubResolvedLocalUser(List<GroupType> groups, User user) {
        when(cognitoClient.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class)))
                .thenReturn(AdminListGroupsForUserResponse.builder().groups(groups).build());
        when(maintenanceService.getSystemStatus()).thenReturn(maintenance(false));
        AdminGetUserResponse cognitoUser = cognitoUser(user.getUserId(), EMAIL, "Learner");
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(cognitoUser);
        RoleName roleName = groups.stream()
                .anyMatch(group -> "ADMIN".equals(group.groupName()))
                ? RoleName.ADMIN
                : RoleName.LEARNER;
        when(authMapper.toCognitoUserProfile(cognitoUser, EMAIL))
                .thenReturn(new AuthMapper.CognitoUserProfile(
                        user.getUserId(),
                        EMAIL,
                        true,
                        "Learner"
                ));
        when(userSyncService.syncWithRole(
                user.getUserId(),
                EMAIL,
                true,
                "Learner",
                roleName
        )).thenReturn(user);
    }

    private AuthenticationResultType tokens() {
        return AuthenticationResultType.builder()
                .accessToken("access")
                .idToken("id")
                .refreshToken("refresh")
                .expiresIn(3600)
                .tokenType("Bearer")
                .build();
    }

    private AdminGetUserResponse cognitoUser(String userId, String email, String fullName) {
        return AdminGetUserResponse.builder()
                .username(email)
                .userAttributes(
                        AttributeType.builder().name("sub").value(userId).build(),
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("name").value(fullName).build()
                )
                .build();
    }

    private User activeUser(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(EMAIL);
        user.setFullName("Learner");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail(" LEARNER@EXAMPLE.COM ");
        request.setPassword("password123");
        return request;
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(EMAIL);
        request.setPassword("password123");
        request.setFullName("Learner");
        return request;
    }

    private RefreshTokenRequest refreshRequest() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setEmail(EMAIL);
        request.setRefreshToken("refresh-token");
        return request;
    }

    private SystemStatusResponse maintenance(boolean active) {
        return SystemStatusResponse.builder()
                .enabled(active)
                .maintenance(active)
                .message("Maintenance")
                .build();
    }

    private Jwt jwt(String subject, String username, String email) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("email", email);
        if (username != null) {
            builder.claim("username", username);
        }
        return builder.build();
    }

    private void assertError(ErrorCode expected, org.junit.jupiter.api.function.Executable executable) {
        AppException exception = assertThrows(AppException.class, executable);
        assertEquals(expected, exception.getErrorCode());
    }
}
