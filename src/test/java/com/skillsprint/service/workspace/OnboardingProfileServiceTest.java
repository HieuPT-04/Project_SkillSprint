package com.skillsprint.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.request.workspace.UpsertOnboardingProfileRequest;
import com.skillsprint.dto.response.workspace.OnboardingProfileResponse;
import com.skillsprint.entity.OnboardingProfile;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.calendar.WeekDay;
import com.skillsprint.enums.workspace.ConfidenceLevel;
import com.skillsprint.enums.workspace.PreferredLanguage;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.OnboardingProfileMapper;
import com.skillsprint.repository.OnboardingProfileRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingProfileServiceTest {

    @Mock
    StudyWorkspaceRepository workspaceRepository;

    @Mock
    OnboardingProfileRepository onboardingProfileRepository;

    @Mock
    OnboardingProfileMapper onboardingProfileMapper;

    OnboardingProfileService onboardingProfileService;
    User user;
    StudyWorkspace workspace;

    @BeforeEach
    void setUp() {
        onboardingProfileService = new OnboardingProfileService(
                workspaceRepository,
                onboardingProfileRepository,
                onboardingProfileMapper
        );
        user = user("user-1");
        workspace = workspace(user);
    }

    @Test
    void upsertCreatesProfileForOwnedWorkspaceAndNormalizesGoal() {
        UUID workspaceId = workspace.getWorkspaceId();
        OnboardingProfileResponse expected = OnboardingProfileResponse.builder()
                .workspaceId(workspaceId)
                .targetGoal("Pass Java")
                .build();
        whenOwnedWorkspace(workspaceId);
        when(onboardingProfileRepository.findByWorkspaceWorkspaceId(workspaceId)).thenReturn(Optional.empty());
        when(onboardingProfileMapper.writeJson(List.of(WeekDay.MONDAY))).thenReturn("[\"MONDAY\"]");
        when(onboardingProfileMapper.writeJson(List.of("19:00-20:00"))).thenReturn("[\"19:00-20:00\"]");
        when(onboardingProfileRepository.save(any(OnboardingProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(onboardingProfileMapper.toOnboardingProfileResponse(any(OnboardingProfile.class))).thenReturn(expected);

        OnboardingProfileResponse response = onboardingProfileService.upsertOnboardingProfile(
                "user-1",
                workspaceId,
                request("  Pass Java  ")
        );

        assertSame(expected, response);

        ArgumentCaptor<OnboardingProfile> captor = ArgumentCaptor.forClass(OnboardingProfile.class);
        verify(onboardingProfileRepository).save(captor.capture());
        OnboardingProfile saved = captor.getValue();
        assertSame(workspace, saved.getWorkspace());
        assertEquals("Pass Java", saved.getTargetGoal());
        assertEquals(new BigDecimal("8.5"), saved.getStudyHoursPerWeek());
        assertEquals(LocalDate.parse("2026-08-30"), saved.getTargetDeadline());
        assertEquals(ConfidenceLevel.LOW, saved.getConfidence());
        assertEquals(PreferredLanguage.en, saved.getPreferredLanguage());
        assertEquals("[\"MONDAY\"]", saved.getPreferredDays());
        assertEquals("[\"19:00-20:00\"]", saved.getPreferredTimeSlots());
    }

    @Test
    void upsertKeepsExistingPreferredLanguageWhenRequestOmitsIt() {
        UUID workspaceId = workspace.getWorkspaceId();
        OnboardingProfile existing = new OnboardingProfile();
        existing.setWorkspace(workspace);
        existing.setPreferredLanguage(PreferredLanguage.vi);
        whenOwnedWorkspace(workspaceId);
        when(onboardingProfileRepository.findByWorkspaceWorkspaceId(workspaceId)).thenReturn(Optional.of(existing));
        when(onboardingProfileRepository.save(existing)).thenReturn(existing);
        when(onboardingProfileMapper.toOnboardingProfileResponse(existing))
                .thenReturn(OnboardingProfileResponse.builder().workspaceId(workspaceId).build());

        UpsertOnboardingProfileRequest request = request("Goal");
        request.setPreferredLanguage(null);

        onboardingProfileService.upsertOnboardingProfile("user-1", workspaceId, request);

        assertEquals(PreferredLanguage.vi, existing.getPreferredLanguage());
    }

    @Test
    void upsertRejectsBlankGoalAfterTrim() {
        UUID workspaceId = workspace.getWorkspaceId();
        whenOwnedWorkspace(workspaceId);

        AppException exception = assertThrows(
                AppException.class,
                () -> onboardingProfileService.upsertOnboardingProfile("user-1", workspaceId, request("   "))
        );

        assertEquals(ErrorCode.ONBOARDING_TARGET_GOAL_REQUIRED, exception.getErrorCode());
        verify(onboardingProfileRepository, never()).save(any());
    }

    @Test
    void getOnboardingProfileRejectsMissingWorkspaceBeforeReadingProfile() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspaceId,
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> onboardingProfileService.getOnboardingProfile("user-1", workspaceId)
        );

        assertEquals(ErrorCode.WORKSPACE_NOT_FOUND, exception.getErrorCode());
        verify(onboardingProfileRepository, never()).findByWorkspaceWorkspaceId(any());
    }

    @Test
    void getOnboardingProfileMapsExistingProfile() {
        UUID workspaceId = workspace.getWorkspaceId();
        OnboardingProfile profile = new OnboardingProfile();
        OnboardingProfileResponse expected = OnboardingProfileResponse.builder()
                .workspaceId(workspaceId)
                .targetGoal("Goal")
                .build();
        whenOwnedWorkspace(workspaceId);
        when(onboardingProfileRepository.findByWorkspaceWorkspaceId(workspaceId)).thenReturn(Optional.of(profile));
        when(onboardingProfileMapper.toOnboardingProfileResponse(profile)).thenReturn(expected);

        OnboardingProfileResponse response = onboardingProfileService.getOnboardingProfile("user-1", workspaceId);

        assertSame(expected, response);
    }

    private void whenOwnedWorkspace(UUID workspaceId) {
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspaceId,
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));
    }

    private UpsertOnboardingProfileRequest request(String targetGoal) {
        UpsertOnboardingProfileRequest request = new UpsertOnboardingProfileRequest();
        request.setTargetGoal(targetGoal);
        request.setStudyHoursPerWeek(new BigDecimal("8.5"));
        request.setTargetDeadline(LocalDate.parse("2026-08-30"));
        request.setConfidence(ConfidenceLevel.LOW);
        request.setPreferredLanguage(PreferredLanguage.en);
        request.setPreferredDays(List.of(WeekDay.MONDAY));
        request.setPreferredTimeSlots(List.of("19:00-20:00"));
        return request;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(user);
        workspace.setName("Java");
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }
}
