package com.skillsprint.mapper;

import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.progress.ProgressDashboardResponse;
import com.skillsprint.dto.response.progress.ProgressStepResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProgressMapper {

    private final CalendarMapper calendarMapper;

    public ProgressMapper(CalendarMapper calendarMapper) {
        this.calendarMapper = calendarMapper;
    }

    public ProgressDashboardResponse toDashboardResponse(
            Roadmap roadmap,
            List<CalendarTask> allTasks,
            List<CalendarTask> todayTasks,
            List<CalendarTask> overdueTasks,
            LocalDate today
    ) {
        int completedTasks = (int) allTasks.stream()
                .filter(task -> task.getStatus() == CalendarTaskStatus.COMPLETED)
                .count();
        List<CalendarTaskResponse> todayTaskResponses = todayTasks.stream()
                .map(calendarMapper::toTaskResponse)
                .toList();
        List<CalendarTaskResponse> overdueTaskResponses = overdueTasks.stream()
                .map(calendarMapper::toTaskResponse)
                .toList();

        return ProgressDashboardResponse.builder()
                .workspaceId(roadmap.getWorkspace().getWorkspaceId())
                .roadmapId(roadmap.getRoadmapId())
                .roadmapStatus(roadmap.getStatus())
                .progressPercent(roadmap.getProgressPercent())
                .totalSteps(roadmap.getTotalSteps())
                .completedSteps(roadmap.getCompletedSteps())
                .totalTasks(allTasks.size())
                .completedTasks(completedTasks)
                .todayTaskCount(todayTaskResponses.size())
                .overdueTaskCount(overdueTaskResponses.size())
                .today(today)
                .currentStep(toStepResponse(roadmap.getCurrentStep()))
                .todayTasks(todayTaskResponses)
                .overdueTasks(overdueTaskResponses)
                .build();
    }

    private ProgressStepResponse toStepResponse(RoadmapStep step) {
        if (step == null) {
            return null;
        }

        return ProgressStepResponse.builder()
                .stepId(step.getStepId())
                .title(step.getTitle())
                .sequenceNo(step.getSequenceNo())
                .status(step.getStatus())
                .build();
    }
}
