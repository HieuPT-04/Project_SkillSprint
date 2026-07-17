package com.skillsprint.repository;

import com.skillsprint.entity.CreatorPayout;
import com.skillsprint.enums.marketplace.CreatorPayoutStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorPayoutRepository extends JpaRepository<CreatorPayout, UUID> {

    @EntityGraph(attributePaths = {"creator"})
    List<CreatorPayout> findByCreatorUserIdOrderByCreatedAtDesc(String creatorId);

    @EntityGraph(attributePaths = {"creator"})
    List<CreatorPayout> findByStatusOrderByCreatedAtAsc(CreatorPayoutStatus status);

    @EntityGraph(attributePaths = {"creator"})
    List<CreatorPayout> findAllByOrderByCreatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payout from CreatorPayout payout where payout.payoutId = :payoutId")
    Optional<CreatorPayout> findByPayoutIdForUpdate(@Param("payoutId") UUID payoutId);
}
