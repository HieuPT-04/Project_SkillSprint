package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.CalendarScheduleRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarScheduleRunRepository extends JpaRepository<CalendarScheduleRun, UUID> {

    List<CalendarScheduleRun> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<CalendarScheduleRun> findByRoadmapRoadmapId(UUID roadmapId);

    Optional<CalendarScheduleRun> findTopByWorkspaceWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    long countByUserUserId(String userId);
}
