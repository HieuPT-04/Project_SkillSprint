package com.skillsprint.repository;

import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.UploadedMaterial;
import com.skillsprint.enums.material.FileType;
import com.skillsprint.enums.material.MaterialProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedMaterialRepository extends JpaRepository<UploadedMaterial, UUID> {

    List<UploadedMaterial> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<UploadedMaterial> findByWorkspaceWorkspaceIdAndUserUserIdOrderByUploadedAtDesc(
            UUID workspaceId,
            String userId
    );

    List<UploadedMaterial> findByUserUserId(String userId);

    List<UploadedMaterial> findByWorkspaceWorkspaceIdAndProcessingStatus(
            UUID workspaceId,
            MaterialProcessingStatus processingStatus
    );

    List<UploadedMaterial> findByWorkspaceWorkspaceIdAndFileType(UUID workspaceId, FileType fileType);
}
