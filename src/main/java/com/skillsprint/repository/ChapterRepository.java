package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<Chapter> findByStructureVersionStructureVersionIdOrderBySequenceNoAsc(UUID structureVersionId);

    Optional<Chapter> findByStructureVersionStructureVersionIdAndSequenceNo(
            UUID structureVersionId,
            Integer sequenceNo
    );
}
