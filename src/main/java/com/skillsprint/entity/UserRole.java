package com.skillsprint.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "user_roles")
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_role_id")
    private UUID userRoleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private StudyWorkspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
