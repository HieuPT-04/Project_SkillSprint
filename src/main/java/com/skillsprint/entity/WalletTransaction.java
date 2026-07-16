package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Entity @Table(name = "wallet_transactions")
public class WalletTransaction extends BaseAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "transaction_id") private UUID transactionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "wallet_id", nullable = false) private UserWallet wallet;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private WalletTransactionDirection direction;
    @Column(nullable = false) private Integer amount;
    @Column(name = "balance_before", nullable = false) private Integer balanceBefore;
    @Column(name = "balance_after", nullable = false) private Integer balanceAfter;
    @Enumerated(EnumType.STRING) @Column(name = "reference_type", nullable = false, length = 40) private WalletTransactionReferenceType referenceType;
    @Column(name = "reference_id", nullable = false) private UUID referenceId;
}
