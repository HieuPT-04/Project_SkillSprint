package com.skillsprint.repository;

import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.WorkspaceProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceProgressRepository extends JpaRepository<WorkspaceProgress, UUID> {

    Optional<WorkspaceProgress> findByWorkspaceWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceWorkspaceId(UUID workspaceId);
}
