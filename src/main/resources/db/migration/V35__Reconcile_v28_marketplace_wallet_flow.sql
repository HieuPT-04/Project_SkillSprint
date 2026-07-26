-- Complete the V28 marketplace money flow. V28 created sales, settlements and
-- revenue directly, but skipped the buyer wallet debit produced by the real
-- checkout service. This migration funds every paid V28 buyer from the existing
-- 1.5M VND Coin-top-up pool, then records the matching wallet purchase debit.

CREATE FUNCTION v35_v28_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v35_v30_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v30:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v30:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v30:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v30:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v30:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v35_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v35:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v35:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v35:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v35:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v35:' || seed), 21, 12)
    )::uuid;
$$;

-- V28 users must exist before their May-July marketplace activity. Keep this
-- isolated cohort outside the live registration timeline of real accounts.
WITH demo_users AS (
    SELECT u.user_id, row_number() OVER (ORDER BY u.user_id)::integer AS ordinal
    FROM users u
    WHERE u.user_id IN (
        SELECT v35_v28_uuid('user:' || n)::text FROM generate_series(1, 184) AS n
    )
)
UPDATE users u
SET created_at = TIMESTAMPTZ '2025-11-15 08:00:00+07'
                 + ((d.ordinal - 1) * INTERVAL '8 hours')
                 + ((get_byte(decode(md5(u.user_id), 'hex'), 0) % 12) * INTERVAL '1 hour'),
    updated_at = GREATEST(u.updated_at, TIMESTAMPTZ '2026-07-26 09:45:00+07')
FROM demo_users d
WHERE u.user_id = d.user_id;

CREATE TEMP TABLE v35_paid_sales ON COMMIT DROP AS
SELECT sale.sale_id,
       sale.buyer_id,
       sale.gross_coin_amount,
       sale.created_at AS sale_at,
       row_number() OVER (
           ORDER BY split_part(sale.idempotency_key, '-', 3)::integer,
                    split_part(sale.idempotency_key, '-', 4)::integer
       )::integer AS ordinal
FROM marketplace_sales sale
WHERE sale.idempotency_key LIKE 'v28-sale-%'
  AND sale.gross_coin_amount > 0;

CREATE TEMP TABLE v35_top_up_schedule ON COMMIT DROP AS
SELECT sale.ordinal,
       sale.sale_id,
       sale.buyer_id,
       sale.gross_coin_amount,
       sale.sale_at,
       v35_v30_uuid('coin-payment:' || sale.ordinal) AS payment_id,
       v35_v30_uuid('wallet:' || sale.ordinal) AS wallet_id,
       v35_v30_uuid('wallet-transaction:' || sale.ordinal) AS top_up_transaction_id,
       CASE WHEN sale.ordinal <= 75 THEN 10000 ELSE 50000 END AS coin_amount,
       CASE WHEN sale.ordinal <= 75 THEN 'COIN_10000' ELSE 'COIN_50000' END AS package_key,
       sale.sale_at - INTERVAL '1 day' - ((sale.ordinal % 7) * INTERVAL '1 hour') AS paid_at
FROM v35_paid_sales sale;

-- Existing V30 rows 1..56 are reassigned to paid buyers. Rows 57..90 are new,
-- keeping the original total: 75 x 10K + 15 x 50K = 1,500,000 VND/Coin.
UPDATE payment_transactions payment
SET user_id = schedule.buyer_id,
    coin_amount = schedule.coin_amount,
    coin_package_key = schedule.package_key,
    amount = schedule.coin_amount,
    txn_ref = 'SP2026C' || lpad(schedule.ordinal::text, 4, '0'),
    transfer_content = 'SP2026C' || lpad(schedule.ordinal::text, 4, '0'),
    expire_at = schedule.paid_at - INTERVAL '14 minutes',
    paid_at = schedule.paid_at,
    provider_transaction_id = 'SPC26-' || lpad(schedule.ordinal::text, 4, '0'),
    provider_reference_code = 'SPCREF26-' || lpad(schedule.ordinal::text, 4, '0'),
    raw_callback_data = jsonb_build_object('channel', 'SEPAY', 'purpose', 'COIN_TOP_UP', 'verified', true)::text,
    created_at = schedule.paid_at - INTERVAL '14 minutes',
    updated_at = schedule.paid_at
FROM v35_top_up_schedule schedule
WHERE payment.payment_id = schedule.payment_id;

INSERT INTO payment_transactions (
    payment_id, user_id, plan_id, purpose, coin_amount, coin_package_key, provider, status,
    txn_ref, amount, currency, subscription_months, transfer_content, expire_at, paid_at,
    provider_transaction_id, provider_reference_code, raw_callback_data, created_at, updated_at
)
SELECT
    schedule.payment_id, schedule.buyer_id, NULL, 'COIN_TOP_UP', schedule.coin_amount, schedule.package_key,
    'SEPAY', 'PAID', 'SP2026C' || lpad(schedule.ordinal::text, 4, '0'), schedule.coin_amount, 'VND', 0,
    'SP2026C' || lpad(schedule.ordinal::text, 4, '0'), schedule.paid_at - INTERVAL '14 minutes', schedule.paid_at,
    'SPC26-' || lpad(schedule.ordinal::text, 4, '0'), 'SPCREF26-' || lpad(schedule.ordinal::text, 4, '0'),
    jsonb_build_object('channel', 'SEPAY', 'purpose', 'COIN_TOP_UP', 'verified', true)::text,
    schedule.paid_at - INTERVAL '14 minutes', schedule.paid_at
FROM v35_top_up_schedule schedule
WHERE schedule.ordinal > 56;

UPDATE user_wallets wallet
SET user_id = schedule.buyer_id,
    balance = schedule.coin_amount - schedule.gross_coin_amount,
    created_at = schedule.paid_at,
    updated_at = schedule.sale_at
FROM v35_top_up_schedule schedule
WHERE wallet.wallet_id = schedule.wallet_id;

INSERT INTO user_wallets (wallet_id, user_id, balance, created_at, updated_at)
SELECT schedule.wallet_id,
       schedule.buyer_id,
       schedule.coin_amount - schedule.gross_coin_amount,
       schedule.paid_at,
       schedule.sale_at
FROM v35_top_up_schedule schedule
WHERE schedule.ordinal > 56;

UPDATE wallet_transactions transaction
SET wallet_id = schedule.wallet_id,
    direction = 'CREDIT',
    amount = schedule.coin_amount,
    balance_before = 0,
    balance_after = schedule.coin_amount,
    reference_type = 'COIN_TOP_UP',
    reference_id = schedule.payment_id,
    created_at = schedule.paid_at,
    updated_at = schedule.paid_at
FROM v35_top_up_schedule schedule
WHERE transaction.transaction_id = schedule.top_up_transaction_id;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT schedule.top_up_transaction_id,
       schedule.wallet_id,
       'CREDIT',
       schedule.coin_amount,
       0,
       schedule.coin_amount,
       'COIN_TOP_UP',
       schedule.payment_id,
       schedule.paid_at,
       schedule.paid_at
FROM v35_top_up_schedule schedule
WHERE schedule.ordinal > 56;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v35_uuid('marketplace-sale-debit:' || schedule.sale_id),
       schedule.wallet_id,
       'DEBIT',
       schedule.gross_coin_amount,
       schedule.coin_amount,
       schedule.coin_amount - schedule.gross_coin_amount,
       'MARKETPLACE_SALE',
       schedule.sale_id,
       schedule.sale_at,
       schedule.sale_at
FROM v35_top_up_schedule schedule
WHERE NOT EXISTS (
    SELECT 1
    FROM wallet_transactions transaction
    WHERE transaction.reference_type = 'MARKETPLACE_SALE'
      AND transaction.reference_id = schedule.sale_id
);

UPDATE platform_treasury_entries treasury
SET amount = schedule.coin_amount,
    counterparty_user_id = schedule.buyer_id,
    counterparty_name_snapshot = buyer.full_name,
    external_reference = 'SP2026C' || lpad(schedule.ordinal::text, 4, '0'),
    note = 'Coin top-up received',
    metadata = jsonb_build_object('channel', 'SEPAY', 'purpose', 'COIN_TOP_UP', 'packageKey', schedule.package_key),
    occurred_at = schedule.paid_at,
    created_at = schedule.paid_at,
    updated_at = schedule.paid_at
FROM v35_top_up_schedule schedule
JOIN users buyer ON buyer.user_id = schedule.buyer_id
WHERE treasury.reference_type = 'PAYMENT'
  AND treasury.reference_id = schedule.payment_id
  AND treasury.asset = 'VND'
  AND treasury.direction = 'CREDIT';

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot,
    external_reference, note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v35_uuid('coin-top-up-treasury:' || schedule.payment_id),
       'VND', 'CREDIT', 'COIN_TOP_UP_RECEIVED', 'PAYMENT', schedule.payment_id, schedule.coin_amount,
       'SYSTEM', schedule.buyer_id, buyer.full_name,
       'SP2026C' || lpad(schedule.ordinal::text, 4, '0'), 'Coin top-up received',
       jsonb_build_object('channel', 'SEPAY', 'purpose', 'COIN_TOP_UP', 'packageKey', schedule.package_key),
       schedule.paid_at, 'v35-coin-top-up:' || schedule.payment_id, schedule.paid_at, schedule.paid_at
FROM v35_top_up_schedule schedule
JOIN users buyer ON buyer.user_id = schedule.buyer_id
WHERE schedule.ordinal > 56;

DO $$
BEGIN
    IF (SELECT count(*) FROM v35_top_up_schedule) <> 90
       OR (SELECT COALESCE(sum(coin_amount), 0) FROM v35_top_up_schedule) <> 1500000
       OR (SELECT COALESCE(sum(gross_coin_amount), 0) FROM v35_top_up_schedule) <> 3600
       OR (SELECT count(*) FROM payment_transactions payment WHERE payment.payment_id IN (SELECT payment_id FROM v35_top_up_schedule)) <> 90
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions payment WHERE payment.payment_id IN (SELECT payment_id FROM v35_top_up_schedule)) <> 1500000
       OR (SELECT count(*) FROM wallet_transactions transaction
           WHERE transaction.reference_type = 'COIN_TOP_UP'
             AND transaction.reference_id IN (SELECT payment_id FROM v35_top_up_schedule)) <> 90
       OR (SELECT count(*) FROM wallet_transactions transaction
           WHERE transaction.reference_type = 'MARKETPLACE_SALE'
             AND transaction.reference_id IN (SELECT sale_id FROM v35_top_up_schedule)
             AND transaction.direction = 'DEBIT') <> 90
       OR (SELECT COALESCE(sum(amount), 0) FROM wallet_transactions transaction
           WHERE transaction.reference_type = 'MARKETPLACE_SALE'
             AND transaction.reference_id IN (SELECT sale_id FROM v35_top_up_schedule)
             AND transaction.direction = 'DEBIT') <> 3600
       OR EXISTS (
           SELECT 1
           FROM v35_top_up_schedule schedule
           JOIN user_wallets wallet ON wallet.wallet_id = schedule.wallet_id
           WHERE wallet.balance <> schedule.coin_amount - schedule.gross_coin_amount
       )
       OR EXISTS (
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
        RAISE EXCEPTION 'V35 postcondition failed; marketplace wallet flow is rolled back';
    END IF;
END $$;

DROP FUNCTION v35_uuid(TEXT);
DROP FUNCTION v35_v30_uuid(TEXT);
DROP FUNCTION v35_v28_uuid(TEXT);
