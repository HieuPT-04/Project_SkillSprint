package com.skillsprint.repository;

import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.PomodoroSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PomodoroSessionRepository extends JpaRepository<PomodoroSession, UUID> {

    List<PomodoroSession> findByUserUserId(String userId);

    List<PomodoroSession> findByCalendarTaskTaskId(UUID taskId);

    List<PomodoroSession> findByRoadmapStepStepId(UUID stepId);
}
