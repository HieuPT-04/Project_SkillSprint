package com.skillsprint.dto.request.calendar;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCalendarTaskStatusRequest {

    @NotBlank
    private String status;
}
