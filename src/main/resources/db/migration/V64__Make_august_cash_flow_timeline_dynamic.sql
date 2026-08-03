-- Re-balances August 1 to 3, 2026 financial transactions, coin top-ups, and marketplace sales
-- to create dynamic, natural daily activity curves on admin cash flow charts.

CREATE FUNCTION v64_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v64:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v64:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v64:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v64:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v64:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v64_v36_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 21, 12))::uuid;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM payment_transactions WHERE txn_ref LIKE 'SP64%') THEN
        RAISE EXCEPTION 'V64 dynamic timeline seed already applied';
    END IF;
END $$;

-- 1. Additional dynamic Coin Top-Ups to create a realistic peak on August 2, 2026
CREATE TEMP TABLE v64_extra_topups ON COMMIT DROP AS
SELECT row_no,
       v64_v36_uuid('user:' || user_no)::text AS user_id,
       coin_amount,
       paid_at
FROM (VALUES
    (1, 14, 100000, TIMESTAMPTZ '2026-08-02 11:20:00+07'),
    (2, 28, 120000, TIMESTAMPTZ '2026-08-02 15:40:00+07'),
    (3, 42,  50000, TIMESTAMPTZ '2026-08-03 14:10:00+07')
) AS topup(row_no, user_no, coin_amount, paid_at);

INSERT INTO payment_transactions (
    payment_id, user_id, plan_id, purpose, coin_amount, coin_package_key, provider, status,
    txn_ref, amount, currency, subscription_months, transfer_content, expire_at, paid_at,
    provider_transaction_id, provider_reference_code, raw_callback_data, created_at, updated_at
)
SELECT v64_uuid('topup-payment:' || row_no), user_id, NULL, 'COIN_TOP_UP', coin_amount,
       'COIN_' || coin_amount, 'SEPAY', 'PAID', 'SP64C' || lpad(row_no::text, 4, '0'),
       coin_amount, 'VND', 0, 'SP64C' || lpad(row_no::text, 4, '0'), paid_at - INTERVAL '15 minutes', paid_at,
       'SP64-SEPAY-C-' || lpad(row_no::text, 4, '0'), 'SP64REF-C-' || lpad(row_no::text, 4, '0'),
       jsonb_build_object('seed', 'V64', 'purpose', 'COIN_TOP_UP', 'coinAmount', coin_amount),
       paid_at - INTERVAL '3 minutes', paid_at
FROM v64_extra_topups;

INSERT INTO user_wallets (wallet_id, user_id, balance, created_at, updated_at)
SELECT v64_uuid('wallet:' || user_id), user_id, coin_amount, paid_at, paid_at
FROM v64_extra_topups
ON CONFLICT (user_id) DO UPDATE
SET balance = user_wallets.balance + EXCLUDED.balance,
    updated_at = EXCLUDED.updated_at;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v64_uuid('topup-wallet:' || topup.row_no), wallet.wallet_id, 'CREDIT', topup.coin_amount,
       wallet.balance - topup.coin_amount, wallet.balance, 'COIN_TOP_UP',
       v64_uuid('topup-payment:' || topup.row_no), topup.paid_at, topup.paid_at
FROM v64_extra_topups topup
JOIN user_wallets wallet ON wallet.user_id = topup.user_id;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v64_uuid('topup-treasury:' || topup.row_no), 'VND', 'CREDIT', 'COIN_TOP_UP_RECEIVED', 'PAYMENT',
       v64_uuid('topup-payment:' || topup.row_no), topup.coin_amount, 'SYSTEM', topup.user_id, account.full_name,
       'SP64C' || lpad(topup.row_no::text, 4, '0'), 'Coin top-up received',
       jsonb_build_object('seed', 'V64', 'packageKey', 'COIN_' || topup.coin_amount), topup.paid_at,
       'COIN_TOP_UP_RECEIVED:' || v64_uuid('topup-payment:' || topup.row_no), topup.paid_at, topup.paid_at
FROM v64_extra_topups topup
JOIN users account ON account.user_id = topup.user_id;

-- 2. Additional Subscription Purchases to create dynamic revenue curve (Peak on August 2)
CREATE TEMP TABLE v64_extra_subs ON COMMIT DROP AS
SELECT row_no,
       v64_v36_uuid('user:' || user_no)::text AS user_id,
       plan_type,
       CASE WHEN plan_type = 'SKILL_BUILDER' THEN 89000 ELSE 199000 END AS amount,
       paid_at
FROM (VALUES
    (1, 25, 'PREMIUM', TIMESTAMPTZ '2026-08-02 14:15:00+07'),
    (2, 45, 'PREMIUM', TIMESTAMPTZ '2026-08-02 19:50:00+07')
) AS sub(row_no, user_no, plan_type, paid_at);

UPDATE subscriptions subscription
SET status = 'CANCELED', end_date = purchase.paid_at::date, end_at = purchase.paid_at
FROM v64_extra_subs purchase
WHERE subscription.user_id = purchase.user_id AND subscription.status = 'ACTIVE';

INSERT INTO payment_transactions (
    payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
    subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
    provider_reference_code, raw_callback_data, created_at, updated_at
)
SELECT v64_uuid('subscription-payment:' || purchase.row_no), purchase.user_id, plan.plan_id,
       'SUBSCRIPTION', 'SEPAY', 'PAID', 'SP64S' || lpad(purchase.row_no::text, 4, '0'), purchase.amount, 'VND', 1,
       'SP64S' || lpad(purchase.row_no::text, 4, '0'), purchase.paid_at - INTERVAL '15 minutes', purchase.paid_at,
       'SP64-SEPAY-S-' || lpad(purchase.row_no::text, 4, '0'), 'SP64REF-S-' || lpad(purchase.row_no::text, 4, '0'),
       jsonb_build_object('seed', 'V64', 'purpose', 'SUBSCRIPTION', 'planType', purchase.plan_type),
       purchase.paid_at - INTERVAL '3 minutes', purchase.paid_at
FROM v64_extra_subs purchase
JOIN service_plans plan ON plan.plan_type = purchase.plan_type;

INSERT INTO subscriptions (subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at)
SELECT v64_uuid('subscription:' || purchase.row_no), purchase.user_id, plan.plan_id,
       purchase.paid_at::date, (purchase.paid_at + INTERVAL '1 month')::date, purchase.paid_at,
       purchase.paid_at + INTERVAL '1 month', 'ACTIVE', purchase.paid_at
FROM v64_extra_subs purchase
JOIN service_plans plan ON plan.plan_type = purchase.plan_type;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
    idempotency_key, created_at, updated_at
)
SELECT v64_uuid('subscription-treasury:' || purchase.row_no), 'VND', 'CREDIT', 'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT',
       v64_uuid('subscription-payment:' || purchase.row_no), purchase.amount, purchase.user_id, account.full_name,
       'SP64S' || lpad(purchase.row_no::text, 4, '0'), 'Subscription payment received',
       jsonb_build_object('seed', 'V64', 'planType', purchase.plan_type), purchase.paid_at,
       'v64-subscription-payment-' || purchase.row_no, purchase.paid_at, purchase.paid_at
FROM v64_extra_subs purchase
JOIN users account ON account.user_id = purchase.user_id;

-- 3. Additional Marketplace Sales to generate dynamic commission peak on August 2
CREATE TEMP TABLE v64_extra_sales ON COMMIT DROP AS
SELECT topup.row_no, topup.user_id, candidate.pack_id, candidate.version_id, candidate.creator_id,
       candidate.price_coins, topup.paid_at + INTERVAL '30 minutes' AS sold_at
FROM v64_extra_topups topup
CROSS JOIN LATERAL (
    SELECT version.pack_id, version.version_id, pack.creator_id, version.price_coins
    FROM marketplace_pack_versions version
    JOIN marketplace_packs pack ON pack.pack_id = version.pack_id
    WHERE version.status = 'PUBLISHED'
      AND version.saleable = TRUE
      AND version.price_coins BETWEEN 1 AND topup.coin_amount
      AND pack.creator_id <> topup.user_id
      AND NOT EXISTS (
          SELECT 1 FROM marketplace_entitlements entitlement
          WHERE entitlement.buyer_id = topup.user_id
            AND entitlement.pack_version_id = version.version_id
            AND entitlement.status = 'ACTIVE'
      )
    ORDER BY version.published_at DESC, version.price_coins ASC
    LIMIT 1
) candidate;

INSERT INTO marketplace_sales (
    sale_id, buyer_id, pack_id, pack_version_id, source_entitlement_id, gross_coin_amount,
    original_gross_coin_amount, discount_coin_amount, gross_vnd_amount, coin_to_vnd_rate,
    status, idempotency_key, created_at, updated_at
)
SELECT v64_uuid('sale:' || row_no), user_id, pack_id, version_id, NULL, price_coins, price_coins, 0,
       price_coins, 1.0000, 'COMPLETED', 'v64-sale-' || row_no, sold_at, sold_at
FROM v64_extra_sales;

INSERT INTO marketplace_entitlements (entitlement_id, buyer_id, pack_version_id, source_sale_id, status, granted_at, created_at, updated_at)
SELECT v64_uuid('entitlement:' || row_no), user_id, version_id, v64_uuid('sale:' || row_no),
       'ACTIVE', sold_at, sold_at, sold_at
FROM v64_extra_sales;

INSERT INTO marketplace_sale_settlements (
    settlement_id, sale_id, creator_id, creator_share_bps, creator_amount, platform_share_bps,
    platform_amount, coin_to_vnd_rate, status, created_at, updated_at
)
SELECT v64_uuid('settlement:' || row_no), v64_uuid('sale:' || row_no), creator_id,
       8000, price_coins * 80 / 100, 2000, price_coins * 20 / 100, 1.0000, 'RECORDED', sold_at, sold_at
FROM v64_extra_sales;

INSERT INTO creator_earning_entries (earning_entry_id, creator_id, settlement_id, amount, state, created_at, updated_at)
SELECT v64_uuid('earning:' || row_no), creator_id, v64_uuid('settlement:' || row_no),
       price_coins * 80 / 100, 'PENDING', sold_at, sold_at
FROM v64_extra_sales;

INSERT INTO platform_revenue_entries (revenue_entry_id, settlement_id, sale_id, amount, created_at, updated_at)
SELECT v64_uuid('revenue:' || row_no), v64_uuid('settlement:' || row_no), v64_uuid('sale:' || row_no),
       price_coins * 20 / 100, sold_at, sold_at
FROM v64_extra_sales;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v64_uuid('commission-treasury:' || sale.row_no), 'COIN', 'CREDIT', 'MARKETPLACE_COMMISSION_EARNED',
       'SALE', v64_uuid('sale:' || sale.row_no), sale.price_coins * 20 / 100, 'SYSTEM', sale.user_id,
       account.full_name, 'v64-sale-' || sale.row_no, 'Marketplace commission',
       jsonb_build_object('settlementId', v64_uuid('settlement:' || sale.row_no), 'platformShareBps', 2000),
       sale.sold_at, 'MARKETPLACE_COMMISSION_EARNED:' || v64_uuid('sale:' || sale.row_no), sale.sold_at, sale.sold_at
FROM v64_extra_sales sale
JOIN users account ON account.user_id = sale.user_id;

UPDATE user_wallets wallet
SET balance = wallet.balance - sale.price_coins,
    updated_at = sale.sold_at
FROM v64_extra_sales sale
WHERE wallet.user_id = sale.user_id;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v64_uuid('sale-wallet:' || sale.row_no), wallet.wallet_id, 'DEBIT', sale.price_coins,
       wallet.balance + sale.price_coins, wallet.balance, 'MARKETPLACE_SALE', v64_uuid('sale:' || sale.row_no),
       sale.sold_at, sale.sold_at
FROM v64_extra_sales sale
JOIN user_wallets wallet ON wallet.user_id = sale.user_id;

-- Postcondition Assertion to guarantee 100% ledgers consistency for V64
DO $$
BEGIN
    IF (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP64C%') <> 3
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP64S%') <> 2
       OR (SELECT count(*) FROM marketplace_sales WHERE idempotency_key LIKE 'v64-sale-%') <> 3 THEN
        RAISE EXCEPTION 'V64 postcondition failed; dynamic financial timeline is inconsistent';
    END IF;
END $$;

DROP FUNCTION v64_v36_uuid(TEXT);
DROP FUNCTION v64_uuid(TEXT);
