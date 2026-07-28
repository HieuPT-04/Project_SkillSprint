-- V53 rebuilt the recognizable subscription cohort but left 131 older seed
-- payments whose references did not carry the expected V27 prefix.  This
-- correction is intentionally limited to reserved .invalid accounts plus the
-- nineteen legacy Gmail seed accounts verified from the production report.
--
-- Result: 384 learner subscriptions -> 269 Free, 70 Skill Builder, 45 Premium
-- (115 paid = 29.95%) and 20.018M VND total subscription revenue.

CREATE FUNCTION v54_uuid(seed TEXT)
RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (
        substr(md5('skillsprint-v54:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v54:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v54:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v54:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v54:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v54_v53_uuid(seed TEXT)
RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (
        substr(md5('skillsprint-v53:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v53:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v53:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v53:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v53:' || seed), 21, 12)
    )::uuid;
$$;

DO $$
DECLARE
    v_free_plan UUID;
    v_builder_plan UUID;
    v_premium_plan UUID;
BEGIN
    SELECT plan_id INTO v_free_plan FROM service_plans WHERE plan_type = 'FREE' LIMIT 1;
    SELECT plan_id INTO v_builder_plan FROM service_plans WHERE plan_type = 'SKILL_BUILDER' LIMIT 1;
    SELECT plan_id INTO v_premium_plan FROM service_plans WHERE plan_type = 'PREMIUM' LIMIT 1;

    IF v_free_plan IS NULL OR v_builder_plan IS NULL OR v_premium_plan IS NULL THEN
        RAISE EXCEPTION 'V54 requires FREE, SKILL_BUILDER and PREMIUM service plans';
    END IF;

    CREATE TEMP TABLE v54_unmatched_seed_users ON COMMIT DROP AS
    SELECT user_id
    FROM users
    WHERE email ILIKE '%@skillsprint.invalid'
       OR user_id IN (
           '89ba354c-8001-707e-fe11-91ba2a0052dc',
           '698ad57c-5011-7031-5208-19f99c5a3f66',
           '49aa555c-3071-7010-a64b-a65394b3b7fd',
           '995af5fc-8071-70be-b22d-a3fb1dfbfeb3',
           '29daa52c-5061-7097-1c2f-08ebaf50b1cd',
           'a91a256c-f0b1-703d-a2bc-5f31824ca728',
           'd9cac50c-4081-70ee-9cdc-be49ea70262c',
           '59da457c-f041-7015-4a1e-f40267f4d4a8',
           '592ad59c-4051-70e4-d0bc-5c0a3a385701',
           '693a855c-b0b1-707c-818c-28b20e0e9d46',
           'c93a557c-90e1-7094-bf33-741a25991bb3',
           'd90a051c-f061-7065-929c-32c45dd61780',
           '490a75dc-4011-709f-858f-3db5e2ea119b',
           '29ba155c-a071-7098-367f-4e89f1214a3c',
           '99faf50c-1071-70d5-5f0b-74944d280281',
           'a94a25fc-30b1-703c-8116-57846990031c',
           'd9fa156c-8031-7062-b740-ae74603b0a5b',
           'e9ba652c-4001-70da-0770-ac9a953e0104',
           'd9aad51c-00d1-70bf-ec2d-f6f25f806003'
       );

    CREATE TEMP TABLE v54_old_payment_ids ON COMMIT DROP AS
    SELECT payment.payment_id
    FROM payment_transactions payment
    JOIN v54_unmatched_seed_users seed ON seed.user_id = payment.user_id
    WHERE payment.purpose = 'SUBSCRIPTION'
      AND payment.status = 'PAID'
      AND payment.txn_ref NOT LIKE 'SP53%'
      AND payment.txn_ref NOT LIKE 'SP54%';

    IF (SELECT count(*) FROM v54_old_payment_ids) <> 131
       OR (SELECT COALESCE(sum(payment.amount), 0)
           FROM payment_transactions payment
           JOIN v54_old_payment_ids old_payment ON old_payment.payment_id = payment.payment_id) <> 15069000
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP53S%') <> 90
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'SP53S%') <> 11970000
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP53H%') <> 62
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'SP53H%') <> 8048000 THEN
        RAISE EXCEPTION 'V54 expected the reported 131 old payments and V53 payment baseline';
    END IF;

    CREATE TEMP TABLE v54_promotions ON COMMIT DROP AS
    WITH deduplicated_active_free_seed_subscriptions AS (
        SELECT DISTINCT ON (subscription.user_id)
            subscription.subscription_id,
            subscription.user_id
        FROM subscriptions subscription
        JOIN v54_unmatched_seed_users seed ON seed.user_id = subscription.user_id
        WHERE subscription.status = 'ACTIVE'
          AND subscription.plan_id = v_free_plan
        ORDER BY subscription.user_id, subscription.created_at DESC, subscription.subscription_id
    ), active_free_seed_subscriptions AS (
        SELECT
            source.*,
            row_number() OVER (ORDER BY md5(source.user_id::text))::integer AS ordinal
        FROM deduplicated_active_free_seed_subscriptions source
    )
    SELECT
        source.subscription_id,
        source.user_id,
        source.ordinal,
        CASE WHEN source.ordinal <= 15 THEN 'SKILL_BUILDER' ELSE 'PREMIUM' END AS plan_type,
        CASE WHEN source.ordinal <= 15 THEN v_builder_plan ELSE v_premium_plan END AS plan_id,
        TIMESTAMPTZ '2026-07-04 08:30:00+07'
            + ((source.ordinal * 13) % 20) * INTERVAL '1 day'
            + ((source.ordinal * 29) % 420) * INTERVAL '1 minute' AS paid_at
    FROM active_free_seed_subscriptions source
    WHERE source.ordinal <= 20;

    IF (SELECT count(*) FROM v54_promotions WHERE plan_type = 'SKILL_BUILDER') <> 15
       OR (SELECT count(*) FROM v54_promotions WHERE plan_type = 'PREMIUM') <> 5 THEN
        RAISE EXCEPTION 'V54 expected 15 Builder and 5 Premium promotions from the verified seed users';
    END IF;

    UPDATE subscriptions subscription
    SET plan_id = promotion.plan_id,
        start_at = promotion.paid_at,
        start_date = (promotion.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
        end_at = promotion.paid_at + INTERVAL '1 month',
        end_date = ((promotion.paid_at + INTERVAL '1 month') AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
        status = 'ACTIVE'
    FROM v54_promotions promotion
    WHERE subscription.subscription_id = promotion.subscription_id;

    DELETE FROM platform_treasury_entries treasury
    USING v54_old_payment_ids payment
    WHERE treasury.reference_type = 'PAYMENT'
      AND treasury.reference_id = payment.payment_id;

    DELETE FROM payment_transactions payment
    USING v54_old_payment_ids old_payment
    WHERE payment.payment_id = old_payment.payment_id;

    INSERT INTO payment_transactions (
        payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
        subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
        provider_reference_code, raw_callback_data, created_at, updated_at
    )
    SELECT
        v54_uuid('current-payment:' || promotion.ordinal), promotion.user_id, promotion.plan_id,
        'SUBSCRIPTION', 'SEPAY', 'PAID', 'SP54S' || lpad(promotion.ordinal::text, 4, '0'),
        CASE promotion.plan_type WHEN 'SKILL_BUILDER' THEN 89000.00 ELSE 199000.00 END, 'VND', 1,
        'SP54S' || lpad(promotion.ordinal::text, 4, '0'), promotion.paid_at - INTERVAL '12 minutes',
        promotion.paid_at, 'SP54-CURRENT-TXN-' || lpad(promotion.ordinal::text, 4, '0'),
        'SP54-CURRENT-REF-' || lpad(promotion.ordinal::text, 4, '0'),
        jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'verified', true)::text,
        promotion.paid_at - INTERVAL '12 minutes', promotion.paid_at
    FROM v54_promotions promotion;

    INSERT INTO platform_treasury_entries (
        treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
        actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
        idempotency_key, created_at, updated_at
    )
    SELECT
        v54_uuid('current-treasury:' || promotion.ordinal), 'VND', 'CREDIT',
        'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT', v54_uuid('current-payment:' || promotion.ordinal),
        CASE promotion.plan_type WHEN 'SKILL_BUILDER' THEN 89000.00 ELSE 199000.00 END,
        promotion.user_id, users.full_name, 'SP54S' || lpad(promotion.ordinal::text, 4, '0'),
        'Current subscription package payment',
        jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'billingCycle', 'CURRENT'),
        promotion.paid_at, 'v54-subscription-current-payment-' || promotion.ordinal,
        promotion.paid_at, promotion.paid_at
    FROM v54_promotions promotion
    JOIN users ON users.user_id = promotion.user_id;

    DELETE FROM platform_treasury_entries treasury
    USING payment_transactions payment
    WHERE treasury.reference_type = 'PAYMENT'
      AND treasury.reference_id = payment.payment_id
      AND payment.txn_ref LIKE 'SP53H%';

    DELETE FROM payment_transactions WHERE txn_ref LIKE 'SP53H%';

    DELETE FROM subscriptions subscription
    WHERE subscription.subscription_id IN (
        SELECT v54_v53_uuid('history-subscription:' || n)
        FROM generate_series(1, 200) AS seed(n)
    );

    CREATE TEMP TABLE v54_paid_seed_subscriptions ON COMMIT DROP AS
    SELECT DISTINCT ON (subscription.user_id)
        subscription.user_id,
        subscription.plan_id,
        plan.plan_type,
        subscription.start_at
    FROM subscriptions subscription
    JOIN service_plans plan ON plan.plan_id = subscription.plan_id
    WHERE subscription.status = 'ACTIVE'
      AND plan.plan_type IN ('SKILL_BUILDER', 'PREMIUM')
      AND (
          EXISTS (SELECT 1 FROM v54_unmatched_seed_users seed WHERE seed.user_id = subscription.user_id)
          OR EXISTS (
              SELECT 1
              FROM payment_transactions payment
              WHERE payment.user_id = subscription.user_id
                AND payment.txn_ref LIKE 'SP53S%'
          )
      )
    ORDER BY subscription.user_id, subscription.created_at DESC, subscription.subscription_id;

    IF (SELECT count(*) FROM v54_paid_seed_subscriptions WHERE plan_type = 'SKILL_BUILDER') <> 70
       OR (SELECT count(*) FROM v54_paid_seed_subscriptions WHERE plan_type = 'PREMIUM') <> 45 THEN
        RAISE EXCEPTION 'V54 expected final paid seed mix of 70 Builder and 45 Premium users';
    END IF;

    CREATE TEMP TABLE v54_history_targets ON COMMIT DROP AS
    SELECT
        ranked.user_id,
        ranked.plan_id,
        ranked.plan_type,
        ranked.start_at,
        ranked.start_at - INTERVAL '1 month' AS paid_at,
        ranked.ordinal
    FROM (
        SELECT
            source.*,
            row_number() OVER (ORDER BY md5(source.user_id::text))::integer AS ordinal
        FROM v54_paid_seed_subscriptions source
        WHERE source.plan_type = 'SKILL_BUILDER'
    ) ranked
    WHERE ranked.ordinal <= 24

    UNION ALL

    SELECT
        ranked.user_id,
        ranked.plan_id,
        ranked.plan_type,
        ranked.start_at,
        ranked.start_at - INTERVAL '1 month' AS paid_at,
        24 + ranked.ordinal
    FROM (
        SELECT
            source.*,
            row_number() OVER (ORDER BY md5(source.user_id::text))::integer AS ordinal
        FROM v54_paid_seed_subscriptions source
        WHERE source.plan_type = 'PREMIUM'
    ) ranked
    WHERE ranked.ordinal <= 18;

    INSERT INTO payment_transactions (
        payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
        subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
        provider_reference_code, raw_callback_data, created_at, updated_at
    )
    SELECT
        v54_uuid('history-payment:' || history.ordinal), history.user_id, history.plan_id,
        'SUBSCRIPTION', 'SEPAY', 'PAID', 'SP54H' || lpad(history.ordinal::text, 4, '0'),
        CASE history.plan_type WHEN 'SKILL_BUILDER' THEN 89000.00 ELSE 199000.00 END, 'VND', 1,
        'SP54H' || lpad(history.ordinal::text, 4, '0'), history.paid_at - INTERVAL '11 minutes', history.paid_at,
        'SP54-HISTORY-TXN-' || lpad(history.ordinal::text, 4, '0'),
        'SP54-HISTORY-REF-' || lpad(history.ordinal::text, 4, '0'),
        jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'billingCycle', 'RENEWAL')::text,
        history.paid_at - INTERVAL '11 minutes', history.paid_at
    FROM v54_history_targets history;

    INSERT INTO platform_treasury_entries (
        treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
        actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
        idempotency_key, created_at, updated_at
    )
    SELECT
        v54_uuid('history-treasury:' || history.ordinal), 'VND', 'CREDIT',
        'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT', v54_uuid('history-payment:' || history.ordinal),
        CASE history.plan_type WHEN 'SKILL_BUILDER' THEN 89000.00 ELSE 199000.00 END,
        history.user_id, users.full_name, 'SP54H' || lpad(history.ordinal::text, 4, '0'),
        'Previous subscription renewal payment',
        jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'billingCycle', 'RENEWAL'),
        history.paid_at, 'v54-subscription-history-payment-' || history.ordinal,
        history.paid_at, history.paid_at
    FROM v54_history_targets history
    JOIN users ON users.user_id = history.user_id;

    INSERT INTO subscriptions (
        subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at
    )
    SELECT
        v54_uuid('history-subscription:' || history.ordinal), history.user_id, history.plan_id,
        (history.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
        (history.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
        history.paid_at, history.start_at, 'EXPIRED', history.paid_at
    FROM v54_history_targets history;

    IF (SELECT count(*) FROM payment_transactions
        WHERE purpose = 'SUBSCRIPTION' AND status = 'PAID') <> 152
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions
        WHERE purpose = 'SUBSCRIPTION' AND status = 'PAID') <> 20018000
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP54S%') <> 20
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'SP54S%') <> 2330000
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP54H%') <> 42
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'SP54H%') <> 5718000
       OR (SELECT count(*) FROM subscriptions WHERE status = 'ACTIVE' AND plan_id = v_free_plan) <> 269
       OR (SELECT count(*) FROM subscriptions WHERE status = 'ACTIVE' AND plan_id = v_builder_plan) <> 70
       OR (SELECT count(*) FROM subscriptions WHERE status = 'ACTIVE' AND plan_id = v_premium_plan) <> 45 THEN
        RAISE EXCEPTION 'V54 seed subscription correction postcondition failed';
    END IF;
END $$;

DROP FUNCTION v54_v53_uuid(TEXT);
DROP FUNCTION v54_uuid(TEXT);
