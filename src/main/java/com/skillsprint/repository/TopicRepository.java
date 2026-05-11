package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findByChapterChapterIdOrderBySequenceNoAsc(UUID chapterId);

    List<Topic> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<Topic> findByStructureVersionStructureVersionId(UUID structureVersionId);

    Optional<Topic> findByChapterChapterIdAndSequenceNo(UUID chapterId, Integer sequenceNo);
}
