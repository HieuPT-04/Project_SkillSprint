-- Seed an additional isolated learner cohort for local/demo dashboards.
-- The accounts use the reserved .invalid domain and therefore cannot receive
-- real communication. Twenty-eight of the one hundred learners have a paid
-- package: 11 choose Skill Builder and 17 choose Premium. Together with the
-- existing 284-account seed dashboard this produces 150 paid users; adding
-- the planned 116 Free learners later yields exactly 500 users / 30% paid.

CREATE FUNCTION v36_seed_uuid(seed TEXT)
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
    v_learner_role UUID;
    v_free_plan UUID;
    v_builder_plan UUID;
    v_premium_plan UUID;
    v_row INTEGER;
    v_paid_ordinal INTEGER := 0;
    v_user_id VARCHAR(100);
    v_created_at TIMESTAMPTZ;
    v_last_login_at TIMESTAMPTZ;
    v_paid_at TIMESTAMPTZ;
    v_plan_id UUID;
    v_amount NUMERIC(12, 2);
    v_reference TEXT;
    v_history_plan_id UUID;
    v_history_amount NUMERIC(12, 2);
    v_history_paid_at TIMESTAMPTZ;
    v_history_reference TEXT;
    v_history_cycle TEXT;
BEGIN
    SELECT role_id INTO v_learner_role
    FROM roles
    WHERE role_name = 'LEARNER'
    LIMIT 1;

    SELECT plan_id INTO v_free_plan FROM service_plans WHERE plan_type = 'FREE' LIMIT 1;
    SELECT plan_id INTO v_builder_plan FROM service_plans WHERE plan_type = 'SKILL_BUILDER' LIMIT 1;
    SELECT plan_id INTO v_premium_plan FROM service_plans WHERE plan_type = 'PREMIUM' LIMIT 1;

    IF v_learner_role IS NULL OR v_free_plan IS NULL OR v_builder_plan IS NULL OR v_premium_plan IS NULL THEN
        RAISE EXCEPTION 'V36 requires LEARNER role and FREE, SKILL_BUILDER, PREMIUM service plans';
    END IF;

    IF EXISTS (SELECT 1 FROM users WHERE email LIKE 'seed36.%@skillsprint.invalid') THEN
        RAISE EXCEPTION 'V36 demo learners already exist; do not apply this migration to a partially seeded database';
    END IF;

    FOR v_row IN 1..100 LOOP
        v_user_id := v36_seed_uuid('user:' || v_row)::text;
        v_created_at := TIMESTAMPTZ '2026-04-05 08:10:00+07'
            + ((v_row - 1) * INTERVAL '1 day 1 hour 45 minutes')
            + ((v_row * 37 % 145) * INTERVAL '5 minutes');
        v_last_login_at := LEAST(
            v_created_at + INTERVAL '1 day' + ((v_row * 13 % 126) * INTERVAL '12 hours'),
            TIMESTAMPTZ '2026-07-27 21:40:00+07'
        );

        INSERT INTO users (
            user_id, email, email_verified, full_name, timezone, status,
            last_login_at, created_at, updated_at
        ) VALUES (
            v_user_id,
            'seed36.' ||
                (ARRAY['nguyen','tran','le','pham','hoang','vo','dang','bui','do','ho'])[((v_row - 1) % 10) + 1] || '.' ||
                (ARRAY['minh-anh','gia-huy','thao-nguyen','quoc-bao','khanh-linh','duc-minh','phuong-anh','tuan-kiet','ngoc-han','hai-nam'])[((v_row - 1) / 10) + 1] ||
                lpad(v_row::text, 3, '0') || '@skillsprint.invalid',
            TRUE,
            (ARRAY['Nguyễn','Trần','Lê','Phạm','Hoàng','Võ','Đặng','Bùi','Đỗ','Hồ'])[((v_row - 1) % 10) + 1] || ' ' ||
                (ARRAY['Minh Anh','Gia Huy','Thảo Nguyên','Quốc Bảo','Khánh Linh','Đức Minh','Phương Anh','Tuấn Kiệt','Ngọc Hân','Hải Nam'])[((v_row - 1) / 10) + 1],
            'Asia/Ho_Chi_Minh', 'ACTIVE', v_last_login_at, v_created_at, v_last_login_at
        );

        INSERT INTO user_roles (user_role_id, user_id, role_id, granted_at)
        VALUES (v36_seed_uuid('learner-role:' || v_row), v_user_id, v_learner_role, v_created_at);

        -- Multiplication by 17 permutes 0..99, giving exactly 28 paid users
        -- distributed throughout the registration cohort rather than clustered.
        IF (v_row * 17) % 100 < 28 THEN
            v_paid_ordinal := v_paid_ordinal + 1;
            v_plan_id := CASE WHEN v_paid_ordinal <= 11 THEN v_builder_plan ELSE v_premium_plan END;
            v_amount := CASE WHEN v_paid_ordinal <= 11 THEN 89000.00 ELSE 199000.00 END;
            v_paid_at := GREATEST(
                v_created_at + INTERVAL '2 days' + ((v_row % 3) * INTERVAL '3 hours'),
                TIMESTAMPTZ '2026-07-01 08:25:00+07'
                    + ((v_paid_ordinal - 1) * INTERVAL '16 hours 10 minutes')
                    + ((v_row * 11 % 83) * INTERVAL '5 minutes')
            );
            v_reference := 'SP36S' || lpad(v_paid_ordinal::text, 4, '0');

            INSERT INTO payment_transactions (
                payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
                subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
                provider_reference_code, raw_callback_data, created_at, updated_at
            ) VALUES (
                v36_seed_uuid('payment:' || v_paid_ordinal), v_user_id, v_plan_id,
                'SUBSCRIPTION', 'SEPAY', 'PAID', v_reference, v_amount, 'VND', 1,
                v_reference, v_paid_at - INTERVAL '13 minutes', v_paid_at,
                'SP36-TXN-' || lpad(v_paid_ordinal::text, 4, '0'),
                'SP36-REF-' || lpad(v_paid_ordinal::text, 4, '0'),
                jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'verified', true)::text,
                v_paid_at - INTERVAL '13 minutes', v_paid_at
            );

            INSERT INTO platform_treasury_entries (
                treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
                actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
                idempotency_key, created_at, updated_at
            ) VALUES (
                v36_seed_uuid('treasury:' || v_paid_ordinal), 'VND', 'CREDIT',
                'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT', v36_seed_uuid('payment:' || v_paid_ordinal), v_amount,
                v_user_id, (SELECT full_name FROM users WHERE user_id = v_user_id), v_reference,
                'Subscription package payment received',
                jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION'), v_paid_at,
                'v36-subscription-payment-' || v_paid_ordinal, v_paid_at, v_paid_at
            );

            -- The first 18 paid learners have a believable preceding billing
            -- cycle. Six Premium users upgraded from Skill Builder; this gives
            -- the payment history a mix of renewals and upgrades while each
            -- learner still has exactly one current ACTIVE subscription.
            IF v_paid_ordinal <= 18 THEN
                v_history_plan_id := CASE
                    WHEN v_paid_ordinal BETWEEN 12 AND 17 THEN v_builder_plan
                    ELSE v_plan_id
                END;
                v_history_amount := CASE
                    WHEN v_history_plan_id = v_builder_plan THEN 89000.00
                    ELSE 199000.00
                END;
                v_history_paid_at := v_paid_at - INTERVAL '1 month';
                v_history_reference := 'SP36H' || lpad(v_paid_ordinal::text, 4, '0');
                v_history_cycle := CASE
                    WHEN v_history_plan_id <> v_plan_id THEN 'UPGRADE_PREVIOUS_CYCLE'
                    ELSE 'RENEWAL_PREVIOUS_CYCLE'
                END;

                INSERT INTO payment_transactions (
                    payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
                    subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
                    provider_reference_code, raw_callback_data, created_at, updated_at
                ) VALUES (
                    v36_seed_uuid('history-payment:' || v_paid_ordinal), v_user_id, v_history_plan_id,
                    'SUBSCRIPTION', 'SEPAY', 'PAID', v_history_reference, v_history_amount, 'VND', 1,
                    v_history_reference, v_history_paid_at - INTERVAL '11 minutes', v_history_paid_at,
                    'SP36-HISTORY-TXN-' || lpad(v_paid_ordinal::text, 4, '0'),
                    'SP36-HISTORY-REF-' || lpad(v_paid_ordinal::text, 4, '0'),
                    jsonb_build_object(
                        'channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'verified', true,
                        'subscriptionCycle', v_history_cycle
                    )::text,
                    v_history_paid_at - INTERVAL '11 minutes', v_history_paid_at
                );

                INSERT INTO platform_treasury_entries (
                    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
                    actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
                    idempotency_key, created_at, updated_at
                ) VALUES (
                    v36_seed_uuid('history-treasury:' || v_paid_ordinal), 'VND', 'CREDIT',
                    'SUBSCRIPTION_PAYMENT_RECEIVED', 'PAYMENT', v36_seed_uuid('history-payment:' || v_paid_ordinal),
                    v_history_amount, v_user_id, (SELECT full_name FROM users WHERE user_id = v_user_id),
                    v_history_reference,
                    CASE WHEN v_history_cycle = 'UPGRADE_PREVIOUS_CYCLE'
                        THEN 'Skill Builder subscription before Premium upgrade'
                        ELSE 'Previous subscription renewal payment' END,
                    jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'subscriptionCycle', v_history_cycle),
                    v_history_paid_at, 'v36-subscription-history-payment-' || v_paid_ordinal,
                    v_history_paid_at, v_history_paid_at
                );

                INSERT INTO subscriptions (
                    subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at
                ) VALUES (
                    v36_seed_uuid('history-subscription:' || v_paid_ordinal), v_user_id, v_history_plan_id,
                    (v_history_paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                    (v_paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                    v_history_paid_at, v_paid_at, 'EXPIRED', v_history_paid_at
                );
            END IF;

            INSERT INTO subscriptions (
                subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at
            ) VALUES (
                v36_seed_uuid('subscription:' || v_row), v_user_id, v_plan_id,
                (v_paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                ((v_paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh') + INTERVAL '1 month')::date,
                v_paid_at, v_paid_at + INTERVAL '1 month', 'ACTIVE', v_paid_at
            );
        ELSE
            INSERT INTO subscriptions (
                subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at
            ) VALUES (
                v36_seed_uuid('subscription:' || v_row), v_user_id, v_free_plan,
                (v_created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, NULL,
                v_created_at, NULL, 'ACTIVE', v_created_at
            );
        END IF;
    END LOOP;

    IF (SELECT count(*) FROM users WHERE user_id IN (
            SELECT v36_seed_uuid('user:' || n)::text FROM generate_series(1, 100) AS n
        )) <> 100
       OR (SELECT count(*) FROM user_roles WHERE user_role_id IN (
            SELECT v36_seed_uuid('learner-role:' || n) FROM generate_series(1, 100) AS n
        ) AND role_id = v_learner_role) <> 100
       OR (SELECT count(*) FROM subscriptions WHERE subscription_id IN (
            SELECT v36_seed_uuid('subscription:' || n) FROM generate_series(1, 100) AS n
        ) AND status = 'ACTIVE') <> 100
       OR (SELECT count(*) FROM subscriptions WHERE subscription_id IN (
            SELECT v36_seed_uuid('subscription:' || n) FROM generate_series(1, 100) AS n
        ) AND plan_id = v_free_plan) <> 72
       OR (SELECT count(*) FROM subscriptions WHERE subscription_id IN (
            SELECT v36_seed_uuid('subscription:' || n) FROM generate_series(1, 100) AS n
        ) AND plan_id = v_builder_plan) <> 11
       OR (SELECT count(*) FROM subscriptions WHERE subscription_id IN (
            SELECT v36_seed_uuid('subscription:' || n) FROM generate_series(1, 100) AS n
        ) AND plan_id = v_premium_plan) <> 17
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP36S%'
           AND purpose = 'SUBSCRIPTION' AND status = 'PAID') <> 28
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'SP36S%') <> 4362000
       OR (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'SP36H%'
           AND purpose = 'SUBSCRIPTION' AND status = 'PAID') <> 18
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'SP36H%') <> 1712000
       OR (SELECT count(*) FROM subscriptions WHERE subscription_id IN (
            SELECT v36_seed_uuid('history-subscription:' || n) FROM generate_series(1, 18) AS n
        ) AND status = 'EXPIRED') <> 18
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'v36-subscription-payment-%'
           AND asset = 'VND' AND direction = 'CREDIT') <> 28
       OR (SELECT COALESCE(sum(amount), 0) FROM platform_treasury_entries
           WHERE idempotency_key LIKE 'v36-subscription-payment-%') <> 4362000
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'v36-subscription-history-payment-%'
           AND asset = 'VND' AND direction = 'CREDIT') <> 18
       OR (SELECT COALESCE(sum(amount), 0) FROM platform_treasury_entries
           WHERE idempotency_key LIKE 'v36-subscription-history-payment-%') <> 1712000 THEN
        RAISE EXCEPTION 'V36 postcondition failed; realistic learner seed is rolled back';
    END IF;
END $$;

DROP FUNCTION v36_seed_uuid(TEXT);
