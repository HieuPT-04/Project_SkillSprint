package com.skillsprint.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.response.workspace.OnboardingProfileResponse;
import com.skillsprint.entity.OnboardingProfile;
import com.skillsprint.enums.calendar.WeekDay;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OnboardingProfileMapper {

    static TypeReference<List<WeekDay>> WEEK_DAY_LIST_TYPE = new TypeReference<>() {};
    static TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    ObjectMapper objectMapper;

    public OnboardingProfileResponse toOnboardingProfileResponse(OnboardingProfile profile) {
        return OnboardingProfileResponse.builder()
                .profileId(profile.getProfileId())
                .workspaceId(profile.getWorkspace().getWorkspaceId())
                .targetGoal(profile.getTargetGoal())
                .studyHoursPerWeek(profile.getStudyHoursPerWeek())
                .targetDeadline(profile.getTargetDeadline())
                .confidence(profile.getConfidence())
                .preferredLanguage(profile.getPreferredLanguage())
                .preferredDays(readList(profile.getPreferredDays(), WEEK_DAY_LIST_TYPE))
                .preferredTimeSlots(readList(profile.getPreferredTimeSlots(), STRING_LIST_TYPE))
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    public String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.ONBOARDING_WRITE_FAILED);
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.ONBOARDING_READ_FAILED);
        }
    }
}
