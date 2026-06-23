package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.user.ConfirmAvatarUploadRequest;
import com.skillsprint.dto.request.user.CreateAvatarUploadUrlRequest;
import com.skillsprint.dto.request.user.UpdateMeRequest;
import com.skillsprint.dto.request.workspace.UpsertOnboardingProfileRequest;
import com.skillsprint.dto.response.points.LeaderboardEntryResponse;
import com.skillsprint.dto.response.points.LeaderboardResponse;
import com.skillsprint.dto.response.points.MyPointsResponse;
import com.skillsprint.dto.response.points.PointEventResponse;
import com.skillsprint.dto.response.user.AvatarUploadUrlResponse;
import com.skillsprint.dto.response.user.MeResponse;
import com.skillsprint.dto.response.workspace.OnboardingProfileResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.calendar.WeekDay;
import com.skillsprint.enums.points.LeaderboardPeriod;
import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.enums.points.PointSourceType;
import com.skillsprint.enums.workspace.ConfidenceLevel;
import com.skillsprint.enums.workspace.PreferredLanguage;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.points.PointService;
import com.skillsprint.service.storage.S3PresignedUrlService;
import com.skillsprint.service.user.UserQueryService;
import com.skillsprint.service.workspace.OnboardingProfileService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserFacingApiFlowTest {

    private static final String USER_ID = "user-facing-flow";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    PointService pointService;

    @MockBean
    OnboardingProfileService onboardingProfileService;

    @MockBean
    UserQueryService userQueryService;

    @MockBean
    S3PresignedUrlService s3PresignedUrlService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID workspaceId;
    UUID profileId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        profileId = UUID.randomUUID();
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void anonymousUserCannotUseUserFacingProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/leaderboard/weekly"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/leaderboard/weekly"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/onboarding", workspaceId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(pointService, never()).getLeaderboard(any(), any(Integer.class));
        verify(onboardingProfileService, never()).getOnboardingProfile(any(), any());
        verify(userQueryService, never()).getMe(any());
    }

    @Test
    void authenticatedUserCanReadLeaderboardsPointsAndEvents() throws Exception {
        when(pointService.getLeaderboard(LeaderboardPeriod.WEEKLY, 5)).thenReturn(leaderboard(LeaderboardPeriod.WEEKLY));
        when(pointService.getLeaderboard(LeaderboardPeriod.MONTHLY, 10)).thenReturn(leaderboard(LeaderboardPeriod.MONTHLY));
        when(pointService.getLeaderboard(LeaderboardPeriod.ALL_TIME, 20)).thenReturn(leaderboard(LeaderboardPeriod.ALL_TIME));
        when(pointService.getMyPoints(USER_ID)).thenReturn(myPoints());
        when(pointService.getMyPointEvents(USER_ID)).thenReturn(List.of(pointEvent()));

        mockMvc.perform(get("/api/leaderboard/weekly")
                        .with(userJwt())
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy bảng xếp hạng tuần thành công"))
                .andExpect(jsonPath("$.data.period").value("WEEKLY"))
                .andExpect(jsonPath("$.data.entries[0].rank").value(1))
                .andExpect(jsonPath("$.data.entries[0].userId").value(USER_ID));

        mockMvc.perform(get("/api/leaderboard/monthly")
                        .with(userJwt())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy bảng xếp hạng tháng thành công"))
                .andExpect(jsonPath("$.data.period").value("MONTHLY"));

        mockMvc.perform(get("/api/leaderboard/all-time").with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy bảng xếp hạng tổng thành công"))
                .andExpect(jsonPath("$.data.period").value("ALL_TIME"));

        mockMvc.perform(get("/api/leaderboard/me").with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy điểm của tôi thành công"))
                .andExpect(jsonPath("$.data.totalPoints").value(900))
                .andExpect(jsonPath("$.data.weeklyRank").value(3));

        mockMvc.perform(get("/api/leaderboard/me/events").with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy lịch sử điểm thành công"))
                .andExpect(jsonPath("$.data[0].eventType").value("QUIZ_PASSED"))
                .andExpect(jsonPath("$.data[0].sourceType").value("QUIZ"))
                .andExpect(jsonPath("$.data[0].points").value(30));

        verify(pointService).getLeaderboard(LeaderboardPeriod.WEEKLY, 5);
        verify(pointService).getLeaderboard(LeaderboardPeriod.MONTHLY, 10);
        verify(pointService).getLeaderboard(LeaderboardPeriod.ALL_TIME, 20);
        verify(pointService).getMyPoints(USER_ID);
        verify(pointService).getMyPointEvents(USER_ID);
    }

    @Test
    void authenticatedUserCanUpsertAndReadOnboardingProfile() throws Exception {
        when(onboardingProfileService.upsertOnboardingProfile(
                eq(USER_ID),
                eq(workspaceId),
                any(UpsertOnboardingProfileRequest.class)
        )).thenReturn(onboardingProfile());
        when(onboardingProfileService.getOnboardingProfile(USER_ID, workspaceId)).thenReturn(onboardingProfile());

        mockMvc.perform(put("/api/workspaces/{workspaceId}/onboarding", workspaceId)
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOnboardingJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lưu thiết lập học tập thành công"))
                .andExpect(jsonPath("$.data.profileId").value(profileId.toString()))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.targetGoal").value("Learn Java backend"))
                .andExpect(jsonPath("$.data.confidence").value("LOW"))
                .andExpect(jsonPath("$.data.preferredDays[0]").value("MONDAY"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/onboarding", workspaceId).with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Thành công"))
                .andExpect(jsonPath("$.data.profileId").value(profileId.toString()))
                .andExpect(jsonPath("$.data.preferredLanguage").value("vi"));

        verify(onboardingProfileService).upsertOnboardingProfile(
                eq(USER_ID),
                eq(workspaceId),
                any(UpsertOnboardingProfileRequest.class)
        );
        verify(onboardingProfileService).getOnboardingProfile(USER_ID, workspaceId);
    }

    @Test
    void onboardingValidationAndBusinessErrorsAreMapped() throws Exception {
        mockMvc.perform(put("/api/workspaces/{workspaceId}/onboarding", workspaceId)
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetGoal": "   ",
                                  "confidence": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        when(onboardingProfileService.getOnboardingProfile(USER_ID, workspaceId))
                .thenThrow(new AppException(ErrorCode.ONBOARDING_PROFILE_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/onboarding", workspaceId).with(userJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));

        verify(onboardingProfileService, never()).upsertOnboardingProfile(any(), any(), any());
    }

    @Test
    void authenticatedUserCanReadUpdateAndManageAvatar() throws Exception {
        String objectKey = "avatars/%s/avatar.png".formatted(USER_ID);
        when(userQueryService.getMe(USER_ID)).thenReturn(meResponse("Skill Sprinter", "https://cdn.example/avatar.png"));
        when(userQueryService.updateMe(eq(USER_ID), any(UpdateMeRequest.class)))
                .thenReturn(meResponse("Updated Sprinter", "https://cdn.example/avatar.png"));
        when(s3PresignedUrlService.createAvatarUploadUrl(eq(USER_ID), any(CreateAvatarUploadUrlRequest.class)))
                .thenReturn(AvatarUploadUrlResponse.builder()
                        .uploadUrl("https://s3.example/upload")
                        .fileUrl("https://cdn.example/avatar.png")
                        .objectKey(objectKey)
                        .expiresAt(Instant.parse("2026-06-23T10:15:00Z"))
                        .build());
        when(s3PresignedUrlService.confirmAvatarUpload(eq(USER_ID), any(ConfirmAvatarUploadRequest.class)))
                .thenReturn(objectKey);
        when(userQueryService.updateAvatar(USER_ID, objectKey))
                .thenReturn(meResponse("Updated Sprinter", "https://cdn.example/avatar.png"));

        mockMvc.perform(get("/api/me").with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.fullName").value("Skill Sprinter"))
                .andExpect(jsonPath("$.data.roles[0]").value("LEARNER"));

        mockMvc.perform(patch("/api/me")
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated Sprinter"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật hồ sơ thành công"))
                .andExpect(jsonPath("$.data.fullName").value("Updated Sprinter"));

        mockMvc.perform(post("/api/me/avatar/upload-url")
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "avatar.png",
                                  "contentType": "image/png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.example/upload"))
                .andExpect(jsonPath("$.data.objectKey").value(objectKey));

        mockMvc.perform(post("/api/me/avatar/confirm")
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "%s"
                                }
                                """.formatted(objectKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật ảnh đại diện thành công"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example/avatar.png"));

        verify(userQueryService).getMe(USER_ID);
        verify(userQueryService).updateMe(eq(USER_ID), any(UpdateMeRequest.class));
        verify(s3PresignedUrlService).createAvatarUploadUrl(eq(USER_ID), any(CreateAvatarUploadUrlRequest.class));
        verify(s3PresignedUrlService).confirmAvatarUpload(eq(USER_ID), any(ConfirmAvatarUploadRequest.class));
        verify(userQueryService).updateAvatar(USER_ID, objectKey);
    }

    @Test
    void meValidationAndBusinessErrorsAreMapped() throws Exception {
        mockMvc.perform(post("/api/me/avatar/upload-url")
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "",
                                  "contentType": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        when(userQueryService.updateMe(eq(USER_ID), any(UpdateMeRequest.class)))
                .thenThrow(new AppException(ErrorCode.USER_FULL_NAME_REQUIRED));
        when(s3PresignedUrlService.confirmAvatarUpload(eq(USER_ID), any(ConfirmAvatarUploadRequest.class)))
                .thenThrow(new AppException(ErrorCode.INVALID_AVATAR_OBJECT_KEY));

        mockMvc.perform(patch("/api/me")
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/me/avatar/confirm")
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "avatars/another-user/avatar.png"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(s3PresignedUrlService, never()).createAvatarUploadUrl(any(), any());
        verify(userQueryService, never()).updateAvatar(any(), any());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor userJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("user-facing-flow@example.com");
        user.setEmailVerified(true);
        user.setFullName("Skill Sprinter");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private LeaderboardResponse leaderboard(LeaderboardPeriod period) {
        return LeaderboardResponse.builder()
                .period(period)
                .periodStart(LocalDate.parse("2026-06-01"))
                .periodEnd(LocalDate.parse("2026-06-23"))
                .entries(List.of(LeaderboardEntryResponse.builder()
                        .rank(1)
                        .userId(USER_ID)
                        .fullName("Skill Sprinter")
                        .avatarObjectKey("avatars/user.png")
                        .points(250)
                        .streakDays(5)
                        .build()))
                .build();
    }

    private MyPointsResponse myPoints() {
        return MyPointsResponse.builder()
                .totalPoints(900)
                .weeklyPoints(120)
                .monthlyPoints(250)
                .streakDays(5)
                .lastPointDate(LocalDate.parse("2026-06-23"))
                .weeklyRank(3)
                .monthlyRank(2)
                .allTimeRank(7)
                .build();
    }

    private PointEventResponse pointEvent() {
        return PointEventResponse.builder()
                .eventType(PointEventType.QUIZ_PASSED)
                .sourceType(PointSourceType.QUIZ)
                .sourceId("quiz-1")
                .points(30)
                .description("Passed quiz")
                .eventDate(LocalDate.parse("2026-06-23"))
                .createdAt(Instant.parse("2026-06-23T10:00:00Z"))
                .build();
    }

    private String validOnboardingJson() {
        return """
                {
                  "targetGoal": "Learn Java backend",
                  "studyHoursPerWeek": 8.5,
                  "targetDeadline": "2026-08-30",
                  "confidence": "LOW",
                  "preferredLanguage": "vi",
                  "preferredDays": ["MONDAY", "WEDNESDAY"],
                  "preferredTimeSlots": ["19:00-21:00"]
                }
                """;
    }

    private OnboardingProfileResponse onboardingProfile() {
        return OnboardingProfileResponse.builder()
                .profileId(profileId)
                .workspaceId(workspaceId)
                .targetGoal("Learn Java backend")
                .studyHoursPerWeek(new BigDecimal("8.5"))
                .targetDeadline(LocalDate.parse("2026-08-30"))
                .confidence(ConfidenceLevel.LOW)
                .preferredLanguage(PreferredLanguage.vi)
                .preferredDays(List.of(WeekDay.MONDAY, WeekDay.WEDNESDAY))
                .preferredTimeSlots(List.of("19:00-21:00"))
                .createdAt(Instant.parse("2026-06-23T10:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T10:00:00Z"))
                .build();
    }

    private MeResponse meResponse(String fullName, String avatarUrl) {
        return MeResponse.builder()
                .userId(USER_ID)
                .email("user-facing-flow@example.com")
                .emailVerified(true)
                .fullName(fullName)
                .avatarUrl(avatarUrl)
                .timeZone("Asia/Ho_Chi_Minh")
                .status(UserStatus.ACTIVE)
                .roles(List.of("LEARNER"))
                .build();
    }
}
