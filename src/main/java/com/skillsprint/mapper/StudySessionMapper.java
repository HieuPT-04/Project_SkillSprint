package com.skillsprint.mapper;

import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.entity.StudySession;
import org.springframework.stereotype.Component;

@Component
public class StudySessionMapper {

    public StudySessionResponse toResponse(StudySession session) {
        return StudySessionResponse.builder()
                .sessionId(session.getSessionId())
                .workspaceId(session.getWorkspace().getWorkspaceId())
                .calendarTaskId(session.getCalendarTask() == null ? null : session.getCalendarTask().getTaskId())
                .roadmapStepId(session.getRoadmapStep() == null ? null : session.getRoadmapStep().getStepId())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .durationMinutes(session.getDurationMinutes())
                .notes(session.getNotes())
                .focusScore(session.getFocusScore())
                .build();
    }
}
