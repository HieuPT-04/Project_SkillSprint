package com.skillsprint.entity;

import com.skillsprint.enums.community.CommunityRoomRole;
import com.skillsprint.enums.community.CommunityRoomMemberStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "community_room_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_community_room_member_room_user",
                columnNames = {"room_id", "user_id"}
        )
)
public class CommunityRoomMember extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "member_id")
    private UUID memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private CommunityRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private CommunityRoomRole role = CommunityRoomRole.MEMBER;

    @Column(name = "mute_until")
    private Instant muteUntil;

    @Column(name = "banned", nullable = false)
    private boolean banned = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CommunityRoomMemberStatus status = CommunityRoomMemberStatus.ACTIVE;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by")
    private User removedBy;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;
}
