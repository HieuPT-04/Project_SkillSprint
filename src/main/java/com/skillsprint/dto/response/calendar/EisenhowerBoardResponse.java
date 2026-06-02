package com.skillsprint.dto.response.calendar;

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
public class EisenhowerBoardResponse {

    UUID workspaceId;
    LocalDate date;
    int totalTasks;
    int completedTasks;
    int pendingTasks;
    List<EisenhowerQuadrantResponse> quadrants;
}
