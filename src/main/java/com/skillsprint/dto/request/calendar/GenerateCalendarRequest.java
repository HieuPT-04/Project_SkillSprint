package com.skillsprint.dto.request.calendar;

import com.skillsprint.enums.calendar.WeekDay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalTime;
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
public class GenerateCalendarRequest {

    LocalDate startDate;

    List<WeekDay> studyDays;

    LocalTime dailyStartTime;

    @Min(15)
    @Max(240)
    Integer sessionMinutes;

    @Min(1)
    @Max(8)
    Integer sessionsPerDay;

    Boolean includeReviewSessions;
}
