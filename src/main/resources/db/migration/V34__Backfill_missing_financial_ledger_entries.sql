-- Reconcile immutable financial source records with the system treasury.
-- V27 inserted some paid payments after V25 had already backfilled receipts,
-- while V28 records marketplace revenue directly. Add only demonstrably missing
-- ledger rows; never rewrite a payment, sale, settlement, refund or wallet.

CREATE FUNCTION v34_ledger_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v34:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v34:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v34:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v34:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v34:' || seed), 21, 12)
    )::uuid;
$$;

-- A paid subscription or Coin top-up needs exactly one VND credit receipt for
-- its full payment amount. This backfills only payments with no receipt at all.
INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot,
    external_reference, note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT
    v34_ledger_uuid('payment-receipt:' || payment.payment_id),
    'VND',
    'CREDIT',
    CASE WHEN payment.purpose = 'COIN_TOP_UP'
        THEN 'COIN_TOP_UP_RECEIVED'
        ELSE 'SUBSCRIPTION_PAYMENT_RECEIVED'
    END,
    'PAYMENT',
    payment.payment_id,
    payment.amount,
    'SYSTEM',
    payment.user_id,
    user_row.full_name,
    COALESCE(payment.provider_transaction_id, payment.txn_ref),
    'Backfilled verified payment receipt',
    jsonb_build_object('purpose', payment.purpose, 'source', 'V34 ledger reconciliation'),
    COALESCE(payment.paid_at, payment.updated_at),
    'v34-payment-receipt:' || payment.payment_id,
    COALESCE(payment.paid_at, payment.updated_at),
    COALESCE(payment.paid_at, payment.updated_at)
FROM payment_transactions payment
JOIN users user_row ON user_row.user_id = payment.user_id
WHERE payment.status = 'PAID'
  AND payment.purpose IN ('SUBSCRIPTION', 'COIN_TOP_UP')
  AND NOT EXISTS (
      SELECT 1
      FROM platform_treasury_entries treasury
      WHERE treasury.reference_type = 'PAYMENT'
        AND treasury.reference_id = payment.payment_id
        AND treasury.asset = 'VND'
        AND treasury.direction = 'CREDIT'
  );

-- Every recognized marketplace revenue entry needs one matching Coin commission
-- credit. V28 has 90 paid sales; free packs correctly have no revenue row.
INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT
    v34_ledger_uuid('marketplace-commission:' || revenue.sale_id),
    'COIN',
    'CREDIT',
    'MARKETPLACE_COMMISSION_EARNED',
    'SALE',
    revenue.sale_id,
    revenue.amount,
    'SYSTEM',
    sale.buyer_id,
    buyer.full_name,
    'Backfilled marketplace commission',
    jsonb_build_object('settlementId', revenue.settlement_id, 'source', 'V34 ledger reconciliation'),
    revenue.created_at,
    'v34-marketplace-commission:' || revenue.sale_id,
    revenue.created_at,
    revenue.created_at
FROM platform_revenue_entries revenue
JOIN marketplace_sales sale ON sale.sale_id = revenue.sale_id
JOIN users buyer ON buyer.user_id = sale.buyer_id
WHERE NOT EXISTS (
    SELECT 1
    FROM platform_treasury_entries treasury
    WHERE treasury.reference_type = 'SALE'
      AND treasury.reference_id = revenue.sale_id
      AND treasury.asset = 'COIN'
      AND treasury.direction = 'CREDIT'
      AND treasury.entry_type = 'MARKETPLACE_COMMISSION_EARNED'
);

DO $$
BEGIN
    -- Payment-side reconciliation: exactly one full VND receipt per paid source.
    IF EXISTS (
        SELECT 1
        FROM payment_transactions payment
        WHERE payment.status = 'PAID'
          AND payment.purpose IN ('SUBSCRIPTION', 'COIN_TOP_UP')
          AND payment.amount <> COALESCE((
              SELECT sum(treasury.amount)
              FROM platform_treasury_entries treasury
              WHERE treasury.reference_type = 'PAYMENT'
                AND treasury.reference_id = payment.payment_id
                AND treasury.asset = 'VND'
                AND treasury.direction = 'CREDIT'
          ), 0)
    ) THEN
        RAISE EXCEPTION 'V34 payment receipt reconciliation failed';
    END IF;

    -- Marketplace-side reconciliation: a sale's recognized revenue and its
    -- commission credit must agree exactly. Refund debits remain separate.
    IF EXISTS (
        SELECT 1
        FROM platform_revenue_entries revenue
        WHERE revenue.amount <> COALESCE((
            SELECT sum(treasury.amount)
            FROM platform_treasury_entries treasury
            WHERE treasury.reference_type = 'SALE'
              AND treasury.reference_id = revenue.sale_id
              AND treasury.asset = 'COIN'
              AND treasury.direction = 'CREDIT'
              AND treasury.entry_type = 'MARKETPLACE_COMMISSION_EARNED'
        ), 0)
    ) THEN
        RAISE EXCEPTION 'V34 marketplace commission reconciliation failed';
    END IF;
END $$;

DROP FUNCTION v34_ledger_uuid(TEXT);
