package com.skillsprint.dto.request.calendar;

import com.skillsprint.enums.calendar.EisenhowerQuadrant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCalendarTaskRequest {

    @NotBlank
    @Size(max = 500)
    private String title;

    private String description;

    private EisenhowerQuadrant quadrant;

    private String status;

    private LocalDate taskDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer durationMinutes;
}
