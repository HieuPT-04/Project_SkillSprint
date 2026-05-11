package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.ExtractedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedDocumentRepository extends JpaRepository<ExtractedDocument, UUID> {

    Optional<ExtractedDocument> findByMaterialMaterialId(UUID materialId);

    List<ExtractedDocument> findByWorkspaceWorkspaceId(UUID workspaceId);
}
