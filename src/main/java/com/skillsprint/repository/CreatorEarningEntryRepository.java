package com.skillsprint.repository;

import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorEarningEntryRepository extends JpaRepository<CreatorEarningEntry, UUID> {

    List<CreatorEarningEntry> findByCreatorUserIdAndStateOrderByCreatedAtDesc(
            String creatorId,
            CreatorEarningState state
    );
}
