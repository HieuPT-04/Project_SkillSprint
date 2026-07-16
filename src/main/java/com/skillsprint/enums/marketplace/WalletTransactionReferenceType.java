package com.skillsprint.enums.marketplace;

/**
 * What a wallet ledger entry refers to. The longest name must fit
 * {@code wallet_transactions.reference_type}, widened to VARCHAR(40) in V8.
 */
public enum WalletTransactionReferenceType {
    ADMIN_ADJUSTMENT,
    MARKETPLACE_PURCHASE,
    MARKETPLACE_EARNING,
    /** Credit from a verified SePay VND top-up; {@code referenceId} is the payment id. */
    COIN_TOP_UP,
    /** Reserved for Plan 3 settlement: the creator's share of a sale. */
    MARKETPLACE_CREATOR_EARNING,
    /** Reserved for Plan 3 settlement: the platform's commission on a sale. */
    MARKETPLACE_PLATFORM_COMMISSION,
    /** Reserved for Plan 3: an admin-controlled debit of creator earnings. */
    CREATOR_PAYOUT
}
