package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.UploadedMaterial;
import com.skillsprint.enums.material.FileType;
import com.skillsprint.enums.material.MaterialProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadedMaterialRepository extends JpaRepository<UploadedMaterial, UUID> {

    List<UploadedMaterial> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<UploadedMaterial> findByWorkspaceWorkspaceIdAndUserUserIdOrderByUploadedAtDesc(
            UUID workspaceId,
            String userId
    );

    Optional<UploadedMaterial> findByMaterialIdAndWorkspaceWorkspaceIdAndUserUserId(
            UUID materialId,
            UUID workspaceId,
            String userId
    );

    List<UploadedMaterial> findByUserUserId(String userId);

    List<UploadedMaterial> findByWorkspaceWorkspaceIdAndProcessingStatus(
            UUID workspaceId,
            MaterialProcessingStatus processingStatus
    );

    List<UploadedMaterial> findByWorkspaceWorkspaceIdAndFileType(UUID workspaceId, FileType fileType);

    long countByUserUserId(String userId);

    @Query("""
        select coalesce(sum(m.fileSizeBytes), 0)
        from UploadedMaterial m
        where m.user.userId = :userId
        """)
    Long sumFileSizeByUserId(@Param("userId") String userId);

    @Query("""
        select coalesce(sum(m.fileSizeBytes), 0)
        from UploadedMaterial m
        where m.workspace.workspaceId = :workspaceId
          and m.user.userId = :userId
        """)
    Long sumFileSizeByWorkspaceIdAndUserId(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") String userId
    );
}
