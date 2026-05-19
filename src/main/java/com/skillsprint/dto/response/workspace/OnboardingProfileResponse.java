package com.skillsprint.dto.response.workspace;

import com.skillsprint.enums.calendar.WeekDay;
import com.skillsprint.enums.workspace.ConfidenceLevel;
import com.skillsprint.enums.workspace.PreferredLanguage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OnboardingProfileResponse {

    UUID profileId;
    UUID workspaceId;
    String targetGoal;
    BigDecimal studyHoursPerWeek;
    LocalDate targetDeadline;
    ConfidenceLevel confidence;
    PreferredLanguage preferredLanguage;
    List<WeekDay> preferredDays;
    List<String> preferredTimeSlots;
    Instant createdAt;
    Instant updatedAt;
}
