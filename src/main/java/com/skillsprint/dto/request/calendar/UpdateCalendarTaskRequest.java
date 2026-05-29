package com.skillsprint.dto.request.calendar;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCalendarTaskRequest {

    LocalDate taskDate;
    LocalTime startTime;
    LocalTime endTime;
}
