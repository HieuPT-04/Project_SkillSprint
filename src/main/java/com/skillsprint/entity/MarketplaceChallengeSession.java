package com.skillsprint.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter @Setter @NoArgsConstructor @Entity @Table(name="marketplace_challenge_sessions")
public class MarketplaceChallengeSession extends BaseAuditEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="session_id") private UUID sessionId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="item_id",nullable=false) private MarketplaceItem item;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="completed_at") private Instant completedAt;
}
