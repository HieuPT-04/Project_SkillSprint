package com.skillsprint.repository;

import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.RoadmapProgressLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapProgressLogRepository extends JpaRepository<RoadmapProgressLog, UUID> {

    List<RoadmapProgressLog> findByRoadmapRoadmapIdOrderByCreatedAtDesc(UUID roadmapId);

    List<RoadmapProgressLog> findByStepStepIdOrderByCreatedAtDesc(UUID stepId);

    List<RoadmapProgressLog> findByUserUserIdOrderByCreatedAtDesc(String userId);
}
