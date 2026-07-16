package com.skillsprint.entity;

import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.enums.payment.PaymentStatus;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id")
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Set for {@link PaymentPurpose#SUBSCRIPTION} only; always null for a Coin top-up. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private ServicePlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private PaymentPurpose purpose = PaymentPurpose.SUBSCRIPTION;

    /** Coin credited on a verified top-up. Set for {@link PaymentPurpose#COIN_TOP_UP} only. */
    @Column(name = "coin_amount")
    private Integer coinAmount;

    /** The server-defined package this top-up was priced from. Top-ups only. */
    @Column(name = "coin_package_key", length = 50)
    private String coinPackageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private PaymentProvider provider = PaymentProvider.SEPAY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "txn_ref", nullable = false, unique = true, columnDefinition = "varchar(100)")
    private String txnRef;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "VND";

    @Column(name = "subscription_months", nullable = false)
    private Integer subscriptionMonths = 1;

    @Column(name = "qr_code_url", columnDefinition = "TEXT")
    private String qrCodeUrl;

    @Column(name = "bank_code", length = 50)
    private String bankCode;

    @Column(name = "bank_account_number", length = 100)
    private String bankAccountNumber;

    @Column(name = "bank_account_name", length = 255)
    private String bankAccountName;

    @Column(name = "transfer_content", length = 255)
    private String transferContent;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "provider_transaction_id", unique = true, columnDefinition = "varchar(100)")
    private String providerTransactionId;

    @Column(name = "provider_reference_code", columnDefinition = "varchar(100)")
    private String providerReferenceCode;

    @Column(name = "raw_callback_data", columnDefinition = "TEXT")
    private String rawCallbackData;
}
