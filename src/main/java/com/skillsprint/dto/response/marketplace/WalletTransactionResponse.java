package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * One immutable Coin ledger entry, only ever returned for the calling user's own wallet.
 *
 * <p>{@code referenceType} plus {@code referenceId} say what moved the balance: for a
 * COIN_TOP_UP the reference is the payment id, so a credit can be traced back to the
 * top-up that caused it.
 */
@Getter @Builder
public class WalletTransactionResponse {
    UUID transactionId;
    WalletTransactionDirection direction;
    Integer amount;
    Integer balanceBefore;
    Integer balanceAfter;
    WalletTransactionReferenceType referenceType;
    UUID referenceId;
    Instant createdAt;
}
