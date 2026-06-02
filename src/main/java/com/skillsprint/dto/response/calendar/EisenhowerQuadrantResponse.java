package com.skillsprint.dto.response.calendar;

import com.skillsprint.enums.calendar.EisenhowerQuadrant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EisenhowerQuadrantResponse {

    EisenhowerQuadrant quadrant;
    String title;
    String description;
    int taskCount;
    List<CalendarTaskResponse> tasks;
}
