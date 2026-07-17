package com.skillsprint.repository;

import com.skillsprint.entity.CreatorPayoutAllocation;
import com.skillsprint.enums.marketplace.CreatorPayoutAllocationState;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorPayoutAllocationRepository extends JpaRepository<CreatorPayoutAllocation, UUID> {

    @EntityGraph(attributePaths = {"earningEntry"})
    List<CreatorPayoutAllocation> findByPayoutPayoutIdOrderByCreatedAtAsc(UUID payoutId);

    @EntityGraph(attributePaths = {"earningEntry"})
    List<CreatorPayoutAllocation> findByPayoutCreatorUserIdOrderByCreatedAtDesc(String creatorId);

    @Query("""
            select allocation.earningEntry.earningEntryId as earningEntryId, sum(allocation.amount) as amount
            from CreatorPayoutAllocation allocation
            where allocation.earningEntry.earningEntryId in :earningEntryIds
              and allocation.state in :states
            group by allocation.earningEntry.earningEntryId
            """)
    List<EarningAllocationTotal> sumAmountsByEarningEntryIdsAndStates(
            @Param("earningEntryIds") Collection<UUID> earningEntryIds,
            @Param("states") Collection<CreatorPayoutAllocationState> states
    );

    interface EarningAllocationTotal {
        UUID getEarningEntryId();
        Long getAmount();
    }
}
