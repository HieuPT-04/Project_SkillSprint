package com.skillsprint.service.progress;

import com.skillsprint.dto.response.progress.ProgressDashboardResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.ProgressMapper;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProgressService {

    StudyWorkspaceRepository workspaceRepository;
    RoadmapRepository roadmapRepository;
    CalendarTaskRepository calendarTaskRepository;
    ProgressMapper progressMapper;

    @Transactional(readOnly = true)
    public ProgressDashboardResponse getDashboard(String userId, UUID workspaceId) {
        ensureOwnedWorkspace(userId, workspaceId);
        Roadmap roadmap = roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.ROADMAP_NOT_FOUND));
        LocalDate today = LocalDate.now();
        List<CalendarTask> allTasks = calendarTaskRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdOrderByTaskDateAscStartTimeAscCreatedAtAsc(workspaceId, userId);
        List<CalendarTask> todayTasks = calendarTaskRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdAndTaskDateOrderByStartTimeAscCreatedAtAsc(
                        workspaceId,
                        userId,
                        today
                );
        List<CalendarTask> overdueTasks = calendarTaskRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdAndStatusAndTaskDateBeforeOrderByTaskDateAscStartTimeAscCreatedAtAsc(
                        workspaceId,
                        userId,
                        CalendarTaskStatus.TODO,
                        today
                );

        return progressMapper.toDashboardResponse(roadmap, allTasks, todayTasks, overdueTasks, today);
    }

    private void ensureOwnedWorkspace(String userId, UUID workspaceId) {
        workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }
}
