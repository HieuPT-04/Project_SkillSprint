package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceReview;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceReviewRepository extends JpaRepository<MarketplaceReview, UUID> {

    /** Kept until the item-scoped read/write compatibility adapter is removed. */
    Optional<MarketplaceReview> findByItemItemIdAndUserUserId(UUID itemId, String userId);

    /** Kept for callers that still aggregate unmigrated item-scoped reviews. */
    List<MarketplaceReview> findByItemItemId(UUID itemId);

    /** True only when the review exists and belongs to the exact pack version. */
    boolean existsByReviewIdAndPackVersionVersionId(UUID reviewId, UUID versionId);

    @EntityGraph(attributePaths = {"user"})
    Optional<MarketplaceReview> findByPackVersionVersionIdAndUserUserId(UUID versionId, String userId);

    @EntityGraph(attributePaths = {"user"})
    List<MarketplaceReview> findByPackVersionVersionIdOrderByUpdatedAtDesc(UUID versionId);

    @EntityGraph(attributePaths = {"user"})
    List<MarketplaceReview> findByItemItemIdAndPackVersionIsNullOrderByUpdatedAtDesc(UUID itemId);

    @Query("""
            select review.packVersion.versionId as versionId,
                   avg(review.rating) as averageRating,
                   count(review) as reviewCount
            from MarketplaceReview review
            where review.packVersion.versionId in :versionIds
            group by review.packVersion.versionId
            """)
    List<VersionRatingSummary> summarizeByVersionIds(@Param("versionIds") Collection<UUID> versionIds);

    @Query("""
            select review.item.itemId as itemId,
                   avg(review.rating) as averageRating,
                   count(review) as reviewCount
            from MarketplaceReview review
            where review.packVersion is null
              and review.item.itemId in :itemIds
            group by review.item.itemId
            """)
    List<LegacyRatingSummary> summarizeLegacyByItemIds(@Param("itemIds") Collection<UUID> itemIds);

    interface VersionRatingSummary {
        UUID getVersionId();
        Double getAverageRating();
        Long getReviewCount();
    }

    interface LegacyRatingSummary {
        UUID getItemId();
        Double getAverageRating();
        Long getReviewCount();
    }
}
