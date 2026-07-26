-- Repair the V28 marketplace settlement calculation and spread its historical
-- sales across the intended May-July demo window. V28 mistakenly multiplied a
-- Coin price by 200/800 instead of calculating the 20%/80% shares.

CREATE TEMP TABLE v31_v28_sale_schedule ON COMMIT DROP AS
SELECT sale_id,
       (TIMESTAMPTZ '2026-05-02 09:00:00+07'
        + ((row_number() OVER (ORDER BY sale_id) - 1) * INTERVAL '13 hours 30 minutes')) AS occurred_at
FROM marketplace_sales
WHERE idempotency_key LIKE 'v28-sale-%';

-- Coin is deliberately priced at 1 VND in this application. Keep the immutable
-- checkout values internally consistent with that application-owned rate.
UPDATE marketplace_sales sale
SET gross_vnd_amount = sale.gross_coin_amount,
    coin_to_vnd_rate = 1.0000,
    created_at = schedule.occurred_at,
    updated_at = schedule.occurred_at
FROM v31_v28_sale_schedule schedule
WHERE sale.sale_id = schedule.sale_id;

UPDATE marketplace_sale_settlements settlement
SET creator_amount = sale.gross_coin_amount * 80 / 100,
    platform_amount = sale.gross_coin_amount * 20 / 100,
    coin_to_vnd_rate = 1.0000,
    created_at = schedule.occurred_at,
    updated_at = schedule.occurred_at
FROM marketplace_sales sale
JOIN v31_v28_sale_schedule schedule ON schedule.sale_id = sale.sale_id
WHERE settlement.sale_id = sale.sale_id;

UPDATE creator_earning_entries earning
SET amount = settlement.creator_amount,
    created_at = schedule.occurred_at,
    updated_at = schedule.occurred_at
FROM marketplace_sale_settlements settlement
JOIN v31_v28_sale_schedule schedule ON schedule.sale_id = settlement.sale_id
WHERE earning.settlement_id = settlement.settlement_id;

UPDATE platform_revenue_entries revenue
SET amount = settlement.platform_amount,
    created_at = schedule.occurred_at,
    updated_at = schedule.occurred_at
FROM marketplace_sale_settlements settlement
JOIN v31_v28_sale_schedule schedule ON schedule.sale_id = settlement.sale_id
WHERE revenue.settlement_id = settlement.settlement_id;

UPDATE marketplace_entitlements entitlement
SET granted_at = schedule.occurred_at,
    created_at = schedule.occurred_at,
    updated_at = schedule.occurred_at
FROM v31_v28_sale_schedule schedule
WHERE entitlement.source_sale_id = schedule.sale_id;

UPDATE marketplace_ranked_attempts attempt
SET started_at = schedule.occurred_at + INTERVAL '10 minutes',
    expires_at = schedule.occurred_at + INTERVAL '1 hour 10 minutes',
    completed_at = schedule.occurred_at + INTERVAL '18 minutes',
    created_at = schedule.occurred_at + INTERVAL '10 minutes',
    updated_at = schedule.occurred_at + INTERVAL '18 minutes'
FROM marketplace_entitlements entitlement
JOIN v31_v28_sale_schedule schedule ON schedule.sale_id = entitlement.source_sale_id
WHERE attempt.buyer_id = entitlement.buyer_id
  AND attempt.pack_version_id = entitlement.pack_version_id
  AND attempt.definition_id IN (
      SELECT definition_id
      FROM marketplace_ranked_quiz_definitions
      WHERE pack_version_id = entitlement.pack_version_id
  );

DO $$
BEGIN
    IF (SELECT count(*) FROM v31_v28_sale_schedule) <> 150
       OR (SELECT COALESCE(sum(revenue.amount), 0)
           FROM platform_revenue_entries revenue
           JOIN v31_v28_sale_schedule schedule ON schedule.sale_id = revenue.sale_id) <> 720
       OR (SELECT count(*) FROM platform_revenue_entries revenue
           JOIN v31_v28_sale_schedule schedule ON schedule.sale_id = revenue.sale_id
           WHERE revenue.created_at >= TIMESTAMPTZ '2026-07-26 00:00:00+07') <> 0 THEN
        RAISE EXCEPTION 'V31 postcondition failed; V28 marketplace repair is rolled back';
    END IF;
END $$;
