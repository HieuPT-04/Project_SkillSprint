package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.MarketplaceSaleStatus;
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
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "marketplace_sales",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_marketplace_sales_buyer_idempotency",
                columnNames = {"buyer_id", "idempotency_key"}
        )
)
public class MarketplaceSale extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "sale_id")
    private UUID saleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_id", nullable = false)
    private MarketplacePack pack;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_version_id", nullable = false)
    private MarketplacePackVersion packVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_entitlement_id")
    private MarketplaceEntitlement sourceEntitlement;

    @Column(name = "gross_coin_amount", nullable = false)
    private Integer grossCoinAmount;

    @Column(name = "gross_vnd_amount", nullable = false)
    private Long grossVndAmount;

    @Column(name = "coin_to_vnd_rate", nullable = false, precision = 12, scale = 4)
    private BigDecimal coinToVndRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MarketplaceSaleStatus status = MarketplaceSaleStatus.COMPLETED;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
}
