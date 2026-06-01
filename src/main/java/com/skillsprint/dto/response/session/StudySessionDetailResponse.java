package com.skillsprint.dto.response.session;

import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.roadmap.RoadmapResourceResponse;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StudySessionDetailResponse {

    CalendarTaskResponse task;
    RoadmapStepStudyResponse roadmapStep;
    PracticePromptResponse practice;
    List<RoadmapResourceResponse> resources;
    StudySessionActionsResponse actions;

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RoadmapStepStudyResponse {
        UUID stepId;
        UUID chapterId;
        UUID topicId;
        String title;
        String subtitle;
        String summary;
        List<String> whatToLearn;
        List<String> keyConcepts;
        List<String> learningOutcomes;
        List<String> recommendedFocus;
        DifficultyLevel difficulty;
        Integer estimatedMinutes;
        Integer sequenceNo;
        RoadmapStepStatus status;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PracticePromptResponse {
        String prompt;
        String expectedOutput;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class StudySessionActionsResponse {
        boolean canStart;
        boolean canFinish;
        boolean canCompleteTask;
        String startEndpoint;
        String finishEndpointTemplate;
        String pausePomodoroEndpointTemplate;
        String resumePomodoroEndpointTemplate;
        String nextPomodoroPhaseEndpointTemplate;
        String completeTaskEndpoint;
    }
}
