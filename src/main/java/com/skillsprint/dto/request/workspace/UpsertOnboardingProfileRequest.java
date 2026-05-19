package com.skillsprint.dto.request.workspace;

import com.skillsprint.enums.calendar.WeekDay;
import com.skillsprint.enums.workspace.ConfidenceLevel;
import com.skillsprint.enums.workspace.PreferredLanguage;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpsertOnboardingProfileRequest {

    @NotBlank
    @Size(max = 2000)
    String targetGoal;

    @DecimalMin(value = "0.5")
    @DecimalMax(value = "168.0")
    BigDecimal studyHoursPerWeek;

    LocalDate targetDeadline;

    @NotNull
    ConfidenceLevel confidence;

    PreferredLanguage preferredLanguage;

    List<WeekDay> preferredDays;

    List<@Size(max = 100) String> preferredTimeSlots;
}
