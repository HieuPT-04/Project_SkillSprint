package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.MarketplacePaymentMethod;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
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
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketplace_purchases")
public class MarketplacePurchase extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "purchase_id")
    private UUID purchaseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private MarketplaceItem item;

    /** Nullable until every legacy row is backfilled; see V7 migration notes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_version_id")
    private MarketplacePackVersion packVersion;

    @Column(name = "price_coins", nullable = false)
    private Integer priceCoins;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private MarketplacePaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketplacePurchaseStatus status = MarketplacePurchaseStatus.PENDING;

    @Column(name = "purchased_at")
    private Instant purchasedAt;
}
