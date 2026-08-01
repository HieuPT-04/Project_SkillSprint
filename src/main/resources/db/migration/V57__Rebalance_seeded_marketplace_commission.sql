-- Keep the 50,000 Coin catalog price and buyer activity intact, but use a
-- credible 5% marketplace fee for seeded sales.  The previous 20% fee was
-- applied to all 244 demo purchases and inflated the admin dashboard to 2.2M.

UPDATE marketplace_sale_settlements settlement
SET creator_share_bps = 9500,
    creator_amount = sale.gross_coin_amount * 95 / 100,
    platform_share_bps = 500,
    platform_amount = sale.gross_coin_amount * 5 / 100,
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sales sale
WHERE settlement.sale_id = sale.sale_id
  AND (sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%');

UPDATE creator_earning_entries earning
SET amount = settlement.creator_amount,
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sale_settlements settlement
JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
WHERE earning.settlement_id = settlement.settlement_id
  AND (sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%');

UPDATE platform_revenue_entries revenue
SET amount = settlement.platform_amount,
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sale_settlements settlement
JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
WHERE revenue.settlement_id = settlement.settlement_id
  AND (sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%');

UPDATE platform_treasury_entries treasury
SET amount = settlement.platform_amount,
    note = 'Marketplace commission (5% seeded catalog rate)',
    metadata = jsonb_set(COALESCE(treasury.metadata, '{}'::jsonb), '{platformShareBps}', '500'::jsonb, TRUE),
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sale_settlements settlement
JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
WHERE treasury.entry_type = 'MARKETPLACE_COMMISSION_EARNED'
  AND treasury.reference_type = 'SALE'
  AND treasury.reference_id = sale.sale_id
  AND (sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%');

DO $$
BEGIN
    IF (SELECT count(*) FROM marketplace_sales
        WHERE idempotency_key LIKE 'v28-sale-%' OR idempotency_key LIKE 'v52-sale-%') <> 244
       OR (SELECT COALESCE(sum(platform_amount), 0) FROM marketplace_sale_settlements settlement
           JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
           WHERE sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%') <> 542500
       OR (SELECT COALESCE(sum(creator_amount), 0) FROM marketplace_sale_settlements settlement
           JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
           WHERE sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%') <> 10307500
       OR (SELECT COALESCE(sum(amount), 0) FROM platform_revenue_entries revenue
           JOIN marketplace_sales sale ON sale.sale_id = revenue.sale_id
           WHERE sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%') <> 542500
       OR (SELECT COALESCE(sum(treasury.amount), 0) FROM platform_treasury_entries treasury
           JOIN marketplace_sales sale ON sale.sale_id = treasury.reference_id
           WHERE treasury.entry_type = 'MARKETPLACE_COMMISSION_EARNED'
             AND treasury.reference_type = 'SALE'
             AND (sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%')) <> 542500 THEN
        RAISE EXCEPTION 'V57 postcondition failed; seeded marketplace commission ledger is inconsistent';
    END IF;
END $$;
