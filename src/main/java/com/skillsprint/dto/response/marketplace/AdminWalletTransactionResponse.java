package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.WalletTransactionDirection;
import com.skillsprint.enums.marketplace.WalletTransactionReferenceType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/** Admin-only wallet ledger view, including the accountable actor for manual adjustments. */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminWalletTransactionResponse {

    UUID transactionId;
    WalletTransactionDirection direction;
    Integer amount;
    Integer balanceBefore;
    Integer balanceAfter;
    WalletTransactionReferenceType referenceType;
    String adjustedByUserId;
    String adjustedByName;
    String adjustmentReason;
    Instant createdAt;
}
