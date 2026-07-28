-- Seed non-successful payment states for dashboard and admin-list demos.
-- PENDING SePay rows remain payable for a short time; the scheduled payment
-- expiry job will move them to EXPIRED after their expire_at timestamp.

CREATE FUNCTION v39_seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v39:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v39:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v39:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v39:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v39:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v39_v36_uuid(seed TEXT)
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

DO $$
DECLARE
    v_builder_plan UUID;
    v_premium_plan UUID;
    v_row INTEGER;
    v_user_id VARCHAR(100);
    v_plan_id UUID;
    v_purpose TEXT;
    v_status TEXT;
    v_amount NUMERIC(12, 2);
    v_coin_amount INTEGER;
    v_coin_package_key TEXT;
    v_created_at TIMESTAMPTZ;
    v_expire_at TIMESTAMPTZ;
    v_reason TEXT;
BEGIN
    SELECT plan_id INTO v_builder_plan FROM service_plans WHERE plan_type = 'SKILL_BUILDER' LIMIT 1;
    SELECT plan_id INTO v_premium_plan FROM service_plans WHERE plan_type = 'PREMIUM' LIMIT 1;
    IF v_builder_plan IS NULL OR v_premium_plan IS NULL THEN
        RAISE EXCEPTION 'V39 requires SKILL_BUILDER and PREMIUM service plans';
    END IF;

    IF EXISTS (SELECT 1 FROM payment_transactions WHERE txn_ref LIKE 'SP39%') THEN
        RAISE EXCEPTION 'V39 payment rows already exist; do not apply this migration to a partially seeded database';
    END IF;

    FOR v_row IN 1..18 LOOP
        v_user_id := v39_v36_uuid('user:' || (((v_row * 13 - 1) % 100) + 1))::text;
        v_status := CASE
            WHEN v_row <= 5 THEN 'PENDING'
            WHEN v_row <= 11 THEN 'FAILED'
            WHEN v_row <= 16 THEN 'EXPIRED'
            ELSE 'CANCELED'
        END;
        v_purpose := CASE WHEN v_row IN (4, 5, 10, 11, 16) THEN 'COIN_TOP_UP' ELSE 'SUBSCRIPTION' END;
        v_plan_id := CASE
            WHEN v_purpose = 'SUBSCRIPTION' AND v_row % 3 = 0 THEN v_premium_plan
            WHEN v_purpose = 'SUBSCRIPTION' THEN v_builder_plan
            ELSE NULL
        END;
        v_amount := CASE
            WHEN v_purpose = 'COIN_TOP_UP' AND v_row % 2 = 0 THEN 50000.00
            WHEN v_purpose = 'COIN_TOP_UP' THEN 10000.00
            WHEN v_plan_id = v_premium_plan THEN 199000.00
            ELSE 89000.00
        END;
        v_coin_amount := CASE WHEN v_purpose = 'COIN_TOP_UP' THEN v_amount::integer ELSE NULL END;
        v_coin_package_key := CASE
            WHEN v_purpose <> 'COIN_TOP_UP' THEN NULL
            WHEN v_amount = 50000.00 THEN 'COIN_50000'
            ELSE 'COIN_10000'
        END;
        v_created_at := CASE
            WHEN v_status = 'PENDING' THEN CURRENT_TIMESTAMP - ((v_row + 2) * INTERVAL '4 minutes')
            ELSE CURRENT_TIMESTAMP - ((v_row + 3) * INTERVAL '9 hours')
        END;
        v_expire_at := CASE
            WHEN v_status = 'PENDING' THEN CURRENT_TIMESTAMP + ((v_row + 2) * INTERVAL '11 minutes')
            ELSE v_created_at + INTERVAL '15 minutes'
        END;
        v_reason := CASE v_status
            WHEN 'FAILED' THEN (ARRAY[
                'Transfer amount did not match the requested amount',
                'Payment reference was not found in transfer content',
                'Incoming transfer used an unsupported channel',
                'Provider callback could not be verified',
                'Transfer was sent to a different receiver account',
                'Payment was rejected after validation'
            ])[v_row - 5]
            WHEN 'EXPIRED' THEN 'Payment window expired before a valid transfer was received'
            WHEN 'CANCELED' THEN 'Payment was canceled by the learner before completion'
            ELSE 'Waiting for a verified SePay transfer'
        END;

        INSERT INTO payment_transactions (
            payment_id, user_id, plan_id, purpose, coin_amount, coin_package_key, provider, status,
            txn_ref, amount, currency, subscription_months, transfer_content, expire_at, paid_at,
            provider_transaction_id, provider_reference_code, raw_callback_data, created_at, updated_at
        ) VALUES (
            v39_seed_uuid('payment:' || v_row), v_user_id, v_plan_id, v_purpose,
            v_coin_amount, v_coin_package_key, 'SEPAY', v_status,
            'SP39' || lpad(v_row::text, 4, '0'), v_amount, 'VND',
            CASE WHEN v_purpose = 'SUBSCRIPTION' THEN 1 ELSE 0 END,
            'SP39' || lpad(v_row::text, 4, '0'), v_expire_at, NULL,
            NULL, NULL,
            jsonb_build_object('channel', 'SEPAY', 'purpose', v_purpose, 'status', v_status, 'reason', v_reason)::text,
            v_created_at, CASE WHEN v_status = 'PENDING' THEN v_created_at ELSE v_expire_at END
        );
    END LOOP;

    IF (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP39%' AND status = 'PENDING') <> 5
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP39%' AND status = 'FAILED') <> 6
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP39%' AND status = 'EXPIRED') <> 5
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP39%' AND status = 'CANCELED') <> 2
       OR EXISTS (
            SELECT 1
            FROM payment_transactions
            WHERE txn_ref LIKE 'SP39%'
              AND status = 'PENDING'
              AND expire_at <= CURRENT_TIMESTAMP
       )
       OR EXISTS (
            SELECT 1
            FROM payment_transactions
            WHERE txn_ref LIKE 'SP39%'
              AND status <> 'PENDING'
              AND paid_at IS NOT NULL
       ) THEN
        RAISE EXCEPTION 'V39 postcondition failed; payment status seed is rolled back';
    END IF;
END $$;

DROP FUNCTION v39_v36_uuid(TEXT);
DROP FUNCTION v39_seed_uuid(TEXT);
