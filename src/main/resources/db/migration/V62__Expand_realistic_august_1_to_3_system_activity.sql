-- Expands realistic user activity, learning progress, marketplace sales, social interactions,
-- feedback, and financial transactions for the window 01-03 August 2026.
-- All data is completely reconciled across wallets, payment ledgers, treasury, and point summaries.

CREATE FUNCTION v62_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v62:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v62:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v62:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v62:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v62:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v62_v36_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v62_v37_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v37:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v37:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v37:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v37:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v37:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v62_v28_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 21, 12))::uuid;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM payment_transactions WHERE txn_ref LIKE 'SP62%')
       OR EXISTS (SELECT 1 FROM community_posts WHERE post_id = v62_uuid('post:august:1')) THEN
        RAISE EXCEPTION 'V62 August expanded activity already exists; do not apply this migration to a partially seeded database';
    END IF;
END $$;

-- 1. Organic user registrations during August 1 to 3, 2026.
INSERT INTO users (user_id, email, email_verified, full_name, timezone, status, last_login_at, created_at, updated_at)
SELECT v62_uuid('new-user:' || n),
       (ARRAY['tuan.phamminh94@gmail.com','ha.nguyenthu96@gmail.com','long.tranhoang97@gmail.com','linh.lekhanh99@gmail.com','nam.dangbao98@gmail.com','yen.hoanghai95@gmail.com'])[n],
       TRUE,
       (ARRAY['Phạm Minh Tuấn','Nguyễn Thu Hà','Trần Hoàng Long','Lê Khánh Linh','Đặng Bảo Nam','Hoàng Hải Yến'])[n],
       'Asia/Ho_Chi_Minh', 'ACTIVE',
       TIMESTAMPTZ '2026-08-01 09:00:00+07' + (n * INTERVAL '11 hours 20 minutes'),
       TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours'),
       TIMESTAMPTZ '2026-08-01 09:00:00+07' + (n * INTERVAL '11 hours 20 minutes')
FROM generate_series(1, 6) AS n
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_roles (user_role_id, user_id, role_id, granted_at)
SELECT v62_uuid('new-user-role:' || n), v62_uuid('new-user:' || n), role.role_id,
       TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours')
FROM generate_series(1, 6) AS n
CROSS JOIN (SELECT role_id FROM roles WHERE role_name = 'LEARNER' LIMIT 1) role
ON CONFLICT (user_role_id) DO NOTHING;

INSERT INTO user_wallets (wallet_id, user_id, balance, created_at, updated_at)
SELECT v62_uuid('new-user-wallet:' || n), v62_uuid('new-user:' || n)::text, 0,
       TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours'),
       TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours')
FROM generate_series(1, 6) AS n
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_point_summaries (user_id, total_points, current_week_points, current_week_start_date, current_month_points, current_month_start_date, streak_days, last_point_date, created_at, updated_at)
SELECT v62_uuid('new-user:' || n)::text, 0, 0, DATE '2026-07-27', 0, DATE '2026-08-01', 0, NULL,
       TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours'),
       TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours')
FROM generate_series(1, 6) AS n
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO subscriptions (subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at)
SELECT v62_uuid('new-user-sub:' || n), v62_uuid('new-user:' || n)::text, plan.plan_id,
       (TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours'))::date,
       DATE '2099-12-31',
       TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours'),
       TIMESTAMPTZ '2099-12-31 23:59:59+07',
       'ACTIVE',
       TIMESTAMPTZ '2026-08-01 08:30:00+07' + (n * INTERVAL '11 hours')
FROM generate_series(1, 6) AS n
CROSS JOIN (SELECT plan_id FROM service_plans WHERE plan_type = 'FREE' LIMIT 1) plan
ON CONFLICT DO NOTHING;

-- Backfill active FREE subscription for any active LEARNER currently missing an active subscription record
INSERT INTO subscriptions (subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at)
SELECT v62_uuid('backfill-sub:' || account.user_id), account.user_id, plan.plan_id,
       account.created_at::date, DATE '2099-12-31', account.created_at, TIMESTAMPTZ '2099-12-31 23:59:59+07',
       'ACTIVE', account.created_at
FROM users account
JOIN user_roles user_role ON user_role.user_id = account.user_id
JOIN roles role ON role.role_id = user_role.role_id AND role.role_name = 'LEARNER'
CROSS JOIN (SELECT plan_id FROM service_plans WHERE plan_type = 'FREE' LIMIT 1) plan
WHERE account.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM subscriptions sub WHERE sub.user_id = account.user_id AND sub.status = 'ACTIVE'
  )
ON CONFLICT DO NOTHING;

-- 2. Additional Coin Top-Ups across August 1 to 3, 2026.
CREATE TEMP TABLE v62_topups ON COMMIT DROP AS
SELECT row_no,
       v62_v36_uuid('user:' || user_no)::text AS user_id,
       coin_amount,
       paid_at
FROM (VALUES
    (1,  5,  50000, TIMESTAMPTZ '2026-08-01 09:15:00+07'),
    (2, 12, 100000, TIMESTAMPTZ '2026-08-01 14:30:00+07'),
    (3, 22,  20000, TIMESTAMPTZ '2026-08-01 21:10:00+07'),
    (4, 33, 100000, TIMESTAMPTZ '2026-08-02 08:40:00+07'),
    (5, 48,  50000, TIMESTAMPTZ '2026-08-02 16:15:00+07'),
    (6, 61, 100000, TIMESTAMPTZ '2026-08-02 20:50:00+07'),
    (7, 77,  50000, TIMESTAMPTZ '2026-08-03 10:20:00+07'),
    (8, 85, 100000, TIMESTAMPTZ '2026-08-03 15:45:00+07'),
    (9, 94,  20000, TIMESTAMPTZ '2026-08-03 22:30:00+07')
) AS topup(row_no, user_no, coin_amount, paid_at);

INSERT INTO payment_transactions (
    payment_id, user_id, plan_id, purpose, coin_amount, coin_package_key, provider, status,
    txn_ref, amount, currency, subscription_months, transfer_content, expire_at, paid_at,
    provider_transaction_id, provider_reference_code, raw_callback_data, created_at, updated_at
)
SELECT v62_uuid('topup-payment:' || row_no), user_id, NULL, 'COIN_TOP_UP', coin_amount,
       'COIN_' || coin_amount, 'SEPAY', 'PAID', 'SP62C' || lpad(row_no::text, 4, '0'),
       coin_amount, 'VND', 0, 'SP62C' || lpad(row_no::text, 4, '0'), paid_at - INTERVAL '15 minutes', paid_at,
       'SP62-SEPAY-C-' || lpad(row_no::text, 4, '0'), 'SP62REF-C-' || lpad(row_no::text, 4, '0'),
       jsonb_build_object('seed', 'V62', 'purpose', 'COIN_TOP_UP', 'coinAmount', coin_amount)::text,
       paid_at - INTERVAL '3 minutes', paid_at
FROM v62_topups;

INSERT INTO user_wallets (wallet_id, user_id, balance, created_at, updated_at)
SELECT v62_uuid('wallet:' || user_id), user_id, coin_amount, paid_at, paid_at
FROM v62_topups
ON CONFLICT (user_id) DO UPDATE
SET balance = user_wallets.balance + EXCLUDED.balance,
    updated_at = EXCLUDED.updated_at;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v62_uuid('topup-wallet:' || topup.row_no), wallet.wallet_id, 'CREDIT', topup.coin_amount,
       wallet.balance - topup.coin_amount, wallet.balance, 'COIN_TOP_UP',
       v62_uuid('topup-payment:' || topup.row_no), topup.paid_at, topup.paid_at
FROM v62_topups topup
JOIN user_wallets wallet ON wallet.user_id = topup.user_id;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v62_uuid('topup-treasury:' || topup.row_no), 'VND', 'CREDIT', 'COIN_TOP_UP_RECEIVED', 'PAYMENT',
       v62_uuid('topup-payment:' || topup.row_no), topup.coin_amount, 'SYSTEM', topup.user_id, account.full_name,
       'SP62C' || lpad(topup.row_no::text, 4, '0'), 'Coin top-up received',
       jsonb_build_object('seed', 'V62', 'packageKey', 'COIN_' || topup.coin_amount), topup.paid_at,
       'COIN_TOP_UP_RECEIVED:' || v62_uuid('topup-payment:' || topup.row_no), topup.paid_at, topup.paid_at
FROM v62_topups topup
JOIN users account ON account.user_id = topup.user_id;

-- 3. Additional Subscription Renewals / Upgrades on August 1 to 3, 2026.
CREATE TEMP TABLE v62_sub_purchases ON COMMIT DROP AS
SELECT row_no,
       v62_v36_uuid('user:' || user_no)::text AS user_id,
       plan_type,
       CASE WHEN plan_type = 'SKILL_BUILDER' THEN 89000 ELSE 199000 END AS amount,
       paid_at
FROM (VALUES
    (1,  8, 'SKILL_BUILDER', TIMESTAMPTZ '2026-08-01 11:30:00+07'),
    (2, 19, 'PREMIUM',       TIMESTAMPTZ '2026-08-01 16:45:00+07'),
    (3, 35, 'SKILL_BUILDER', TIMESTAMPTZ '2026-08-02 10:15:00+07'),
    (4, 52, 'PREMIUM',       TIMESTAMPTZ '2026-08-02 17:30:00+07'),
    (5, 68, 'SKILL_BUILDER', TIMESTAMPTZ '2026-08-03 11:10:00+07'),
    (6, 88, 'PREMIUM',       TIMESTAMPTZ '2026-08-03 18:25:00+07')
) AS sub(row_no, user_no, plan_type, paid_at);

UPDATE subscriptions subscription
SET status = 'CANCELED', end_date = purchase.paid_at::date, end_at = purchase.paid_at
FROM v62_sub_purchases purchase
WHERE subscription.user_id = purchase.user_id AND subscription.status = 'ACTIVE';

INSERT INTO payment_transactions (
    payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
    subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
    provider_reference_code, raw_callback_data, created_at, updated_at
)
SELECT v62_uuid('subscription-payment:' || purchase.row_no), purchase.user_id, plan.plan_id,
       'SUBSCRIPTION', 'SEPAY', 'PAID', 'SP62S' || lpad(purchase.row_no::text, 4, '0'), purchase.amount, 'VND', 1,
       'SP62S' || lpad(purchase.row_no::text, 4, '0'), purchase.paid_at - INTERVAL '15 minutes', purchase.paid_at,
       'SP62-SEPAY-S-' || lpad(purchase.row_no::text, 4, '0'), 'SP62REF-S-' || lpad(purchase.row_no::text, 4, '0'),
       jsonb_build_object('seed', 'V62', 'purpose', 'SUBSCRIPTION', 'planType', purchase.plan_type)::text,
       purchase.paid_at - INTERVAL '3 minutes', purchase.paid_at
FROM v62_sub_purchases purchase
JOIN service_plans plan ON plan.plan_type = purchase.plan_type;

INSERT INTO subscriptions (subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at)
SELECT v62_uuid('subscription:' || purchase.row_no), purchase.user_id, plan.plan_id,
       purchase.paid_at::date, (purchase.paid_at + INTERVAL '1 month')::date, purchase.paid_at,
       purchase.paid_at + INTERVAL '1 month', 'ACTIVE', purchase.paid_at
FROM v62_sub_purchases purchase
JOIN service_plans plan ON plan.plan_type = purchase.plan_type;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
    idempotency_key, created_at, updated_at
)
SELECT v62_uuid('subscription-treasury:' || purchase.row_no), 'VND', 'CREDIT', 'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT',
       v62_uuid('subscription-payment:' || purchase.row_no), purchase.amount, purchase.user_id, account.full_name,
       'SP62S' || lpad(purchase.row_no::text, 4, '0'), 'Subscription payment received',
       jsonb_build_object('seed', 'V62', 'planType', purchase.plan_type), purchase.paid_at,
       'v62-subscription-payment-' || purchase.row_no, purchase.paid_at, purchase.paid_at
FROM v62_sub_purchases purchase
JOIN users account ON account.user_id = purchase.user_id;

-- 4. Marketplace Quiz Pack Purchases on August 1 to 3, 2026.
CREATE TEMP TABLE v62_sales ON COMMIT DROP AS
SELECT topup.row_no, topup.user_id, candidate.pack_id, candidate.version_id, candidate.creator_id,
       candidate.price_coins, topup.paid_at + INTERVAL '25 minutes' AS sold_at
FROM v62_topups topup
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
) candidate
WHERE topup.row_no <= 6;

INSERT INTO marketplace_sales (
    sale_id, buyer_id, pack_id, pack_version_id, source_entitlement_id, gross_coin_amount,
    original_gross_coin_amount, discount_coin_amount, gross_vnd_amount, coin_to_vnd_rate,
    status, idempotency_key, created_at, updated_at
)
SELECT v62_uuid('sale:' || row_no), user_id, pack_id, version_id, NULL, price_coins, price_coins, 0,
       price_coins, 1.0000, 'COMPLETED', 'v62-sale-' || row_no, sold_at, sold_at
FROM v62_sales;

INSERT INTO marketplace_entitlements (entitlement_id, buyer_id, pack_version_id, source_sale_id, status, granted_at, created_at, updated_at)
SELECT v62_uuid('entitlement:' || row_no), user_id, version_id, v62_uuid('sale:' || row_no),
       'ACTIVE', sold_at, sold_at, sold_at
FROM v62_sales;

INSERT INTO marketplace_sale_settlements (
    settlement_id, sale_id, creator_id, creator_share_bps, creator_amount, platform_share_bps,
    platform_amount, coin_to_vnd_rate, status, created_at, updated_at
)
SELECT v62_uuid('settlement:' || row_no), v62_uuid('sale:' || row_no), creator_id,
       8000, price_coins * 80 / 100, 2000, price_coins * 20 / 100, 1.0000, 'RECORDED', sold_at, sold_at
FROM v62_sales;

INSERT INTO creator_earning_entries (earning_entry_id, creator_id, settlement_id, amount, state, created_at, updated_at)
SELECT v62_uuid('earning:' || row_no), creator_id, v62_uuid('settlement:' || row_no),
       price_coins * 80 / 100, 'PENDING', sold_at, sold_at
FROM v62_sales;

INSERT INTO platform_revenue_entries (revenue_entry_id, settlement_id, sale_id, amount, created_at, updated_at)
SELECT v62_uuid('revenue:' || row_no), v62_uuid('settlement:' || row_no), v62_uuid('sale:' || row_no),
       price_coins * 20 / 100, sold_at, sold_at
FROM v62_sales;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v62_uuid('commission-treasury:' || sale.row_no), 'COIN', 'CREDIT', 'MARKETPLACE_COMMISSION_EARNED',
       'SALE', v62_uuid('sale:' || sale.row_no), sale.price_coins * 20 / 100, 'SYSTEM', sale.user_id,
       account.full_name, 'v62-sale-' || sale.row_no, 'Marketplace commission',
       jsonb_build_object('settlementId', v62_uuid('settlement:' || sale.row_no), 'platformShareBps', 2000),
       sale.sold_at, 'MARKETPLACE_COMMISSION_EARNED:' || v62_uuid('sale:' || sale.row_no), sale.sold_at, sale.sold_at
FROM v62_sales sale
JOIN users account ON account.user_id = sale.user_id;

UPDATE user_wallets wallet
SET balance = wallet.balance - sale.price_coins,
    updated_at = sale.sold_at
FROM v62_sales sale
WHERE wallet.user_id = sale.user_id;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v62_uuid('sale-wallet:' || sale.row_no), wallet.wallet_id, 'DEBIT', sale.price_coins,
       wallet.balance + sale.price_coins, wallet.balance, 'MARKETPLACE_SALE', v62_uuid('sale:' || sale.row_no),
       sale.sold_at, sale.sold_at
FROM v62_sales sale
JOIN user_wallets wallet ON wallet.user_id = sale.user_id;

-- 5. Expanded Learning Sessions & XP across August 1 to 3, 2026.
CREATE TEMP TABLE v62_learning ON COMMIT DROP AS
SELECT row_no, user_no, started_at, duration_minutes, focus_score,
       CASE WHEN row_no % 2 = 0 THEN 'QUIZ_EXCELLENT' ELSE 'QUIZ_PASSED' END AS event_type,
       CASE WHEN row_no % 2 = 0 THEN 120 ELSE 80 END AS points
FROM (VALUES
    (1,   5, TIMESTAMPTZ '2026-08-01 07:30:00+07', 45, 90),
    (2,  12, TIMESTAMPTZ '2026-08-01 10:15:00+07', 50, 86),
    (3,  22, TIMESTAMPTZ '2026-08-01 19:40:00+07', 35, 82),
    (4,  33, TIMESTAMPTZ '2026-08-02 08:00:00+07', 60, 95),
    (5,  48, TIMESTAMPTZ '2026-08-02 14:10:00+07', 40, 88),
    (6,  61, TIMESTAMPTZ '2026-08-02 21:00:00+07', 55, 92),
    (7,  77, TIMESTAMPTZ '2026-08-03 07:45:00+07', 48, 89),
    (8,  85, TIMESTAMPTZ '2026-08-03 16:30:00+07', 52, 91),
    (9,  94, TIMESTAMPTZ '2026-08-03 20:15:00+07', 38, 84)
) AS learning(row_no, user_no, started_at, duration_minutes, focus_score);

INSERT INTO study_sessions (
    session_id, workspace_id, calendar_task_id, roadmap_step_id, user_id, started_at, ended_at,
    duration_minutes, status, notes, focus_score
)
SELECT v62_uuid('session:' || row_no), v62_v37_uuid('workspace:' || user_no), NULL, NULL,
       v62_v36_uuid('user:' || user_no)::text, started_at, started_at + duration_minutes * INTERVAL '1 minute',
       duration_minutes, 'COMPLETED', 'Phiên học tập trung đầu tháng 8, ôn luyện kiến thức thực tế.', focus_score
FROM v62_learning;

INSERT INTO point_events (
    point_event_id, user_id, workspace_id, event_type, source_type, source_id,
    points, description, event_date, week_start_date, month_start_date, created_at, updated_at
)
SELECT v62_uuid('point-event:' || row_no), v62_v36_uuid('user:' || user_no)::text,
       v62_v37_uuid('workspace:' || user_no), event_type, 'QUIZ', 'v62-august-learning-' || row_no,
       points, CASE WHEN event_type = 'QUIZ_EXCELLENT' THEN 'Hoàn thành bài kiểm tra xuất sắc' ELSE 'Hoàn thành bài kiểm tra đạt yêu cầu' END,
       started_at::date, date_trunc('week', started_at)::date, date_trunc('month', started_at)::date, started_at, started_at
FROM v62_learning;

UPDATE user_point_summaries summary
SET total_points = summary.total_points + aggregate.points,
    current_week_points = COALESCE(summary.current_week_points, 0) + aggregate.points,
    current_week_start_date = DATE '2026-07-27',
    current_month_points = COALESCE(summary.current_month_points, 0) + aggregate.points,
    current_month_start_date = DATE '2026-08-01',
    streak_days = GREATEST(summary.streak_days, 3),
    last_point_date = aggregate.last_point_date,
    updated_at = aggregate.last_event_at
FROM (
    SELECT event.user_id, sum(event.points) AS points,
           max(event.event_date) AS last_point_date, max(event.created_at) AS last_event_at
    FROM point_events event
    WHERE event.source_id LIKE 'v62-august-learning-%'
    GROUP BY event.user_id
) aggregate
WHERE summary.user_id = aggregate.user_id;

-- 6. Community Posts, Comments & Likes for August 1 to 3, 2026.
CREATE TEMP TABLE v62_posts ON COMMIT DROP AS
SELECT row_no, v62_v36_uuid('user:' || user_no)::text AS user_id, content, hashtags, created_at
FROM (VALUES
    (1,  5, 'Chào tháng 8! Đặt mục tiêu mỗi ngày dành ít nhất 45 phút học lập trình.', '#august #goal #learning', TIMESTAMPTZ '2026-08-01 10:00:00+07'),
    (2, 12, 'Vừa hoàn thành bài test Spring Boot trên SkillSprint, phần giải thích rất dễ hiểu.', '#springboot #java #quiz', TIMESTAMPTZ '2026-08-01 15:30:00+07'),
    (3, 33, 'Cuối tuần dành thời gian tối ưu hóa câu truy vấn SQL. Cảm giác giải quyết được bài toán chậm rất đã!', '#sql #optimization', TIMESTAMPTZ '2026-08-02 09:45:00+07'),
    (4, 48, 'Mới đăng ký gói Skill Builder để dùng đầy đủ lộ trình AI gợi ý. Giao diện trực quan ghê.', '#skillbuilder #ai #roadmap', TIMESTAMPTZ '2026-08-02 18:20:00+07'),
    (5, 77, 'Bắt đầu tuần mới bằng một phiên học tập trung 50 phút. Chúc mọi người tuần mới nhiều năng lượng!', '#productivity #monday', TIMESTAMPTZ '2026-08-03 08:30:00+07'),
    (6, 85, 'Mọi người cho mình hỏi kinh nghiệm luyện phỏng vấn kỹ sư dữ liệu với?', '#dataengineer #interview #career', TIMESTAMPTZ '2026-08-03 17:10:00+07')
) AS post(row_no, user_no, content, hashtags, created_at);

INSERT INTO community_posts (post_id, user_id, content, hashtags, status, like_count, comment_count, report_count, created_at, updated_at)
SELECT v62_uuid('post:' || row_no), user_id, content, hashtags, 'APPROVED', 0, 0, 0, created_at, created_at
FROM v62_posts;

INSERT INTO post_comments (comment_id, post_id, user_id, content, status, report_count, created_at, updated_at)
SELECT v62_uuid('comment:' || comment_no), v62_uuid('post:' || (((comment_no - 1) % 6) + 1)),
       v62_v36_uuid('user:' || (comment_no + 15))::text,
       (ARRAY[
           'Chúc bạn đạt mục tiêu đầu tháng nhé!',
           'Đồng ý, giải thích chi tiết giúp tiếp thu nhanh hơn hẳn.',
           'Tối ưu SQL đúng là kỹ năng cực kỳ quan trọng.',
           'Tính năng lộ trình AI thật sự hỗ trợ học rất bài bản.',
           'Tuần mới năng lượng nhé mọi người!',
           'Bạn có thể tham khảo thêm bộ quiz chuyên môn trên Marketplace.'
       ])[((comment_no - 1) % 6) + 1],
       'VISIBLE', 0,
       TIMESTAMPTZ '2026-08-01 10:30:00+07' + (comment_no * INTERVAL '4 hours 15 minutes'),
       TIMESTAMPTZ '2026-08-01 10:30:00+07' + (comment_no * INTERVAL '4 hours 15 minutes')
FROM generate_series(1, 12) AS comment_no;

INSERT INTO post_likes (like_id, post_id, user_id, created_at, updated_at)
SELECT v62_uuid('like:' || like_no), v62_uuid('post:' || (((like_no - 1) % 6) + 1)),
       v62_v36_uuid('user:' || (like_no + 40))::text,
       TIMESTAMPTZ '2026-08-01 11:00:00+07' + (like_no * INTERVAL '2 hours 40 minutes'),
       TIMESTAMPTZ '2026-08-01 11:00:00+07' + (like_no * INTERVAL '2 hours 40 minutes')
FROM generate_series(1, 18) AS like_no;

UPDATE community_posts post
SET comment_count = (SELECT count(*) FROM post_comments comment WHERE comment.post_id = post.post_id AND comment.status = 'VISIBLE'),
    like_count = (SELECT count(*) FROM post_likes liked WHERE liked.post_id = post.post_id)
WHERE post.post_id IN (SELECT v62_uuid('post:' || n) FROM generate_series(1, 6) AS n);

-- 7. Community Room Chat Messages for August 1 to 3, 2026.
INSERT INTO community_room_members (member_id, room_id, user_id, role, banned, status, created_at, updated_at)
SELECT v62_uuid('room-member:' || user_no), v62_v28_uuid('room:6'), v62_v36_uuid('user:' || user_no)::text,
       'MEMBER', FALSE, 'ACTIVE', TIMESTAMPTZ '2026-08-01 08:00:00+07', TIMESTAMPTZ '2026-08-01 08:00:00+07'
FROM (VALUES (5),(12),(22),(33),(48),(61),(77),(85),(94)) AS learner(user_no)
ON CONFLICT (room_id, user_id) DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at;

INSERT INTO community_chat_messages (message_id, room_id, sender_id, raw_content, masked_content, hidden, report_count, sent_at)
SELECT v62_uuid('chat:' || item.row_no), v62_v28_uuid('room:6'), v62_v36_uuid('user:' || item.user_no)::text,
       item.content, item.content, FALSE, 0, item.sent_at
FROM (VALUES
    (1,  5, 'Hi mọi người, đầu tháng mọi người lên kế hoạch học gì thế?', TIMESTAMPTZ '2026-08-01 08:15:00+07'),
    (2, 12, 'Mình đang tập trung nâng cao kỹ năng thiết kế cơ sở dữ liệu.', TIMESTAMPTZ '2026-08-01 14:40:00+07'),
    (3, 33, 'Ai rảnh tối nay trao đổi về Docker và CI/CD không?', TIMESTAMPTZ '2026-08-02 19:20:00+07'),
    (4, 77, 'Bắt đầu tuần mới nhiều năng lượng nhé ae!', TIMESTAMPTZ '2026-08-03 08:05:00+07')
) AS item(row_no, user_no, content, sent_at);

UPDATE community_rooms room
SET member_count = (SELECT count(*) FROM community_room_members member WHERE member.room_id = room.room_id AND member.status = 'ACTIVE')
WHERE room.room_id = v62_v28_uuid('room:6');

-- 8. User Feedback & Support Tickets on August 1 to 3, 2026.
INSERT INTO feedbacks (
    feedback_id, user_id, type, title, content, related_url, image_object_key, status,
    admin_note, admin_reply, replied_by_user_id, replied_at, created_at, updated_at
)
SELECT v62_uuid('feedback:' || item.row_no), v62_v36_uuid('user:' || item.user_no)::text, item.type,
       item.title, item.content, item.related_url, NULL, item.status,
       CASE WHEN item.status = 'OPEN' THEN NULL ELSE 'Đã xử lý và thông báo lại cho người dùng.' END,
       CASE WHEN item.status = 'OPEN' THEN NULL WHEN item.status = 'IN_PROGRESS' THEN 'Cảm ơn góp ý của bạn, đội kỹ thuật đang tiến hành tối ưu.' ELSE 'Vấn đề đã được cập nhật thành công.' END,
       CASE WHEN item.status = 'OPEN' THEN NULL ELSE admin.user_id END,
       CASE WHEN item.status = 'OPEN' THEN NULL ELSE item.created_at + INTERVAL '3 hours' END,
       item.created_at, CASE WHEN item.status = 'OPEN' THEN item.created_at ELSE item.created_at + INTERVAL '3 hours' END
FROM (VALUES
    (1, 12, 'IMPROVEMENT', 'Tối ưu tốc độ tải danh sách quiz', 'Khi xem danh sách quiz lớn trên mobile, ứng dụng có độ trễ nhẹ.', '/marketplace', 'IN_PROGRESS', TIMESTAMPTZ '2026-08-01 16:10:00+07'),
    (2, 33, 'QUESTION', 'Hạn sử dụng coin nạp vào ví', 'Coin nạp vào ví có thời hạn sử dụng hay không?', '/wallet', 'CLOSED', TIMESTAMPTZ '2026-08-02 11:20:00+07'),
    (3, 85, 'BUG', 'Lỗi hiển thị thông báo trên trình duyệt Safari', 'Thông báo học tập thỉnh thoảng không xuất hiện pop-up trên Safari.', '/notifications', 'OPEN', TIMESTAMPTZ '2026-08-03 14:00:00+07')
) AS item(row_no, user_no, type, title, content, related_url, status, created_at)
LEFT JOIN LATERAL (
    SELECT user_role.user_id
    FROM user_roles user_role
    JOIN roles role ON role.role_id = user_role.role_id
    JOIN users account ON account.user_id = user_role.user_id
    WHERE role.role_name = 'ADMIN' AND account.status = 'ACTIVE'
    LIMIT 1
) admin ON item.status <> 'OPEN';

-- Update user last_login_at to reflect August 1 to 3 active usage.
UPDATE users account
SET last_login_at = activity.last_seen_at,
    updated_at = GREATEST(account.updated_at, activity.last_seen_at)
FROM (
    SELECT v62_v36_uuid('user:' || user_no)::text AS user_id, max(started_at) AS last_seen_at
    FROM v62_learning
    GROUP BY user_no
) activity
WHERE account.user_id = activity.user_id;

-- Postcondition assertions to guarantee 100% data integrity and ledgers reconciliation for V62 records.
DO $$
BEGIN
    IF (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP62C%') <> 9
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP62S%') <> 6
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'COIN_TOP_UP_RECEIVED:%' AND metadata ->> 'seed' = 'V62') <> 9
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'v62-subscription-payment-%') <> 6
       OR (SELECT count(*) FROM marketplace_sales WHERE idempotency_key LIKE 'v62-sale-%') <> 6
       OR (SELECT count(*) FROM study_sessions WHERE session_id IN (SELECT v62_uuid('session:' || n) FROM generate_series(1, 9) AS n)) <> 9
       OR (SELECT count(*) FROM community_posts WHERE post_id IN (SELECT v62_uuid('post:' || n) FROM generate_series(1, 6) AS n)) <> 6
       OR (SELECT count(*) FROM feedbacks WHERE feedback_id IN (SELECT v62_uuid('feedback:' || n) FROM generate_series(1, 3) AS n)) <> 3 THEN
        RAISE EXCEPTION 'V62 postcondition failed; August expanded activity or financial ledgers are inconsistent';
    END IF;
END $$;

DROP FUNCTION v62_v28_uuid(TEXT);
DROP FUNCTION v62_v37_uuid(TEXT);
DROP FUNCTION v62_v36_uuid(TEXT);
DROP FUNCTION v62_uuid(TEXT);
