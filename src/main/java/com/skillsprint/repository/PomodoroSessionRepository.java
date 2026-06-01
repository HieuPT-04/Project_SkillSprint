package com.skillsprint.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.PomodoroSession;
import com.skillsprint.enums.session.PomodoroSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PomodoroSessionRepository extends JpaRepository<PomodoroSession, UUID> {

    List<PomodoroSession> findByUserUserId(String userId);

    List<PomodoroSession> findByCalendarTaskTaskId(UUID taskId);

    List<PomodoroSession> findByRoadmapStepStepId(UUID stepId);

    Optional<PomodoroSession> findFirstByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
            UUID sessionId,
            Collection<PomodoroSessionStatus> statuses
    );

    List<PomodoroSession> findByStudySessionSessionIdAndStatusIn(
            UUID sessionId,
            Collection<PomodoroSessionStatus> statuses
    );

    List<PomodoroSession> findByStudySessionSessionIdAndStatusInOrderByStartedAtDesc(
            UUID sessionId,
            Collection<PomodoroSessionStatus> statuses
    );
}
