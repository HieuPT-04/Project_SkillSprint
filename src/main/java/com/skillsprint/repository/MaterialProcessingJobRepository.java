package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.MaterialProcessingJob;
import com.skillsprint.enums.ProcessingJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialProcessingJobRepository extends JpaRepository<MaterialProcessingJob, UUID> {

    Optional<MaterialProcessingJob> findTopByMaterialMaterialIdOrderByCreatedAtDesc(UUID materialId);

    List<MaterialProcessingJob> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<MaterialProcessingJob> findByUserUserId(String userId);

    List<MaterialProcessingJob> findByStatus(ProcessingJobStatus status);
}
