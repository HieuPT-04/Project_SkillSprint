package com.skillsprint.entity;

import java.time.Instant;
import java.util.UUID;

import com.skillsprint.enums.roadmap.ResourcePlatform;
import com.skillsprint.enums.roadmap.ResourceType;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roadmap_step_resources")
public class RoadmapStepResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "resource_id")
    private UUID resourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false)
    private RoadmapStep step;

    @Column(name = "title", length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 50)
    private ResourcePlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 50)
    private ResourceType resourceType;

    @Column(name = "search_query")
    private String searchQuery;

    @Column(name = "url")
    private String url;

    @Column(name = "reason")
    private String reason;

    @Column(name = "is_ai_recommended", nullable = false)
    private boolean aiRecommended = true;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
