package com.skillsprint.mapper;

import com.skillsprint.dto.response.calendar.CalendarScheduleRunResponse;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.entity.CalendarScheduleRun;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CalendarMapper {

    public CalendarScheduleRunResponse toScheduleRunResponse(
            CalendarScheduleRun run,
            List<CalendarTask> tasks
    ) {
        return CalendarScheduleRunResponse.builder()
                .runId(run.getRunId())
                .workspaceId(run.getWorkspace().getWorkspaceId())
                .roadmapId(run.getRoadmap() == null ? null : run.getRoadmap().getRoadmapId())
                .scheduleScope(run.getScheduleScope())
                .startDate(run.getStartDate())
                .endDate(run.getEndDate())
                .availableDays(run.getAvailableDays())
                .preferredSessionMinutes(run.getPreferredSessionMinutes())
                .maxSessionsPerDay(run.getMaxSessionsPerDay())
                .includeReviewSessions(run.isIncludeReviewSessions())
                .status(run.getStatus())
                .createdAt(run.getCreatedAt())
                .confirmedAt(run.getConfirmedAt())
                .tasks(tasks.stream().map(this::toTaskResponse).toList())
                .build();
    }

    public CalendarTaskResponse toTaskResponse(CalendarTask task) {
        return CalendarTaskResponse.builder()
                .taskId(task.getTaskId())
                .workspaceId(task.getWorkspace().getWorkspaceId())
                .roadmapId(task.getRoadmap() == null ? null : task.getRoadmap().getRoadmapId())
                .roadmapStepId(task.getRoadmapStep() == null ? null : task.getRoadmapStep().getStepId())
                .title(task.getTitle())
                .description(task.getDescription())
                .taskDate(task.getTaskDate())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .durationMinutes(task.getDurationMinutes())
                .category(task.getCategory())
                .priority(task.getPriority())
                .status(task.getStatus())
                .source(task.getSource())
                .overdue(isOverdue(task))
                .studySessionEndpoint("/api/calendar/tasks/" + task.getTaskId() + "/study-session")
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private boolean isOverdue(CalendarTask task) {
        return task.getStatus() == CalendarTaskStatus.TODO
                && task.getTaskDate() != null
                && task.getTaskDate().isBefore(LocalDate.now());
    }
}
