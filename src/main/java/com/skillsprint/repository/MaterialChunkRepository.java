package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.MaterialChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialChunkRepository extends JpaRepository<MaterialChunk, UUID> {

    List<MaterialChunk> findByMaterialMaterialIdOrderByChunkIndexAsc(UUID materialId);

    List<MaterialChunk> findByDocumentDocumentIdOrderByChunkIndexAsc(UUID documentId);

    List<MaterialChunk> findByWorkspaceWorkspaceId(UUID workspaceId);

    Optional<MaterialChunk> findByMaterialMaterialIdAndChunkIndex(UUID materialId, Integer chunkIndex);

    void deleteByMaterialMaterialId(UUID materialId);
}
