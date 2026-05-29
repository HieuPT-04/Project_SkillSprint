package com.skillsprint.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.skillsprint.enums.plan.ServicePlanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_plans")
public class ServicePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, unique = true, length = 20)
    private ServicePlanType planType;

    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice = BigDecimal.ZERO;

    @Column(name = "ai_parsing_limit", nullable = false)
    private Integer aiParsingLimit = 5;

    @Column(name = "max_file_mb", nullable = false)
    private Integer maxFileMb = 20;

    @Column(name = "max_workspace_mb", nullable = false)
    private Integer maxWorkspaceMb = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "max_workspaces")
    private Integer maxWorkspaces = 1;

    @Column(name = "max_uploads")
    private Integer maxUploads = 5;
}
