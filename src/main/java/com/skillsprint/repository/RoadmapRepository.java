package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.Roadmap;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapRepository extends JpaRepository<Roadmap, UUID> {

    List<Roadmap> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<Roadmap> findByUserUserId(String userId);

    long countByUserUserId(String userId);

    long countByStatus(RoadmapStatus status);

    List<Roadmap> findByWorkspaceWorkspaceIdAndStatus(UUID workspaceId, RoadmapStatus status);

    Optional<Roadmap> findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(UUID workspaceId);
}
