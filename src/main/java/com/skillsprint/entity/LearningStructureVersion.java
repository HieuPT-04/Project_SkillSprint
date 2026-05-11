package com.skillsprint.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.skillsprint.enums.GeneratedBy;
import com.skillsprint.enums.LearningStructureStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learning_structure_versions")
public class LearningStructureVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "structure_version_id")
    private UUID structureVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private StudyWorkspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private UploadedMaterial material;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LearningStructureStatus status = LearningStructureStatus.REVIEW_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "generated_by", nullable = false, length = 30)
    private GeneratedBy generatedBy = GeneratedBy.AI;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "input_summary")
    private String inputSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings")
    private String warnings;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
