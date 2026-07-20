package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.MarketplaceDisputeReason;
import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
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

/**
 * A buyer-initiated refund dispute for a completed marketplace sale.
 *
 * <p>APPROVED records only the admin decision. Money movement happens in a separate, explicit,
 * idempotent refund-completion step which transitions the dispute to REFUNDED and records the
 * compensating wallet ledger transaction id.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketplace_refund_disputes")
public class MarketplaceRefundDispute extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "dispute_id")
    private UUID disputeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private MarketplaceSale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_version_id", nullable = false)
    private MarketplacePackVersion packVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private MarketplaceDisputeReason reason;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MarketplaceDisputeStatus status = MarketplaceDisputeStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_actor_id")
    private User adminActor;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    /** The buyer wallet credit amount recorded when the refund completed. */
    @Column(name = "refund_coin_amount")
    private Integer refundCoinAmount;

    /** Ledger link to the compensating wallet CREDIT transaction. */
    @Column(name = "refund_wallet_transaction_id")
    private UUID refundWalletTransactionId;
}
