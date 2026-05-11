package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.enums.RoadmapStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapStepRepository extends JpaRepository<RoadmapStep, UUID> {

    List<RoadmapStep> findByRoadmapRoadmapIdOrderBySequenceNoAsc(UUID roadmapId);

    List<RoadmapStep> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<RoadmapStep> findByRoadmapRoadmapIdAndStatus(UUID roadmapId, RoadmapStepStatus status);

    Optional<RoadmapStep> findByRoadmapRoadmapIdAndSequenceNo(UUID roadmapId, Integer sequenceNo);
}
