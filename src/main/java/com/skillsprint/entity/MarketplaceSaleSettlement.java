package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.MarketplaceSettlementStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketplace_sale_settlements")
public class MarketplaceSaleSettlement extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_id")
    private UUID settlementId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, unique = true)
    private MarketplaceSale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "creator_share_bps", nullable = false)
    private Integer creatorShareBps;

    @Column(name = "creator_amount", nullable = false)
    private Integer creatorAmount;

    @Column(name = "platform_share_bps", nullable = false)
    private Integer platformShareBps;

    @Column(name = "platform_amount", nullable = false)
    private Integer platformAmount;

    @Column(name = "coin_to_vnd_rate", nullable = false, precision = 12, scale = 4)
    private BigDecimal coinToVndRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MarketplaceSettlementStatus status = MarketplaceSettlementStatus.RECORDED;
}
