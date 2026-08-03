-- V30 extends the already-applied V28 demo safely. Do not rewrite V28/V29:
-- Flyway has recorded their checksums on production.

CREATE FUNCTION v30_seed_uuid(seed TEXT)
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

DO $$
DECLARE
    v_row INTEGER;
    v_user_id VARCHAR(100);
    v_payment_id UUID;
    v_wallet_id UUID;
    v_balance_before INTEGER;
    v_coin_amount INTEGER;
    v_package_key VARCHAR(50);
    v_paid_at TIMESTAMPTZ;
BEGIN
    -- Replace the placeholder display names while preserving stable user ids and
    -- intentionally non-deliverable demo email addresses.
    CREATE TEMP TABLE v30_demo_users (
        user_id VARCHAR(100) PRIMARY KEY,
        ordinal INTEGER NOT NULL
    ) ON COMMIT DROP;
    INSERT INTO v30_demo_users (user_id, ordinal)
    SELECT user_id, row_number() OVER (ORDER BY email)::integer
    FROM users
    WHERE email LIKE 'v28-demo-%@example.invalid';

    UPDATE users u
    SET full_name =
            (ARRAY[
                'Nguyễn Minh Anh', 'Trần Gia Bảo', 'Lê Hoàng Nam', 'Phạm Khánh Linh',
                'Vũ Đức Huy', 'Đỗ Ngọc Mai', 'Bùi Quang Hưng', 'Hồ Thanh Hà',
                'Ngô Nhật Minh', 'Dương Thảo Vy', 'Phan Quốc Bảo', 'Huỳnh Gia Hân',
                'Đặng Minh Khang', 'Lý Phương Anh', 'Đoàn Tuấn Kiệt', 'Cao Bích Ngọc'
            ])[((d.ordinal - 1) % 16) + 1]
            || CASE WHEN d.ordinal > 16 THEN ' ' || ((d.ordinal - 1) / 16 + 1)::text ELSE '' END,
        email =
            (ARRAY[
                'nguyen.minh.anh', 'tran.gia.bao', 'le.hoang.nam', 'pham.khanh.linh',
                'vu.duc.huy', 'do.ngoc.mai', 'bui.quang.hung', 'ho.thanh.ha',
                'ngo.nhat.minh', 'duong.thao.vy', 'phan.quoc.bao', 'huynh.gia.han',
                'dang.minh.khang', 'ly.phuong.anh', 'doan.tuan.kiet', 'cao.bich.ngoc'
            ])[((d.ordinal - 1) % 16) + 1]
            || CASE WHEN d.ordinal > 16 THEN '.' || ((d.ordinal - 1) / 16 + 1)::text ELSE '' END
            || '@skillsprint.invalid',
        updated_at = CURRENT_TIMESTAMP
    FROM v30_demo_users d
    WHERE u.user_id = d.user_id;

    -- 56 verified top-ups, total 1,500,000 VND/Coin:
    -- 35 x 10K, 19 x 50K, 2 x 100K. This is about 2x the visible
    -- marketplace gross, leaving a believable reserve in buyer wallets.
    FOR v_row IN 1..56 LOOP
        v_user_id := (SELECT user_id FROM v30_demo_users WHERE ordinal = v_row);
        IF v_user_id IS NULL THEN
            RAISE EXCEPTION 'V30 requires the V28 demo users before seeding Coin top-ups';
        END IF;
        v_coin_amount := CASE WHEN v_row <= 35 THEN 10000 WHEN v_row <= 54 THEN 50000 ELSE 100000 END;
        v_package_key := CASE WHEN v_row <= 35 THEN 'COIN_10000' WHEN v_row <= 54 THEN 'COIN_50000' ELSE 'COIN_100000' END;
        v_payment_id := v30_seed_uuid('coin-payment:' || v_row);
        v_paid_at := TIMESTAMPTZ '2026-05-05 09:00:00+07' + ((v_row - 1) * INTERVAL '36 hours');

        INSERT INTO payment_transactions (
            payment_id, user_id, plan_id, purpose, coin_amount, coin_package_key, provider, status,
            txn_ref, amount, currency, subscription_months, transfer_content, expire_at, paid_at,
            provider_transaction_id, provider_reference_code, raw_callback_data, created_at, updated_at
        ) VALUES (
            v_payment_id, v_user_id, NULL, 'COIN_TOP_UP', v_coin_amount, v_package_key, 'SEPAY', 'PAID',
            'V30COIN' || lpad(v_row::text, 4, '0'), v_coin_amount, 'VND', 0,
            'V30COIN' || lpad(v_row::text, 4, '0'), v_paid_at - INTERVAL '15 minutes', v_paid_at,
            'V30-SEPAY-' || lpad(v_row::text, 4, '0'), 'V30REF' || lpad(v_row::text, 4, '0'),
            jsonb_build_object('seed', 'V30', 'purpose', 'COIN_TOP_UP', 'coinAmount', v_coin_amount),
            v_paid_at, v_paid_at
        );

        INSERT INTO user_wallets (wallet_id, user_id, balance, created_at, updated_at)
        VALUES (v30_seed_uuid('wallet:' || v_row), v_user_id, v_coin_amount, v_paid_at, v_paid_at)
        ON CONFLICT (user_id) DO UPDATE
        SET balance = user_wallets.balance + EXCLUDED.balance,
            updated_at = EXCLUDED.updated_at
        RETURNING wallet_id, balance - v_coin_amount INTO v_wallet_id, v_balance_before;

        INSERT INTO wallet_transactions (
            transaction_id, wallet_id, direction, amount, balance_before, balance_after,
            reference_type, reference_id, created_at, updated_at
        ) VALUES (
            v30_seed_uuid('wallet-transaction:' || v_row), v_wallet_id, 'CREDIT', v_coin_amount,
            v_balance_before, v_balance_before + v_coin_amount, 'COIN_TOP_UP', v_payment_id, v_paid_at, v_paid_at
        );

        INSERT INTO platform_treasury_entries (
            treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
            actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
            note, metadata, occurred_at, idempotency_key, created_at, updated_at
        ) VALUES (
            v30_seed_uuid('treasury:' || v_row), 'VND', 'CREDIT', 'COIN_TOP_UP_RECEIVED', 'PAYMENT', v_payment_id,
            v_coin_amount, 'SYSTEM', v_user_id, (SELECT full_name FROM users WHERE user_id = v_user_id),
            'V30COIN' || lpad(v_row::text, 4, '0'), 'Coin top-up received',
            jsonb_build_object('coinAmount', v_coin_amount, 'packageKey', v_package_key, 'seed', 'V30'), v_paid_at,
            'COIN_TOP_UP_RECEIVED:' || v_payment_id, v_paid_at, v_paid_at
        );
    END LOOP;

    IF (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'V30COIN%') <> 56
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'V30COIN%') <> 1500000
       OR (SELECT count(*) FROM wallet_transactions WHERE reference_type = 'COIN_TOP_UP'
           AND reference_id IN (SELECT payment_id FROM payment_transactions WHERE txn_ref LIKE 'V30COIN%')) <> 56
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'COIN_TOP_UP_RECEIVED:%'
           AND external_reference LIKE 'V30COIN%') <> 56 THEN
        RAISE EXCEPTION 'V30 postcondition failed; Coin top-up seed transaction is rolled back';
    END IF;
END $$;

DROP FUNCTION v30_seed_uuid(TEXT);
