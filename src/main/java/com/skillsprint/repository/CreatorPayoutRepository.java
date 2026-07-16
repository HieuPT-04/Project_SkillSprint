package com.skillsprint.repository;

import com.skillsprint.entity.CreatorPayout;
import com.skillsprint.enums.marketplace.CreatorPayoutStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorPayoutRepository extends JpaRepository<CreatorPayout, UUID> {

    List<CreatorPayout> findByCreatorUserIdOrderByCreatedAtDesc(String creatorId);

    List<CreatorPayout> findByStatusOrderByCreatedAtAsc(CreatorPayoutStatus status);
}
