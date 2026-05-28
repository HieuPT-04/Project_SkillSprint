package com.skillsprint.dto.response.progress;

import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import java.math.BigDecimal;
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
public class ProgressDashboardResponse {

    UUID workspaceId;
    UUID roadmapId;
    RoadmapStatus roadmapStatus;
    BigDecimal progressPercent;
    Integer totalSteps;
    Integer completedSteps;
    Integer totalTasks;
    Integer completedTasks;
    Integer todayTaskCount;
    Integer overdueTaskCount;
    LocalDate today;
    StudyStatsResponse study;
    StudySessionResponse currentSession;
    ProgressStepResponse currentStep;
    List<CalendarTaskResponse> todayTasks;
    List<CalendarTaskResponse> overdueTasks;

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class StudyStatsResponse {
        Integer totalStudyMinutes;
        Integer completedSessions;
        Integer currentStreakDays;
        LocalDate lastStudyDate;
    }
}
