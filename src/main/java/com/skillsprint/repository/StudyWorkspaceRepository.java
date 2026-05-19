package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyWorkspaceRepository extends JpaRepository<StudyWorkspace, UUID> {

    List<StudyWorkspace> findByUserUserId(String userId);

    List<StudyWorkspace> findByUserUserIdAndStatus(String userId, WorkspaceStatus status);

    List<StudyWorkspace> findByUserUserIdAndStatusNotOrderByCreatedAtDesc(String userId, WorkspaceStatus status);

    Optional<StudyWorkspace> findByWorkspaceIdAndUserUserIdAndStatusNot(
            UUID workspaceId,
            String userId,
            WorkspaceStatus status
    );

    boolean existsByWorkspaceIdAndUserUserId(UUID workspaceId, String userId);
}
