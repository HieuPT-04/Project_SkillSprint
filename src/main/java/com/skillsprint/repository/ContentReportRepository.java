package com.skillsprint.repository;

import com.skillsprint.entity.ContentReport;
import com.skillsprint.enums.community.ContentReportStatus;
import com.skillsprint.enums.community.ContentReportTargetType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {

    Optional<ContentReport> findByTargetTypeAndTargetIdAndReporterUserId(
            ContentReportTargetType targetType,
            UUID targetId,
            String reporterId
    );

    long countByTargetTypeAndTargetIdAndStatus(
            ContentReportTargetType targetType,
            UUID targetId,
            ContentReportStatus status
    );

    @Query("""
            select report
            from ContentReport report
            where (:targetType is null or report.targetType = :targetType)
              and (:status is null or report.status = :status)
            """)
    Page<ContentReport> searchAdmin(
            @Param("targetType") ContentReportTargetType targetType,
            @Param("status") ContentReportStatus status,
            Pageable pageable
    );
}
