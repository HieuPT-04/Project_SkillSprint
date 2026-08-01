-- The catalog can advertise 50,000 Coin today without rewriting what learners
-- paid in the past. Restore the V28 historical sale amounts and the 20%/80%
-- marketplace split used throughout the product.

UPDATE marketplace_sales sale
SET gross_coin_amount = CASE split_part(sale.idempotency_key, '-', 3)
                            WHEN '1' THEN 50
                            WHEN '2' THEN 0
                            WHEN '3' THEN 30
                            WHEN '4' THEN 40
                            WHEN '5' THEN 0
                        END,
    original_gross_coin_amount = CASE split_part(sale.idempotency_key, '-', 3)
                                     WHEN '1' THEN 50
                                     WHEN '2' THEN 0
                                     WHEN '3' THEN 30
                                     WHEN '4' THEN 40
                                     WHEN '5' THEN 0
                                 END,
    discount_coin_amount = 0,
    gross_vnd_amount = CASE split_part(sale.idempotency_key, '-', 3)
                           WHEN '1' THEN 50
                           WHEN '2' THEN 0
                           WHEN '3' THEN 30
                           WHEN '4' THEN 40
                           WHEN '5' THEN 0
                       END,
    coin_to_vnd_rate = 1.0000,
    updated_at = CURRENT_TIMESTAMP
WHERE sale.idempotency_key LIKE 'v28-sale-%';

UPDATE marketplace_sale_settlements settlement
SET creator_share_bps = 8000,
    creator_amount = sale.gross_coin_amount * 80 / 100,
    platform_share_bps = 2000,
    platform_amount = sale.gross_coin_amount * 20 / 100,
    coin_to_vnd_rate = 1.0000,
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

-- V55 created credits for previously free V28 packs. They are not commission
-- events in the historical ledger, so remove only those V55-generated rows.
DELETE FROM platform_treasury_entries treasury
USING marketplace_sales sale
WHERE treasury.idempotency_key LIKE 'v55-normalized-v28-commission:%'
  AND treasury.reference_id = sale.sale_id
  AND sale.idempotency_key LIKE 'v28-sale-%'
  AND sale.gross_coin_amount = 0;

UPDATE platform_treasury_entries treasury
SET amount = settlement.platform_amount,
    note = 'Marketplace commission (20% historical rate)',
    metadata = jsonb_set(COALESCE(treasury.metadata, '{}'::jsonb), '{platformShareBps}', '2000'::jsonb, TRUE),
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sale_settlements settlement
JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
WHERE treasury.entry_type = 'MARKETPLACE_COMMISSION_EARNED'
  AND treasury.reference_type = 'SALE'
  AND treasury.reference_id = sale.sale_id
  AND (sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%');

DO $$
BEGIN
    IF (SELECT COALESCE(sum(gross_coin_amount), 0) FROM marketplace_sales
        WHERE idempotency_key LIKE 'v28-sale-%') <> 3600
       OR (SELECT COALESCE(sum(platform_amount), 0) FROM marketplace_sale_settlements settlement
           JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
           WHERE sale.idempotency_key LIKE 'v28-sale-%') <> 720
       OR (SELECT COALESCE(sum(platform_amount), 0) FROM marketplace_sale_settlements settlement
           JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
           WHERE sale.idempotency_key LIKE 'v52-sale-%') <> 670000
       OR (SELECT COALESCE(sum(amount), 0) FROM platform_revenue_entries revenue
           JOIN marketplace_sales sale ON sale.sale_id = revenue.sale_id
           WHERE sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%') <> 670720
       OR (SELECT COALESCE(sum(treasury.amount), 0) FROM platform_treasury_entries treasury
           JOIN marketplace_sales sale ON sale.sale_id = treasury.reference_id
           WHERE treasury.entry_type = 'MARKETPLACE_COMMISSION_EARNED'
             AND treasury.reference_type = 'SALE'
             AND (sale.idempotency_key LIKE 'v28-sale-%' OR sale.idempotency_key LIKE 'v52-sale-%')) <> 670720 THEN
        RAISE EXCEPTION 'V58 postcondition failed; historical marketplace commission was not restored';
    END IF;
END $$;
