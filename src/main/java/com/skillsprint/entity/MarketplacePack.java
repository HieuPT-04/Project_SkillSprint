package com.skillsprint.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Stable, creator-owned product identity. Its content lives in {@link MarketplacePackVersion}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketplace_packs")
public class MarketplacePack extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pack_id")
    private UUID packId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_workspace_id", nullable = false)
    private StudyWorkspace sourceWorkspace;

    /**
     * The legacy {@code marketplace_items} row this pack was migrated from, or the
     * item it mirrors. Kept as a plain identifier rather than an association: it is a
     * transitional compatibility pointer that a later deprecation release removes.
     */
    @Column(name = "legacy_item_id", unique = true)
    private UUID legacyItemId;
}
