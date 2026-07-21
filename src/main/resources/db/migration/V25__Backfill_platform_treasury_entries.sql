-- Build the internal treasury from legacy immutable financial records. This is a
-- reconciliation aid only: it never infers bank cash from a Coin balance.

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot,
    external_reference, note, metadata, occurred_at, idempotency_key
)
SELECT gen_random_uuid(), 'VND', 'CREDIT',
       CASE WHEN payment.purpose = 'COIN_TOP_UP' THEN 'COIN_TOP_UP_RECEIVED' ELSE 'SUBSCRIPTION_PAYMENT_RECEIVED' END,
       'PAYMENT', payment.payment_id, payment.amount, 'SYSTEM', payment.user_id, user_row.full_name,
       payment.provider_transaction_id, 'Backfilled paid payment',
       jsonb_build_object('purpose', payment.purpose), COALESCE(payment.paid_at, payment.updated_at),
       CASE WHEN payment.purpose = 'COIN_TOP_UP' THEN 'COIN_TOP_UP_RECEIVED' ELSE 'SUBSCRIPTION_PAYMENT_RECEIVED' END || ':' || payment.payment_id
FROM payment_transactions payment
JOIN users user_row ON user_row.user_id = payment.user_id
WHERE payment.status = 'PAID'
  AND payment.purpose IN ('COIN_TOP_UP', 'SUBSCRIPTION')
ON CONFLICT (idempotency_key) DO NOTHING;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot,
    note, metadata, occurred_at, idempotency_key
)
SELECT gen_random_uuid(), 'COIN', 'CREDIT', 'MARKETPLACE_COMMISSION_EARNED', 'SALE', revenue.sale_id,
       revenue.amount, 'SYSTEM', sale.buyer_id, buyer.full_name, 'Backfilled Marketplace commission',
       jsonb_build_object('settlementId', revenue.settlement_id), revenue.created_at,
       'MARKETPLACE_COMMISSION_EARNED:' || revenue.sale_id
FROM platform_revenue_entries revenue
JOIN marketplace_sales sale ON sale.sale_id = revenue.sale_id
JOIN users buyer ON buyer.user_id = sale.buyer_id
ON CONFLICT (idempotency_key) DO NOTHING;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_user_id, actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot,
    external_reference, note, metadata, occurred_at, idempotency_key
)
SELECT gen_random_uuid(), 'VND', 'DEBIT', 'CREATOR_PAYOUT_COMPLETED', 'CREATOR_PAYOUT', payout.payout_id,
       payout.paid_vnd_amount, payout.admin_actor_id, COALESCE(admin_user.full_name, 'SYSTEM'),
       payout.creator_id, creator.full_name, payout.external_transfer_reference, 'Backfilled Creator payout',
       jsonb_build_object('requestedCoinAmount', payout.requested_amount), payout.updated_at,
       'CREATOR_PAYOUT_COMPLETED:' || payout.payout_id
FROM creator_payouts payout
JOIN users creator ON creator.user_id = payout.creator_id
LEFT JOIN users admin_user ON admin_user.user_id = payout.admin_actor_id
WHERE payout.status = 'COMPLETED' AND payout.paid_vnd_amount IS NOT NULL
ON CONFLICT (idempotency_key) DO NOTHING;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_user_id, actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot,
    note, metadata, occurred_at, idempotency_key
)
SELECT gen_random_uuid(), 'COIN', 'DEBIT', 'MARKETPLACE_COMMISSION_REVERSED', 'DISPUTE', dispute.dispute_id,
       settlement.platform_amount, dispute.admin_actor_id, COALESCE(admin_user.full_name, 'SYSTEM'),
       sale.buyer_id, buyer.full_name, 'Backfilled Marketplace commission reversal',
       jsonb_build_object('saleId', sale.sale_id, 'refundCoinAmount', dispute.refund_coin_amount), dispute.refunded_at,
       'MARKETPLACE_COMMISSION_REVERSED:' || dispute.dispute_id
FROM marketplace_refund_disputes dispute
JOIN marketplace_sales sale ON sale.sale_id = dispute.sale_id
JOIN marketplace_sale_settlements settlement ON settlement.sale_id = sale.sale_id
JOIN users buyer ON buyer.user_id = sale.buyer_id
LEFT JOIN users admin_user ON admin_user.user_id = dispute.admin_actor_id
WHERE dispute.status = 'REFUNDED'
ON CONFLICT (idempotency_key) DO NOTHING;
