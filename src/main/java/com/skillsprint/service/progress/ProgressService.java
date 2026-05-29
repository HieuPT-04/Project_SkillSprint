package com.skillsprint.service.progress;

import com.skillsprint.dto.response.progress.ProgressDashboardResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.StudySession;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.session.StudySessionStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.ProgressMapper;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.StudySessionRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
    StudySessionRepository studySessionRepository;
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
        List<StudySession> sessions = studySessionRepository.findByWorkspaceWorkspaceIdAndUserUserId(workspaceId, userId);
        Optional<StudySession> currentSession = studySessionRepository
                .findFirstByWorkspaceWorkspaceIdAndUserUserIdAndStatusOrderByStartedAtDesc(
                        workspaceId,
                        userId,
                        StudySessionStatus.IN_PROGRESS
                );

        return progressMapper.toDashboardResponse(
                roadmap,
                allTasks,
                todayTasks,
                overdueTasks,
                today,
                buildStudyStats(sessions, today),
                currentSession.orElse(null)
        );
    }

    private void ensureOwnedWorkspace(String userId, UUID workspaceId) {
        workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private ProgressDashboardResponse.StudyStatsResponse buildStudyStats(List<StudySession> sessions, LocalDate today) {
        List<StudySession> completedSessions = sessions.stream()
                .filter(session -> session.getStatus() == StudySessionStatus.COMPLETED)
                .toList();
        int totalStudyMinutes = completedSessions.stream()
                .map(StudySession::getDurationMinutes)
                .filter(duration -> duration != null && duration > 0)
                .mapToInt(Integer::intValue)
                .sum();
        Set<LocalDate> studyDates = completedSessions.stream()
                .map(this::resolveStudyDate)
                .collect(Collectors.toSet());
        LocalDate lastStudyDate = studyDates.stream()
                .max(Comparator.naturalOrder())
                .orElse(null);

        return ProgressDashboardResponse.StudyStatsResponse.builder()
                .totalStudyMinutes(totalStudyMinutes)
                .completedSessions(completedSessions.size())
                .currentStreakDays(calculateCurrentStreakDays(studyDates, today))
                .lastStudyDate(lastStudyDate)
                .build();
    }

    private int calculateCurrentStreakDays(Set<LocalDate> studyDates, LocalDate today) {
        if (studyDates.isEmpty()) {
            return 0;
        }

        LocalDate cursor = studyDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (studyDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private LocalDate resolveStudyDate(StudySession session) {
        if (session.getEndedAt() != null) {
            return LocalDate.ofInstant(session.getEndedAt(), ZoneId.systemDefault());
        }
        return LocalDate.ofInstant(session.getStartedAt(), ZoneId.systemDefault());
    }
}
