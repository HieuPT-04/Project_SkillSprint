package com.skillsprint.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.CalendarTask;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarTaskRepository extends JpaRepository<CalendarTask, UUID> {

    List<CalendarTask> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<CalendarTask> findByUserUserId(String userId);

    List<CalendarTask> findByWorkspaceWorkspaceIdAndUserUserIdOrderByTaskDateAscStartTimeAscCreatedAtAsc(
            UUID workspaceId,
            String userId
    );

    List<CalendarTask> findByWorkspaceWorkspaceIdAndUserUserIdAndTaskDateOrderByStartTimeAscCreatedAtAsc(
            UUID workspaceId,
            String userId,
            LocalDate taskDate
    );

    List<CalendarTask> findByWorkspaceWorkspaceIdAndUserUserIdAndStatusAndTaskDateBeforeOrderByTaskDateAscStartTimeAscCreatedAtAsc(
            UUID workspaceId,
            String userId,
            CalendarTaskStatus status,
            LocalDate taskDate
    );

    List<CalendarTask> findByRoadmapRoadmapIdAndStatusNot(
            UUID roadmapId,
            CalendarTaskStatus status
    );

    List<CalendarTask> findByRoadmapStepStepIdAndStatusNot(
            UUID stepId,
            CalendarTaskStatus status
    );

    List<CalendarTask> findByWorkspaceWorkspaceIdAndTaskDate(UUID workspaceId, LocalDate taskDate);

    List<CalendarTask> findByWorkspaceWorkspaceIdAndStatus(UUID workspaceId, CalendarTaskStatus status);
}
