package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceVersionProgress;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceVersionProgressRepository extends JpaRepository<MarketplaceVersionProgress, UUID> {

    Optional<MarketplaceVersionProgress> findByBuyerUserIdAndPackVersionVersionId(
            String buyerId,
            UUID versionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select progress
            from MarketplaceVersionProgress progress
            where progress.buyer.userId = :buyerId
              and progress.packVersion.versionId = :versionId
            """)
    Optional<MarketplaceVersionProgress> findByBuyerAndVersionForUpdate(
            @Param("buyerId") String buyerId,
            @Param("versionId") UUID versionId
    );

    long countByPackVersionVersionId(UUID versionId);

    @Query("""
            select count(progress)
            from MarketplaceVersionProgress progress
            where progress.packVersion.versionId = :versionId
              and progress.completionPercent >= 100
            """)
    long countCompletedByVersion(@Param("versionId") UUID versionId);
}
