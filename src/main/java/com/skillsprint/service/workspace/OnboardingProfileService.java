package com.skillsprint.service.workspace;

import com.skillsprint.dto.request.workspace.UpsertOnboardingProfileRequest;
import com.skillsprint.dto.response.workspace.OnboardingProfileResponse;
import com.skillsprint.entity.OnboardingProfile;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.OnboardingProfileMapper;
import com.skillsprint.repository.OnboardingProfileRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OnboardingProfileService {

    StudyWorkspaceRepository workspaceRepository;
    OnboardingProfileRepository onboardingProfileRepository;
    OnboardingProfileMapper onboardingProfileMapper;

    @Transactional
    public OnboardingProfileResponse upsertOnboardingProfile(
            String userId,
            UUID workspaceId,
            UpsertOnboardingProfileRequest request
    ) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        OnboardingProfile profile = onboardingProfileRepository.findByWorkspaceWorkspaceId(workspaceId)
                .orElseGet(OnboardingProfile::new);

        profile.setWorkspace(workspace);
        profile.setTargetGoal(normalizeRequiredText(request.getTargetGoal(), ErrorCode.ONBOARDING_TARGET_GOAL_REQUIRED));
        profile.setStudyHoursPerWeek(request.getStudyHoursPerWeek());
        profile.setTargetDeadline(request.getTargetDeadline());
        profile.setConfidence(request.getConfidence());
        if (request.getPreferredLanguage() != null) {
            profile.setPreferredLanguage(request.getPreferredLanguage());
        }
        profile.setPreferredDays(onboardingProfileMapper.writeJson(request.getPreferredDays()));
        profile.setPreferredTimeSlots(onboardingProfileMapper.writeJson(request.getPreferredTimeSlots()));

        return onboardingProfileMapper.toOnboardingProfileResponse(onboardingProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public OnboardingProfileResponse getOnboardingProfile(String userId, UUID workspaceId) {
        findOwnedWorkspace(userId, workspaceId);
        OnboardingProfile profile = onboardingProfileRepository.findByWorkspaceWorkspaceId(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.ONBOARDING_PROFILE_NOT_FOUND));

        return onboardingProfileMapper.toOnboardingProfileResponse(profile);
    }

    private StudyWorkspace findOwnedWorkspace(String userId, UUID workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private String normalizeRequiredText(String value, ErrorCode errorCode) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new AppException(errorCode);
        }
        return normalized;
    }
}
