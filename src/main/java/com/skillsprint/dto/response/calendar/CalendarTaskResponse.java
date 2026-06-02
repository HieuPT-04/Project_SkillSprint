package com.skillsprint.dto.response.calendar;

import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskSource;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.calendar.ClassifiedBy;
import com.skillsprint.enums.calendar.EisenhowerQuadrant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CalendarTaskResponse {

    UUID taskId;
    UUID workspaceId;
    UUID roadmapId;
    UUID roadmapStepId;
    String title;
    String description;
    LocalDate taskDate;
    LocalTime startTime;
    LocalTime endTime;
    Integer durationMinutes;
    CalendarTaskCategory category;
    CalendarTaskPriority priority;
    CalendarTaskStatus status;
    CalendarTaskSource source;
    BigDecimal importanceScore;
    BigDecimal urgencyScore;
    Boolean important;
    Boolean urgent;
    EisenhowerQuadrant eisenhowerQuadrant;
    String classificationReason;
    ClassifiedBy classifiedBy;
    Instant classifiedAt;
    Integer xpReward;
    boolean overdue;
    String studySessionEndpoint;
    Instant completedAt;
    Instant createdAt;
    Instant updatedAt;
}
