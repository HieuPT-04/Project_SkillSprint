-- Adds a compact, internally reconciled activity window for 01-03 August 2026.
-- It only uses deterministic V36 learners and their V37 workspaces; no real account is
-- created or changed.  Financial entries are recorded through the same payment, wallet,
-- settlement and treasury ledgers used by the application.

CREATE FUNCTION v60_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v60:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v60:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v60:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v60:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v60:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v60_v36_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v36:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v60_v37_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v37:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v37:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v37:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v37:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v37:' || seed), 21, 12)
    )::uuid;
$$;

DO $$
BEGIN
    IF (SELECT count(*) FROM users WHERE user_id IN (
            SELECT v60_v36_uuid('user:' || n)::text FROM generate_series(1, 100) AS n
        )) <> 100
       OR (SELECT count(*) FROM study_workspaces WHERE workspace_id IN (
            SELECT v60_v37_uuid('workspace:' || n) FROM generate_series(1, 100) AS n
        )) <> 100
       OR NOT EXISTS (SELECT 1 FROM service_plans WHERE plan_type = 'FREE')
       OR NOT EXISTS (SELECT 1 FROM service_plans WHERE plan_type = 'SKILL_BUILDER')
       OR NOT EXISTS (SELECT 1 FROM service_plans WHERE plan_type = 'PREMIUM') THEN
        RAISE EXCEPTION 'V60 requires the complete V36 learner cohort, V37 workspaces and standard service plans';
    END IF;

    IF EXISTS (SELECT 1 FROM payment_transactions WHERE txn_ref LIKE 'SP60%')
       OR EXISTS (SELECT 1 FROM community_posts WHERE post_id = v60_uuid('post:1')) THEN
        RAISE EXCEPTION 'V60 August activity already exists; do not apply this migration to a partially seeded database';
    END IF;
END $$;

-- Nine believable top-ups, six of which are followed by a marketplace purchase.
CREATE TEMP TABLE v60_topups ON COMMIT DROP AS
SELECT row_no,
       v60_v36_uuid('user:' || user_no)::text AS user_id,
       coin_amount,
       paid_at
FROM (VALUES
    (1,  4, 100000, TIMESTAMPTZ '2026-08-01 08:42:00+07'),
    (2, 17, 100000, TIMESTAMPTZ '2026-08-01 20:18:00+07'),
    (3, 29, 100000, TIMESTAMPTZ '2026-08-02 07:55:00+07'),
    (4, 46, 100000, TIMESTAMPTZ '2026-08-02 19:36:00+07'),
    (5, 58, 100000, TIMESTAMPTZ '2026-08-03 09:08:00+07'),
    (6, 73, 100000, TIMESTAMPTZ '2026-08-03 21:12:00+07'),
    (7, 82,  50000, TIMESTAMPTZ '2026-08-01 12:24:00+07'),
    (8, 91,  10000, TIMESTAMPTZ '2026-08-02 13:41:00+07'),
    (9, 98,  50000, TIMESTAMPTZ '2026-08-03 17:26:00+07')
) AS topup(row_no, user_no, coin_amount, paid_at);

INSERT INTO payment_transactions (
    payment_id, user_id, plan_id, purpose, coin_amount, coin_package_key, provider, status,
    txn_ref, amount, currency, subscription_months, transfer_content, expire_at, paid_at,
    provider_transaction_id, provider_reference_code, raw_callback_data, created_at, updated_at
)
SELECT v60_uuid('topup-payment:' || row_no), user_id, NULL, 'COIN_TOP_UP', coin_amount,
       'COIN_' || coin_amount, 'SEPAY', 'PAID', 'SP60C' || lpad(row_no::text, 4, '0'),
       coin_amount, 'VND', 0, 'SP60C' || lpad(row_no::text, 4, '0'), paid_at - INTERVAL '12 minutes', paid_at,
       'SP60-SEPAY-C-' || lpad(row_no::text, 4, '0'), 'SP60REF-C-' || lpad(row_no::text, 4, '0'),
       jsonb_build_object('seed', 'V60', 'purpose', 'COIN_TOP_UP', 'coinAmount', coin_amount),
       paid_at - INTERVAL '2 minutes', paid_at
FROM v60_topups;

INSERT INTO user_wallets (wallet_id, user_id, balance, created_at, updated_at)
SELECT v60_uuid('wallet:' || user_id), user_id, coin_amount, paid_at, paid_at
FROM v60_topups
ON CONFLICT (user_id) DO UPDATE
SET balance = user_wallets.balance + EXCLUDED.balance,
    updated_at = EXCLUDED.updated_at;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v60_uuid('topup-wallet:' || topup.row_no), wallet.wallet_id, 'CREDIT', topup.coin_amount,
       wallet.balance - topup.coin_amount, wallet.balance, 'COIN_TOP_UP',
       v60_uuid('topup-payment:' || topup.row_no), topup.paid_at, topup.paid_at
FROM v60_topups topup
JOIN user_wallets wallet ON wallet.user_id = topup.user_id;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v60_uuid('topup-treasury:' || topup.row_no), 'VND', 'CREDIT', 'COIN_TOP_UP_RECEIVED', 'PAYMENT',
       v60_uuid('topup-payment:' || topup.row_no), topup.coin_amount, 'SYSTEM', topup.user_id, account.full_name,
       'SP60C' || lpad(topup.row_no::text, 4, '0'), 'Coin top-up received',
       jsonb_build_object('seed', 'V60', 'packageKey', 'COIN_' || topup.coin_amount), topup.paid_at,
       'COIN_TOP_UP_RECEIVED:' || v60_uuid('topup-payment:' || topup.row_no), topup.paid_at, topup.paid_at
FROM v60_topups topup
JOIN users account ON account.user_id = topup.user_id;

-- Conversion activity: users genuinely leave the free plan before a paid plan begins.
CREATE TEMP TABLE v60_subscription_purchases ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY subscription.user_id) AS row_no,
       subscription.subscription_id AS previous_subscription_id,
       subscription.user_id,
       CASE WHEN row_number() OVER (ORDER BY subscription.user_id) <= 4 THEN 'SKILL_BUILDER' ELSE 'PREMIUM' END AS plan_type,
       CASE WHEN row_number() OVER (ORDER BY subscription.user_id) <= 4 THEN 89000 ELSE 199000 END AS amount,
       (ARRAY[
           TIMESTAMPTZ '2026-08-01 10:05:00+07', TIMESTAMPTZ '2026-08-01 18:47:00+07',
           TIMESTAMPTZ '2026-08-02 09:16:00+07', TIMESTAMPTZ '2026-08-02 20:24:00+07',
           TIMESTAMPTZ '2026-08-03 08:51:00+07', TIMESTAMPTZ '2026-08-03 19:08:00+07'
       ])[row_number() OVER (ORDER BY subscription.user_id)] AS paid_at
FROM subscriptions subscription
JOIN service_plans plan ON plan.plan_id = subscription.plan_id AND plan.plan_type = 'FREE'
WHERE subscription.status = 'ACTIVE'
  AND subscription.user_id IN (SELECT v60_v36_uuid('user:' || n)::text FROM generate_series(1, 100) AS n)
ORDER BY subscription.user_id
LIMIT 6;

DO $$
BEGIN
    IF (SELECT count(*) FROM v60_subscription_purchases) <> 6 THEN
        RAISE EXCEPTION 'V60 requires at least six active FREE subscriptions in the V36 cohort';
    END IF;
END $$;

UPDATE subscriptions subscription
SET status = 'CANCELED', end_date = purchase.paid_at::date,
    end_at = purchase.paid_at
FROM v60_subscription_purchases purchase
WHERE subscription.subscription_id = purchase.previous_subscription_id;

INSERT INTO payment_transactions (
    payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
    subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
    provider_reference_code, raw_callback_data, created_at, updated_at
)
SELECT v60_uuid('subscription-payment:' || purchase.row_no), purchase.user_id, plan.plan_id,
       'SUBSCRIPTION', 'SEPAY', 'PAID', 'SP60S' || lpad(purchase.row_no::text, 4, '0'), purchase.amount, 'VND', 1,
       'SP60S' || lpad(purchase.row_no::text, 4, '0'), purchase.paid_at - INTERVAL '15 minutes', purchase.paid_at,
       'SP60-SEPAY-S-' || lpad(purchase.row_no::text, 4, '0'), 'SP60REF-S-' || lpad(purchase.row_no::text, 4, '0'),
       jsonb_build_object('seed', 'V60', 'purpose', 'SUBSCRIPTION', 'planType', purchase.plan_type),
       purchase.paid_at - INTERVAL '3 minutes', purchase.paid_at
FROM v60_subscription_purchases purchase
JOIN service_plans plan ON plan.plan_type = purchase.plan_type;

INSERT INTO subscriptions (subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at)
SELECT v60_uuid('subscription:' || purchase.row_no), purchase.user_id, plan.plan_id,
       purchase.paid_at::date, (purchase.paid_at + INTERVAL '1 month')::date, purchase.paid_at,
       purchase.paid_at + INTERVAL '1 month', 'ACTIVE', purchase.paid_at
FROM v60_subscription_purchases purchase
JOIN service_plans plan ON plan.plan_type = purchase.plan_type;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
    idempotency_key, created_at, updated_at
)
SELECT v60_uuid('subscription-treasury:' || purchase.row_no), 'VND', 'CREDIT', 'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT',
       v60_uuid('subscription-payment:' || purchase.row_no), purchase.amount, purchase.user_id, account.full_name,
       'SP60S' || lpad(purchase.row_no::text, 4, '0'), 'Subscription payment received',
       jsonb_build_object('seed', 'V60', 'planType', purchase.plan_type), purchase.paid_at,
       'v60-subscription-payment-' || purchase.row_no, purchase.paid_at, purchase.paid_at
FROM v60_subscription_purchases purchase
JOIN users account ON account.user_id = purchase.user_id;

-- Select saleable packs the learner does not already own, preventing duplicate entitlements.
CREATE TEMP TABLE v60_sales ON COMMIT DROP AS
SELECT topup.row_no, topup.user_id, candidate.pack_id, candidate.version_id, candidate.creator_id,
       candidate.price_coins, topup.paid_at + INTERVAL '28 minutes' AS sold_at
FROM v60_topups topup
CROSS JOIN LATERAL (
    SELECT version.pack_id, version.version_id, pack.creator_id, version.price_coins
    FROM marketplace_pack_versions version
    JOIN marketplace_packs pack ON pack.pack_id = version.pack_id
    WHERE version.status = 'PUBLISHED'
      AND version.saleable = TRUE
      AND version.price_coins BETWEEN 1 AND 100000
      AND pack.creator_id <> topup.user_id
      AND NOT EXISTS (
          SELECT 1 FROM marketplace_entitlements entitlement
          WHERE entitlement.buyer_id = topup.user_id
            AND entitlement.pack_version_id = version.version_id
            AND entitlement.status = 'ACTIVE'
      )
    ORDER BY version.price_coins, version.published_at, version.version_id
    LIMIT 1
) candidate
WHERE topup.row_no <= 6;

DO $$
BEGIN
    IF (SELECT count(*) FROM v60_sales) <> 6
       OR EXISTS (
           SELECT 1 FROM v60_sales sale JOIN user_wallets wallet ON wallet.user_id = sale.user_id
           WHERE wallet.balance < sale.price_coins
       ) THEN
        RAISE EXCEPTION 'V60 marketplace sales cannot be funded from eligible learner wallets';
    END IF;
END $$;

INSERT INTO marketplace_sales (
    sale_id, buyer_id, pack_id, pack_version_id, source_entitlement_id, gross_coin_amount,
    original_gross_coin_amount, discount_coin_amount, gross_vnd_amount, coin_to_vnd_rate,
    status, idempotency_key, created_at, updated_at
)
SELECT v60_uuid('sale:' || row_no), user_id, pack_id, version_id, NULL, price_coins, price_coins, 0,
       price_coins, 1.0000, 'COMPLETED', 'v60-sale-' || row_no, sold_at, sold_at
FROM v60_sales;

INSERT INTO marketplace_entitlements (entitlement_id, buyer_id, pack_version_id, source_sale_id, status, granted_at, created_at, updated_at)
SELECT v60_uuid('entitlement:' || row_no), user_id, version_id, v60_uuid('sale:' || row_no),
       'ACTIVE', sold_at, sold_at, sold_at
FROM v60_sales;

INSERT INTO marketplace_sale_settlements (
    settlement_id, sale_id, creator_id, creator_share_bps, creator_amount, platform_share_bps,
    platform_amount, coin_to_vnd_rate, status, created_at, updated_at
)
SELECT v60_uuid('settlement:' || row_no), v60_uuid('sale:' || row_no), creator_id,
       8000, price_coins * 80 / 100, 2000, price_coins * 20 / 100, 1.0000, 'RECORDED', sold_at, sold_at
FROM v60_sales;

INSERT INTO creator_earning_entries (earning_entry_id, creator_id, settlement_id, amount, state, created_at, updated_at)
SELECT v60_uuid('earning:' || row_no), creator_id, v60_uuid('settlement:' || row_no),
       price_coins * 80 / 100, 'PENDING', sold_at, sold_at
FROM v60_sales;

INSERT INTO platform_revenue_entries (revenue_entry_id, settlement_id, sale_id, amount, created_at, updated_at)
SELECT v60_uuid('revenue:' || row_no), v60_uuid('settlement:' || row_no), v60_uuid('sale:' || row_no),
       price_coins * 20 / 100, sold_at, sold_at
FROM v60_sales;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v60_uuid('commission-treasury:' || sale.row_no), 'COIN', 'CREDIT', 'MARKETPLACE_COMMISSION_EARNED',
       'SALE', v60_uuid('sale:' || sale.row_no), sale.price_coins * 20 / 100, 'SYSTEM', sale.user_id,
       account.full_name, 'v60-sale-' || sale.row_no, 'Marketplace commission',
       jsonb_build_object('settlementId', v60_uuid('settlement:' || sale.row_no), 'platformShareBps', 2000),
       sale.sold_at, 'MARKETPLACE_COMMISSION_EARNED:' || v60_uuid('sale:' || sale.row_no), sale.sold_at, sale.sold_at
FROM v60_sales sale
JOIN users account ON account.user_id = sale.user_id;

UPDATE user_wallets wallet
SET balance = wallet.balance - sale.price_coins,
    updated_at = sale.sold_at
FROM v60_sales sale
WHERE wallet.user_id = sale.user_id;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v60_uuid('sale-wallet:' || sale.row_no), wallet.wallet_id, 'DEBIT', sale.price_coins,
       wallet.balance + sale.price_coins, wallet.balance, 'MARKETPLACE_SALE', v60_uuid('sale:' || sale.row_no),
       sale.sold_at, sale.sold_at
FROM v60_sales sale
JOIN user_wallets wallet ON wallet.user_id = sale.user_id;

-- Study sessions and XP are spread across all three dates, with realistic duration and focus variation.
CREATE TEMP TABLE v60_learning ON COMMIT DROP AS
SELECT row_no, user_no, started_at, duration_minutes, focus_score,
       CASE WHEN row_no % 3 = 0 THEN 'QUIZ_EXCELLENT' ELSE 'QUIZ_PASSED' END AS event_type,
       CASE WHEN row_no % 3 = 0 THEN 120 ELSE 80 END AS points
FROM (VALUES
    (1,  4, TIMESTAMPTZ '2026-08-01 06:50:00+07', 42, 88),
    (2, 17, TIMESTAMPTZ '2026-08-01 08:10:00+07', 35, 81),
    (3, 29, TIMESTAMPTZ '2026-08-01 20:05:00+07', 58, 94),
    (4, 46, TIMESTAMPTZ '2026-08-01 21:22:00+07', 29, 76),
    (5,  4, TIMESTAMPTZ '2026-08-02 07:15:00+07', 50, 91),
    (6, 17, TIMESTAMPTZ '2026-08-02 12:35:00+07', 40, 85),
    (7, 29, TIMESTAMPTZ '2026-08-02 19:40:00+07', 67, 96),
    (8, 46, TIMESTAMPTZ '2026-08-02 22:05:00+07', 32, 79),
    (9,  4, TIMESTAMPTZ '2026-08-03 06:55:00+07', 45, 90),
    (10,17, TIMESTAMPTZ '2026-08-03 08:25:00+07', 38, 84),
    (11,29, TIMESTAMPTZ '2026-08-03 18:55:00+07', 62, 93),
    (12,46, TIMESTAMPTZ '2026-08-03 21:10:00+07', 34, 82)
) AS learning(row_no, user_no, started_at, duration_minutes, focus_score);

INSERT INTO study_sessions (
    session_id, workspace_id, calendar_task_id, roadmap_step_id, user_id, started_at, ended_at,
    duration_minutes, status, notes, focus_score
)
SELECT v60_uuid('session:' || row_no), v60_v37_uuid('workspace:' || user_no), NULL, NULL,
       v60_v36_uuid('user:' || user_no)::text, started_at, started_at + duration_minutes * INTERVAL '1 minute',
       duration_minutes, 'COMPLETED', 'Phiên tự học tập trung, có ghi chú ôn lại phần còn vướng.', focus_score
FROM v60_learning;

INSERT INTO point_events (
    point_event_id, user_id, workspace_id, event_type, source_type, source_id,
    points, description, event_date, week_start_date, month_start_date, created_at, updated_at
)
SELECT v60_uuid('point-event:' || row_no), v60_v36_uuid('user:' || user_no)::text,
       v60_v37_uuid('workspace:' || user_no), event_type, 'QUIZ', 'v60-august-learning-' || row_no,
       points, CASE WHEN event_type = 'QUIZ_EXCELLENT' THEN 'Hoàn thành quiz với kết quả xuất sắc' ELSE 'Hoàn thành quiz đạt yêu cầu' END,
       started_at::date, date_trunc('week', started_at)::date, date_trunc('month', started_at)::date, started_at, started_at
FROM v60_learning;

UPDATE user_point_summaries summary
SET total_points = aggregate.total_points,
    current_week_points = aggregate.week_points,
    current_week_start_date = DATE '2026-07-27',
    current_month_points = aggregate.month_points,
    current_month_start_date = DATE '2026-08-01',
    streak_days = GREATEST(summary.streak_days, 3),
    last_point_date = aggregate.last_point_date,
    updated_at = aggregate.last_event_at
FROM (
    SELECT event.user_id, sum(event.points) AS total_points,
           sum(event.points) FILTER (WHERE event.week_start_date = DATE '2026-07-27') AS week_points,
           sum(event.points) FILTER (WHERE event.month_start_date = DATE '2026-08-01') AS month_points,
           max(event.event_date) AS last_point_date, max(event.created_at) AS last_event_at
    FROM point_events event
    WHERE event.user_id IN (SELECT v60_v36_uuid('user:' || n)::text FROM (VALUES (4), (17), (29), (46)) AS learner(n))
    GROUP BY event.user_id
) aggregate
WHERE summary.user_id = aggregate.user_id;

-- Community activity has independent authors, commenters and likers; counters are recalculated from rows.
CREATE TEMP TABLE v60_posts ON COMMIT DROP AS
SELECT row_no, v60_v36_uuid('user:' || user_no)::text AS user_id, content, hashtags, created_at
FROM (VALUES
    (1,  4, 'Mình vừa chia lịch ôn thành các phiên ngắn và thấy dễ bắt đầu hơn hẳn. Ai đang học backend có mẹo giữ nhịp buổi tối không?', '#backend #studyhabit', TIMESTAMPTZ '2026-08-01 09:20:00+07'),
    (2, 17, 'Hoàn thành quiz đầu tháng, phần giải thích đáp án giúp mình nhận ra vài chỗ SQL JOIN còn nhầm. Tối nay ôn lại tiếp.', '#sql #quiz', TIMESTAMPTZ '2026-08-01 20:42:00+07'),
    (3, 29, 'Cuối tuần này mình thử Pomodoro 50/10 thay vì 25/5. Với bài cần đọc kỹ tài liệu thì nhịp này hợp hơn.', '#pomodoro #productivity', TIMESTAMPTZ '2026-08-02 10:05:00+07'),
    (4, 46, 'Mình đang làm roadmap React, tới phần state management thì hơi chậm. Có bạn nào có tài liệu nhập môn dễ theo dõi không?', '#reactjs #help', TIMESTAMPTZ '2026-08-02 21:10:00+07'),
    (5, 58, 'Mới mua một bộ quiz để ôn lại Java Core. Nội dung chia theo chương nên tiện cho những buổi học ngắn sau giờ làm.', '#java #marketplace', TIMESTAMPTZ '2026-08-03 11:18:00+07'),
    (6, 73, 'Mục tiêu của mình trong tháng 8 là duy trì ít nhất một phiên học mỗi ngày, không cần quá dài nhưng đều.', '#streak #motivation', TIMESTAMPTZ '2026-08-03 21:35:00+07')
) AS post(row_no, user_no, content, hashtags, created_at);

INSERT INTO community_posts (post_id, user_id, content, hashtags, status, like_count, comment_count, report_count, created_at, updated_at)
SELECT v60_uuid('post:' || row_no), user_id, content, hashtags, 'APPROVED', 0, 0, 0, created_at, created_at
FROM v60_posts;

INSERT INTO post_comments (comment_id, post_id, user_id, content, status, report_count, created_at, updated_at)
SELECT v60_uuid('comment:' || comment_no), v60_uuid('post:' || (((comment_no - 1) % 6) + 1)),
       v60_v36_uuid('user:' || (comment_no + 10))::text,
       (ARRAY['Mình cũng đang thử cách này, chia phiên ngắn giúp đỡ ngại bắt đầu hơn.', 'Phần giải thích sau quiz đúng là hữu ích, mình thường ghi lại lỗi sai để xem lại.', 'Cảm ơn bạn chia sẻ, mình sẽ thử đổi nhịp Pomodoro vào cuối tuần.', 'Bạn có thể bắt đầu từ ví dụ nhỏ rồi mới ghép vào project, sẽ dễ hình dung hơn.', 'Mình cũng thích dạng nội dung theo chương vì có thể quay lại đúng phần cần ôn.', 'Cố lên nhé, duy trì đều vài ngày là thành thói quen.'])[((comment_no - 1) % 6) + 1],
       'VISIBLE', 0,
       TIMESTAMPTZ '2026-08-01 10:00:00+07' + (comment_no * INTERVAL '5 hours 10 minutes'),
       TIMESTAMPTZ '2026-08-01 10:00:00+07' + (comment_no * INTERVAL '5 hours 10 minutes')
FROM generate_series(1, 12) AS comment_no;

INSERT INTO post_likes (like_id, post_id, user_id, created_at, updated_at)
SELECT v60_uuid('like:' || like_no), v60_uuid('post:' || (((like_no - 1) % 6) + 1)),
       v60_v36_uuid('user:' || (like_no + 30))::text,
       TIMESTAMPTZ '2026-08-01 10:30:00+07' + (like_no * INTERVAL '3 hours 25 minutes'),
       TIMESTAMPTZ '2026-08-01 10:30:00+07' + (like_no * INTERVAL '3 hours 25 minutes')
FROM generate_series(1, 18) AS like_no;

UPDATE community_posts post
SET comment_count = (SELECT count(*) FROM post_comments comment WHERE comment.post_id = post.post_id AND comment.status = 'VISIBLE'),
    like_count = (SELECT count(*) FROM post_likes liked WHERE liked.post_id = post.post_id)
WHERE post.post_id IN (SELECT v60_uuid('post:' || n) FROM generate_series(1, 6) AS n);

-- A small operational feedback queue: two new, two being investigated and two resolved reports.
INSERT INTO feedbacks (
    feedback_id, user_id, type, title, content, related_url, image_object_key, status,
    admin_note, admin_reply, replied_by_user_id, replied_at, created_at, updated_at
)
SELECT v60_uuid('feedback:' || item.row_no), v60_v36_uuid('user:' || item.user_no)::text, item.type,
       item.title, item.content, item.related_url, NULL, item.status,
       CASE WHEN item.status = 'OPEN' THEN NULL ELSE 'Đã ghi nhận và chuyển nhóm phụ trách kiểm tra.' END,
       CASE WHEN item.status = 'OPEN' THEN NULL WHEN item.status = 'IN_PROGRESS' THEN 'Cảm ơn bạn đã mô tả chi tiết. Đội ngũ đang kiểm tra và sẽ cập nhật lại sớm.' ELSE 'Cảm ơn bạn đã phản hồi. Vấn đề đã được xử lý trong đợt cập nhật gần nhất.' END,
       CASE WHEN item.status = 'OPEN' THEN NULL ELSE admin.user_id END,
       CASE WHEN item.status = 'OPEN' THEN NULL ELSE item.created_at + INTERVAL '4 hours' END,
       item.created_at, CASE WHEN item.status = 'OPEN' THEN item.created_at ELSE item.created_at + INTERVAL '4 hours' END
FROM (VALUES
    (1, 82, 'BUG', 'Biểu đồ thời gian học chưa cập nhật ngay', 'Mình vừa hoàn thành một phiên học nhưng biểu đồ tổng quan chưa ghi nhận trong vài phút đầu.', '/dashboard', 'OPEN', TIMESTAMPTZ '2026-08-01 11:42:00+07'),
    (2, 91, 'IMPROVEMENT', 'Đề xuất lưu bộ lọc marketplace', 'Nếu bộ lọc môn học và mức giá được lưu lại thì lần sau tìm bộ quiz sẽ nhanh hơn.', '/marketplace', 'OPEN', TIMESTAMPTZ '2026-08-02 14:12:00+07'),
    (3, 98, 'QUESTION', 'Cách tính XP khi làm quiz xuất sắc', 'Mình muốn xác nhận mốc điểm nào được tính là kết quả xuất sắc để theo dõi XP chính xác.', '/leaderboard', 'IN_PROGRESS', TIMESTAMPTZ '2026-08-02 20:35:00+07'),
    (4, 58, 'BUG', 'Nút quay lại sau khi nộp quiz trên mobile', 'Trên màn hình nhỏ, sau khi nộp quiz mình không thấy nút quay về lộ trình.', '/quizzes/result', 'IN_PROGRESS', TIMESTAMPTZ '2026-08-03 09:45:00+07'),
    (5, 73, 'IMPROVEMENT', 'Thêm chế độ tối cho trang học', 'Buổi tối giao diện sáng khá chói, mình mong có thêm Dark Mode cho workspace.', '/study', 'CLOSED', TIMESTAMPTZ '2026-08-03 16:20:00+07'),
    (6, 46, 'OTHER', 'Góp ý về phần giải thích quiz', 'Phần giải thích đáp án rất hữu ích, mong tiếp tục bổ sung các ví dụ thực tế.', '/marketplace', 'CLOSED', TIMESTAMPTZ '2026-08-03 22:08:00+07')
) AS item(row_no, user_no, type, title, content, related_url, status, created_at)
LEFT JOIN LATERAL (
    SELECT user_role.user_id
    FROM user_roles user_role
    JOIN roles role ON role.role_id = user_role.role_id
    JOIN users account ON account.user_id = user_role.user_id
    WHERE role.role_name = 'ADMIN' AND account.status = 'ACTIVE'
    ORDER BY user_role.granted_at NULLS LAST, user_role.user_id
    LIMIT 1
) admin ON item.status <> 'OPEN';

UPDATE users account
SET last_login_at = activity.last_seen_at,
    updated_at = GREATEST(account.updated_at, activity.last_seen_at)
FROM (
    SELECT v60_v36_uuid('user:' || user_no)::text AS user_id, max(started_at) AS last_seen_at
    FROM v60_learning
    GROUP BY user_no
) activity
WHERE account.user_id = activity.user_id;

DO $$
BEGIN
    IF (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP60C%') <> 9
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP60S%') <> 6
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'COIN_TOP_UP_RECEIVED:%' AND metadata ->> 'seed' = 'V60') <> 9
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'v60-subscription-payment-%') <> 6
       OR (SELECT count(*) FROM marketplace_sales WHERE idempotency_key LIKE 'v60-sale-%') <> 6
       OR (SELECT count(*) FROM marketplace_entitlements WHERE source_sale_id IN (SELECT v60_uuid('sale:' || n) FROM generate_series(1, 6) AS n)) <> 6
       OR (SELECT count(*) FROM marketplace_sale_settlements WHERE sale_id IN (SELECT v60_uuid('sale:' || n) FROM generate_series(1, 6) AS n)
           AND creator_amount + platform_amount = (SELECT gross_coin_amount FROM marketplace_sales sale WHERE sale.sale_id = marketplace_sale_settlements.sale_id)) <> 6
       OR (SELECT count(*) FROM wallet_transactions WHERE reference_id IN (SELECT v60_uuid('sale:' || n) FROM generate_series(1, 6) AS n) AND direction = 'DEBIT') <> 6
       OR (SELECT count(*) FROM study_sessions WHERE session_id IN (SELECT v60_uuid('session:' || n) FROM generate_series(1, 12) AS n) AND status = 'COMPLETED') <> 12
       OR (SELECT count(*) FROM point_events WHERE source_id LIKE 'v60-august-learning-%') <> 12
       OR (SELECT count(*) FROM community_posts WHERE post_id IN (SELECT v60_uuid('post:' || n) FROM generate_series(1, 6) AS n)) <> 6
       OR (SELECT count(*) FROM post_comments WHERE comment_id IN (SELECT v60_uuid('comment:' || n) FROM generate_series(1, 12) AS n)) <> 12
       OR (SELECT count(*) FROM post_likes WHERE like_id IN (SELECT v60_uuid('like:' || n) FROM generate_series(1, 18) AS n)) <> 18
       OR (SELECT count(*) FROM feedbacks WHERE feedback_id IN (SELECT v60_uuid('feedback:' || n) FROM generate_series(1, 6) AS n)) <> 6 THEN
        RAISE EXCEPTION 'V60 postcondition failed; August activity or financial ledgers are inconsistent';
    END IF;
END $$;

DROP FUNCTION v60_v37_uuid(TEXT);
DROP FUNCTION v60_v36_uuid(TEXT);
DROP FUNCTION v60_uuid(TEXT);
