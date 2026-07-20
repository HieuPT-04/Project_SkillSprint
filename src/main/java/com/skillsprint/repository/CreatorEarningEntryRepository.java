package com.skillsprint.repository;

import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CreatorEarningEntryRepository extends JpaRepository<CreatorEarningEntry, UUID> {

    List<CreatorEarningEntry> findByCreatorUserIdAndStateOrderByCreatedAtDesc(
            String creatorId,
            CreatorEarningState state
    );

    @EntityGraph(attributePaths = {"settlement", "settlement.sale"})
    List<CreatorEarningEntry> findByCreatorUserIdOrderByCreatedAtDesc(String creatorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select earning from CreatorEarningEntry earning
            where earning.creator.userId = :creatorId
              and earning.state <> com.skillsprint.enums.marketplace.CreatorEarningState.REVERSED
              and earning.state <> com.skillsprint.enums.marketplace.CreatorEarningState.PAID
            order by earning.createdAt asc
            """)
    List<CreatorEarningEntry> findEligibleForUpdate(@Param("creatorId") String creatorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select earning from CreatorEarningEntry earning
            where earning.settlement.settlementId = :settlementId
            """)
    java.util.Optional<CreatorEarningEntry> findBySettlementIdForUpdate(@Param("settlementId") UUID settlementId);
}
