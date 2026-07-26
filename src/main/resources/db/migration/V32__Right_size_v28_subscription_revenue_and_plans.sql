-- Right-size the V28 subscription demo from 45.295M to 10.995M. Together
-- with the pre-existing 9.005M production demo data this yields ~20M VND on
-- the admin revenue card, while retaining a believable free/builder/premium mix.

CREATE FUNCTION v32_v28_uuid(seed TEXT)
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
        RAISE EXCEPTION 'V32 requires FREE, SKILL_BUILDER and PREMIUM service plans';
    END IF;

    -- Keep 90 Builder payments and 15 Premium payments:
    -- 90 x 89,000 + 15 x 199,000 = 10,995,000 VND.
    -- Treasury rows have no FK, but are removed first to keep the financial
    -- journal exactly aligned with the retained payment history.
    DELETE FROM platform_treasury_entries treasury
    USING payment_transactions payment
    WHERE treasury.reference_id = payment.payment_id
      AND payment.txn_ref LIKE 'V28SUB%'
      AND NOT (
          substring(payment.txn_ref FROM 7)::integer BETWEEN 1 AND 90
          OR substring(payment.txn_ref FROM 7)::integer BETWEEN 141 AND 155
      );

    DELETE FROM payment_transactions payment
    WHERE payment.txn_ref LIKE 'V28SUB%'
      AND NOT (
          substring(payment.txn_ref FROM 7)::integer BETWEEN 1 AND 90
          OR substring(payment.txn_ref FROM 7)::integer BETWEEN 141 AND 155
      );

    -- Keep all 184 demo users active but put unpaid users on the normal Free
    -- plan. Their V28 subscription ids are deterministic and are isolated from
    -- real users, so no production subscription is modified.
    UPDATE subscriptions subscription
    SET plan_id = CASE
            WHEN seed.ordinal BETWEEN 1 AND 90 THEN v_builder_plan
            WHEN seed.ordinal BETWEEN 141 AND 155 THEN v_premium_plan
            ELSE v_free_plan
        END,
        start_date = DATE '2026-07-01',
        start_at = TIMESTAMPTZ '2026-07-01 00:00:00+07',
        end_date = CASE
            WHEN seed.ordinal BETWEEN 1 AND 90 OR seed.ordinal BETWEEN 141 AND 155 THEN DATE '2026-08-01'
            ELSE NULL
        END,
        end_at = CASE
            WHEN seed.ordinal BETWEEN 1 AND 90 OR seed.ordinal BETWEEN 141 AND 155
                THEN TIMESTAMPTZ '2026-08-01 00:00:00+07'
            ELSE NULL
        END,
        status = 'ACTIVE'
    FROM generate_series(1, 184) AS seed(ordinal)
    WHERE subscription.subscription_id = v32_v28_uuid('subscription:' || seed.ordinal);

    IF (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'V28SUB%') <> 105
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'V28SUB%') <> 10995000
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'v28-subscription-payment-%') <> 105
       OR (SELECT count(*) FROM subscriptions WHERE subscription_id IN (
               SELECT v32_v28_uuid('subscription:' || ordinal) FROM generate_series(1, 184) AS seed(ordinal)
           ) AND plan_id = v_free_plan AND status = 'ACTIVE') <> 79
       OR (SELECT count(*) FROM subscriptions WHERE subscription_id IN (
               SELECT v32_v28_uuid('subscription:' || ordinal) FROM generate_series(1, 184) AS seed(ordinal)
           ) AND plan_id = v_builder_plan AND status = 'ACTIVE') <> 90
       OR (SELECT count(*) FROM subscriptions WHERE subscription_id IN (
               SELECT v32_v28_uuid('subscription:' || ordinal) FROM generate_series(1, 184) AS seed(ordinal)
           ) AND plan_id = v_premium_plan AND status = 'ACTIVE') <> 15 THEN
        RAISE EXCEPTION 'V32 postcondition failed; V28 subscription right-sizing is rolled back';
    END IF;
END $$;

DROP FUNCTION v32_v28_uuid(TEXT);
