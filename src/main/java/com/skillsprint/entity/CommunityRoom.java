package com.skillsprint.entity;

import com.skillsprint.enums.community.CommunityRoomMode;
import com.skillsprint.enums.community.CommunityRoomStatus;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "community_rooms")
@DynamicUpdate
public class CommunityRoom extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 30)
    private CommunityRoomMode mode = CommunityRoomMode.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CommunityRoomStatus status = CommunityRoomStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 50;

    @Column(name = "member_count", nullable = false)
    private int memberCount = 0;

    @Column(name = "report_count", nullable = false)
    private int reportCount = 0;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;
}
