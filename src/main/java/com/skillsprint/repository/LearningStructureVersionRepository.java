package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.LearningStructureVersion;
import com.skillsprint.enums.LearningStructureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningStructureVersionRepository extends JpaRepository<LearningStructureVersion, UUID> {

    List<LearningStructureVersion> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<LearningStructureVersion> findByWorkspaceWorkspaceIdAndStatus(
            UUID workspaceId,
            LearningStructureStatus status
    );

    Optional<LearningStructureVersion> findByWorkspaceWorkspaceIdAndVersionNo(UUID workspaceId, Integer versionNo);

    Optional<LearningStructureVersion> findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(UUID workspaceId);
}
