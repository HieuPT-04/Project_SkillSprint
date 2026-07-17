package com.skillsprint.repository;

import com.skillsprint.entity.CreatorPayoutDestination;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorPayoutDestinationRepository extends JpaRepository<CreatorPayoutDestination, UUID> {

    Optional<CreatorPayoutDestination> findByCreatorUserIdAndActiveTrue(String creatorId);
}
