package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.LearningStructureVersion;
import com.skillsprint.enums.learningstructure.LearningStructureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningStructureVersionRepository extends JpaRepository<LearningStructureVersion, UUID> {

    List<LearningStructureVersion> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<LearningStructureVersion> findByWorkspaceWorkspaceIdAndStatus(
            UUID workspaceId,
            LearningStructureStatus status
    );

    Optional<LearningStructureVersion> findByWorkspaceWorkspaceIdAndVersionNo(UUID workspaceId, Integer versionNo);

    Optional<LearningStructureVersion> findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(UUID workspaceId);

    boolean existsByWorkspaceWorkspaceId(UUID workspaceId);

    @Query("""
        select count(v)
        from LearningStructureVersion v
        where v.workspace.user.userId = :userId
        """)
    long countByUserId(@Param("userId") String userId);
}
