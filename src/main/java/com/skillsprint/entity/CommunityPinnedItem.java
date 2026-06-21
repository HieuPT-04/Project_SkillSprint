package com.skillsprint.entity;

import com.skillsprint.enums.community.CommunityPinItemType;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "community_pinned_items")
public class CommunityPinnedItem extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pin_id")
    private UUID pinId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private CommunityRoom room;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private CommunityPinItemType itemType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private CommunityChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pinned_by", nullable = false)
    private User pinnedBy;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
