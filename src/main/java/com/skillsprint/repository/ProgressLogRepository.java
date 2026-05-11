package com.skillsprint.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.ProgressLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressLogRepository extends JpaRepository<ProgressLog, UUID> {

    List<ProgressLog> findByWorkspaceWorkspaceIdOrderByLogDateDesc(UUID workspaceId);

    Optional<ProgressLog> findByWorkspaceWorkspaceIdAndLogDate(UUID workspaceId, LocalDate logDate);
}
