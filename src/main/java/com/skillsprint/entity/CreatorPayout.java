package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.CreatorPayoutStatus;
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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "creator_payouts")
public class CreatorPayout extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payout_id")
    private UUID payoutId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private CreatorPayoutDestination destination;

    @Column(name = "requested_amount", nullable = false)
    private Integer requestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreatorPayoutStatus status = CreatorPayoutStatus.REQUESTED;

    @Column(name = "destination_bank_name", nullable = false, length = 150)
    private String destinationBankName;

    @Column(name = "destination_bank_code", length = 50)
    private String destinationBankCode;

    @Column(name = "destination_account_holder", nullable = false, length = 200)
    private String destinationAccountHolder;

    @Column(name = "destination_account_number_encrypted", columnDefinition = "TEXT")
    private String destinationAccountNumberEncrypted;

    @Column(name = "destination_qr_object_key", nullable = false, length = 512)
    private String destinationQrObjectKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_actor_id")
    private User adminActor;

    @Column(name = "external_transfer_reference", length = 200)
    private String externalTransferReference;

    @Column(name = "paid_vnd_amount", precision = 19, scale = 2)
    private BigDecimal paidVndAmount;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
