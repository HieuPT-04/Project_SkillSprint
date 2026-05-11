package com.skillsprint.repository;

import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.RoadmapStepResource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapStepResourceRepository extends JpaRepository<RoadmapStepResource, UUID> {

    List<RoadmapStepResource> findByStepStepIdOrderBySequenceNoAsc(UUID stepId);
}
