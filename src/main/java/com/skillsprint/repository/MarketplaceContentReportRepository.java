package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceContentReport;
import com.skillsprint.enums.marketplace.MarketplaceReportCategory;
import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceContentReportRepository extends JpaRepository<MarketplaceContentReport, UUID> {

    /** Active duplicate states are OPEN and IN_REVIEW; a resolved/dismissed report no longer blocks. */
    @Query("""
            select count(report) > 0
            from MarketplaceContentReport report
            where report.reporter.userId = :reporterId
              and report.packVersion.versionId = :versionId
              and report.targetType = :targetType
              and report.category = :category
              and report.status in (
                  com.skillsprint.enums.marketplace.MarketplaceReportStatus.OPEN,
                  com.skillsprint.enums.marketplace.MarketplaceReportStatus.IN_REVIEW)
              and ((:targetRef is null and report.targetRef is null)
                   or report.targetRef = :targetRef)
            """)
    boolean existsActiveReport(
            @Param("reporterId") String reporterId,
            @Param("versionId") UUID versionId,
            @Param("targetType") MarketplaceReportTargetType targetType,
            @Param("targetRef") String targetRef,
            @Param("category") MarketplaceReportCategory category
    );

    List<MarketplaceContentReport> findByReporterUserIdOrderByCreatedAtDesc(String reporterId);

    @Query("""
            select report
            from MarketplaceContentReport report
            where (:status is null or report.status = :status)
              and (:targetType is null or report.targetType = :targetType)
              and (:category is null or report.category = :category)
            """)
    Page<MarketplaceContentReport> searchAdmin(
            @Param("status") MarketplaceReportStatus status,
            @Param("targetType") MarketplaceReportTargetType targetType,
            @Param("category") MarketplaceReportCategory category,
            Pageable pageable
    );

    Optional<MarketplaceContentReport> findByReportIdAndReporterUserId(UUID reportId, String reporterId);

    long countByPackVersionVersionId(UUID versionId);

    long countByPackVersionVersionIdAndStatus(UUID versionId, MarketplaceReportStatus status);
}
