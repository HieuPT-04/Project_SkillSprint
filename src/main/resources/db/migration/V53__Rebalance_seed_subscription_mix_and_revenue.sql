-- Rebalance only the identifiable V27/V28/V36 seed cohorts.  The V27 scope
-- uses its migration-only transaction prefix; V28 and V36 use deterministic
-- ids, so real accounts and their payment history are never selected.
--
-- The target is calculated from the active seed users actually present in the
-- deployment: about 30% paid, with a 60/40 Builder/Premium paid split.  The
-- corresponding seed subscription history is rebuilt to 20.018M VND.

CREATE FUNCTION v53_seed_uuid(seed TEXT)
RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (
        substr(md5('skillsprint-v53:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v53:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v53:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v53:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v53:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v53_v28_uuid(seed TEXT)
RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (
        substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v53_v36_uuid(seed TEXT)
RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (
        substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v36:' || seed), 21, 12)
    )::uuid;
$$;

DO $$
DECLARE
    v_free_plan UUID;
    v_builder_plan UUID;
    v_premium_plan UUID;
    v_total_users INTEGER;
    v_paid_users INTEGER;
    v_builder_users INTEGER;
    v_premium_users INTEGER;
    v_direct_revenue_thousand INTEGER;
    v_candidate_revenue_thousand INTEGER;
    v_remaining_thousand INTEGER;
    v_history_builder INTEGER := NULL;
    v_history_premium INTEGER := NULL;
    v_best_delta INTEGER := 999999;
    v_premium_candidate INTEGER;
    v_builder_candidate INTEGER;
BEGIN
    SELECT plan_id INTO v_free_plan FROM service_plans WHERE plan_type = 'FREE' LIMIT 1;
    SELECT plan_id INTO v_builder_plan FROM service_plans WHERE plan_type = 'SKILL_BUILDER' LIMIT 1;
    SELECT plan_id INTO v_premium_plan FROM service_plans WHERE plan_type = 'PREMIUM' LIMIT 1;

    IF v_free_plan IS NULL OR v_builder_plan IS NULL OR v_premium_plan IS NULL THEN
        RAISE EXCEPTION 'V53 requires FREE, SKILL_BUILDER and PREMIUM service plans';
    END IF;

    CREATE TEMP TABLE v53_seed_payment_ids ON COMMIT DROP AS
    SELECT payment_id
    FROM payment_transactions
    WHERE purpose = 'SUBSCRIPTION'
      AND (
          txn_ref LIKE 'DH178504436%'
          OR txn_ref LIKE 'V28SUB%'
          OR txn_ref LIKE 'SP36S%'
          OR txn_ref LIKE 'SP36H%'
      );

    CREATE TEMP TABLE v53_seed_active_subscriptions ON COMMIT DROP AS
    WITH candidate_users AS (
        SELECT DISTINCT payment.user_id
        FROM payment_transactions payment
        WHERE payment.purpose = 'SUBSCRIPTION'
          AND payment.txn_ref LIKE 'DH178504436%'

        UNION

        SELECT v53_v28_uuid('user:' || n)::text
        FROM generate_series(1, 184) AS seed(n)

        UNION

        SELECT v53_v36_uuid('user:' || n)::text
        FROM generate_series(1, 100) AS seed(n)
    )
    SELECT candidate.user_id, active_subscription.subscription_id
    FROM candidate_users candidate
    JOIN LATERAL (
        SELECT subscription.subscription_id
        FROM subscriptions subscription
        WHERE subscription.user_id = candidate.user_id
          AND subscription.status = 'ACTIVE'
        ORDER BY subscription.created_at DESC, subscription.subscription_id
        LIMIT 1
    ) active_subscription ON TRUE;

    SELECT count(*) INTO v_total_users FROM v53_seed_active_subscriptions;
    IF v_total_users < 100 THEN
        RAISE EXCEPTION 'V53 expected at least 100 active seed subscriptions, found %', v_total_users;
    END IF;

    v_paid_users := CEIL(v_total_users * 0.30)::integer;
    v_builder_users := CEIL(v_paid_users * 0.60)::integer;
    v_premium_users := v_paid_users - v_builder_users;
    v_direct_revenue_thousand := (v_builder_users * 89) + (v_premium_users * 199);

    -- Find whole monthly renewal counts nearest to 20.018M.  The cap of two
    -- historical renewals per paid account keeps the generated timeline real.
    FOR v_candidate_revenue_thousand IN 19800..20250 LOOP
        v_remaining_thousand := v_candidate_revenue_thousand - v_direct_revenue_thousand;
        IF v_remaining_thousand >= 0 THEN
            FOR v_premium_candidate IN 0..(v_remaining_thousand / 199) LOOP
                IF (v_remaining_thousand - (v_premium_candidate * 199)) % 89 = 0 THEN
                    v_builder_candidate := (v_remaining_thousand - (v_premium_candidate * 199)) / 89;
                    IF v_builder_candidate <= v_builder_users * 2
                       AND v_premium_candidate <= v_premium_users * 2
                       AND abs(v_candidate_revenue_thousand - 20018) < v_best_delta THEN
                        v_history_builder := v_builder_candidate;
                        v_history_premium := v_premium_candidate;
                        v_best_delta := abs(v_candidate_revenue_thousand - 20018);
                    END IF;
                END IF;
            END LOOP;
        END IF;
    END LOOP;

    IF v_history_builder IS NULL OR v_history_premium IS NULL THEN
        RAISE EXCEPTION 'V53 could not derive a realistic subscription renewal history';
    END IF;

    UPDATE subscriptions subscription
    SET status = 'CANCELED',
        end_at = COALESCE(subscription.end_at, TIMESTAMPTZ '2026-07-01 00:00:00+07'),
        end_date = COALESCE(subscription.end_date, DATE '2026-07-01')
    FROM v53_seed_active_subscriptions target
    WHERE subscription.user_id = target.user_id
      AND subscription.subscription_id <> target.subscription_id
      AND subscription.status = 'ACTIVE';

    CREATE TEMP TABLE v53_plan_targets ON COMMIT DROP AS
    WITH ranked AS (
        SELECT
            target.user_id,
            target.subscription_id,
            row_number() OVER (ORDER BY md5(target.user_id::text))::integer AS ordinal
        FROM v53_seed_active_subscriptions target
    ), classified AS (
        SELECT
            ranked.*,
            CASE
                WHEN ordinal <= v_builder_users THEN 'SKILL_BUILDER'
                WHEN ordinal <= v_paid_users THEN 'PREMIUM'
                ELSE 'FREE'
            END AS plan_type
        FROM ranked
    )
    SELECT
        classified.*,
        CASE classified.plan_type
            WHEN 'SKILL_BUILDER' THEN v_builder_plan
            WHEN 'PREMIUM' THEN v_premium_plan
            ELSE v_free_plan
        END AS plan_id,
        CASE
            WHEN classified.plan_type = 'FREE' THEN
                TIMESTAMPTZ '2026-04-01 08:10:00+07'
                    + ((classified.ordinal * 17) % 96) * INTERVAL '1 day'
                    + ((classified.ordinal * 23) % 540) * INTERVAL '1 minute'
            ELSE
                TIMESTAMPTZ '2026-07-01 08:10:00+07'
                    + ((classified.ordinal * 19) % 22) * INTERVAL '1 day'
                    + ((classified.ordinal * 37) % 540) * INTERVAL '1 minute'
        END AS current_start_at
    FROM classified;

    UPDATE subscriptions subscription
    SET plan_id = target.plan_id,
        start_at = target.current_start_at,
        start_date = (target.current_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
        end_at = CASE
            WHEN target.plan_type = 'FREE' THEN NULL
            ELSE target.current_start_at + INTERVAL '1 month'
        END,
        end_date = CASE
            WHEN target.plan_type = 'FREE' THEN NULL
            ELSE ((target.current_start_at + INTERVAL '1 month') AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
        END,
        status = 'ACTIVE'
    FROM v53_plan_targets target
    WHERE subscription.subscription_id = target.subscription_id;

    -- These V36 expired rows belong to payment history that is rebuilt below.
    DELETE FROM subscriptions subscription
    WHERE subscription.subscription_id IN (
        SELECT v53_v36_uuid('history-subscription:' || n)
        FROM generate_series(1, 18) AS seed(n)
    );

    DELETE FROM platform_treasury_entries treasury
    USING v53_seed_payment_ids payment
    WHERE treasury.reference_type = 'PAYMENT'
      AND treasury.reference_id = payment.payment_id;

    DELETE FROM payment_transactions payment
    USING v53_seed_payment_ids seed_payment
    WHERE payment.payment_id = seed_payment.payment_id;

    INSERT INTO payment_transactions (
        payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
        subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
        provider_reference_code, raw_callback_data, created_at, updated_at
    )
    SELECT
        v53_seed_uuid('current-payment:' || target.ordinal), target.user_id, target.plan_id,
        'SUBSCRIPTION', 'SEPAY', 'PAID', 'SP53S' || lpad(target.ordinal::text, 4, '0'),
        CASE target.plan_type WHEN 'SKILL_BUILDER' THEN 89000.00 ELSE 199000.00 END, 'VND', 1,
        'SP53S' || lpad(target.ordinal::text, 4, '0'), target.current_start_at - INTERVAL '12 minutes',
        target.current_start_at, 'SP53-CURRENT-TXN-' || lpad(target.ordinal::text, 4, '0'),
        'SP53-CURRENT-REF-' || lpad(target.ordinal::text, 4, '0'),
        jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'verified', true),
        target.current_start_at - INTERVAL '12 minutes', target.current_start_at
    FROM v53_plan_targets target
    WHERE target.plan_type <> 'FREE';

    INSERT INTO platform_treasury_entries (
        treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
        actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
        idempotency_key, created_at, updated_at
    )
    SELECT
        v53_seed_uuid('current-treasury:' || target.ordinal), 'VND', 'CREDIT',
        'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT', v53_seed_uuid('current-payment:' || target.ordinal),
        CASE target.plan_type WHEN 'SKILL_BUILDER' THEN 89000.00 ELSE 199000.00 END,
        target.user_id, users.full_name, 'SP53S' || lpad(target.ordinal::text, 4, '0'),
        'Current subscription package payment',
        jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'billingCycle', 'CURRENT'),
        target.current_start_at, 'v53-subscription-current-payment-' || target.ordinal,
        target.current_start_at, target.current_start_at
    FROM v53_plan_targets target
    JOIN users ON users.user_id = target.user_id
    WHERE target.plan_type <> 'FREE';

    CREATE TEMP TABLE v53_history_targets ON COMMIT DROP AS
    SELECT
        target.user_id,
        target.plan_id,
        target.plan_type,
        target.current_start_at,
        target.current_start_at
            - make_interval(months => CEIL(seed.renewal_no::numeric / v_builder_users)::integer) AS paid_at,
        seed.renewal_no::integer AS ordinal
    FROM generate_series(1, v_history_builder) AS seed(renewal_no)
    JOIN LATERAL (
        SELECT candidate.*
        FROM v53_plan_targets candidate
        WHERE candidate.plan_type = 'SKILL_BUILDER'
        ORDER BY candidate.ordinal
        OFFSET ((seed.renewal_no - 1) % v_builder_users)
        LIMIT 1
    ) target ON TRUE

    UNION ALL

    SELECT
        target.user_id,
        target.plan_id,
        target.plan_type,
        target.current_start_at,
        target.current_start_at
            - make_interval(months => CEIL(seed.renewal_no::numeric / v_premium_users)::integer) AS paid_at,
        (v_history_builder + seed.renewal_no)::integer AS ordinal
    FROM generate_series(1, v_history_premium) AS seed(renewal_no)
    JOIN LATERAL (
        SELECT candidate.*
        FROM v53_plan_targets candidate
        WHERE candidate.plan_type = 'PREMIUM'
        ORDER BY candidate.ordinal
        OFFSET ((seed.renewal_no - 1) % v_premium_users)
        LIMIT 1
    ) target ON TRUE;

    INSERT INTO payment_transactions (
        payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
        subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
        provider_reference_code, raw_callback_data, created_at, updated_at
    )
    SELECT
        v53_seed_uuid('history-payment:' || history.ordinal), history.user_id, history.plan_id,
        'SUBSCRIPTION', 'SEPAY', 'PAID', 'SP53H' || lpad(history.ordinal::text, 4, '0'),
        CASE history.plan_type WHEN 'SKILL_BUILDER' THEN 89000.00 ELSE 199000.00 END, 'VND', 1,
        'SP53H' || lpad(history.ordinal::text, 4, '0'), history.paid_at - INTERVAL '11 minutes', history.paid_at,
        'SP53-HISTORY-TXN-' || lpad(history.ordinal::text, 4, '0'),
        'SP53-HISTORY-REF-' || lpad(history.ordinal::text, 4, '0'),
        jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'billingCycle', 'RENEWAL'),
        history.paid_at - INTERVAL '11 minutes', history.paid_at
    FROM v53_history_targets history;

    INSERT INTO platform_treasury_entries (
        treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
        actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
        idempotency_key, created_at, updated_at
    )
    SELECT
        v53_seed_uuid('history-treasury:' || history.ordinal), 'VND', 'CREDIT',
        'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT', v53_seed_uuid('history-payment:' || history.ordinal),
        CASE history.plan_type WHEN 'SKILL_BUILDER' THEN 89000.00 ELSE 199000.00 END,
        history.user_id, users.full_name, 'SP53H' || lpad(history.ordinal::text, 4, '0'),
        'Previous subscription renewal payment',
        jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'billingCycle', 'RENEWAL'),
        history.paid_at, 'v53-subscription-history-payment-' || history.ordinal,
        history.paid_at, history.paid_at
    FROM v53_history_targets history
    JOIN users ON users.user_id = history.user_id;

    INSERT INTO subscriptions (
        subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at
    )
    SELECT
        v53_seed_uuid('history-subscription:' || history.ordinal), history.user_id, history.plan_id,
        (history.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
        (history.current_start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
        history.paid_at, history.current_start_at, 'EXPIRED', history.paid_at
    FROM v53_history_targets history;

    IF (SELECT count(*) FROM v53_plan_targets WHERE plan_type = 'FREE') <> v_total_users - v_paid_users
       OR (SELECT count(*) FROM v53_plan_targets WHERE plan_type = 'SKILL_BUILDER') <> v_builder_users
       OR (SELECT count(*) FROM v53_plan_targets WHERE plan_type = 'PREMIUM') <> v_premium_users
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP53S%') <> v_paid_users
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP53H%') <> v_history_builder + v_history_premium
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'v53-subscription-current-payment-%') <> v_paid_users
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'v53-subscription-history-payment-%') <> v_history_builder + v_history_premium
       OR (SELECT count(*) FROM payment_transactions
           WHERE purpose = 'SUBSCRIPTION'
             AND (txn_ref LIKE 'DH178504436%' OR txn_ref LIKE 'V28SUB%' OR txn_ref LIKE 'SP36S%' OR txn_ref LIKE 'SP36H%')) <> 0
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions
           WHERE txn_ref LIKE 'SP53S%' OR txn_ref LIKE 'SP53H%') <> (v_direct_revenue_thousand + (v_history_builder * 89) + (v_history_premium * 199)) * 1000 THEN
        RAISE EXCEPTION 'V53 seed subscription rebalance postcondition failed';
    END IF;
END $$;

DROP FUNCTION v53_v36_uuid(TEXT);
DROP FUNCTION v53_v28_uuid(TEXT);
DROP FUNCTION v53_seed_uuid(TEXT);
