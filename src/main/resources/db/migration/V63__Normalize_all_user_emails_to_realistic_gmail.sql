-- Normalize all user email addresses in the database to 100% realistic Vietnamese @gmail.com addresses.
-- Replaces synthetic demo/seed email patterns (v28.demo..., august.user..., @skillsprint.invalid, etc.)
-- with realistic, natural Vietnamese Gmail accounts matching user full names.

CREATE FUNCTION v63_v28_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v63_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v62:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v62:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v62:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v62:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v62:' || seed), 21, 12))::uuid;
$$;

DO $$
BEGIN
    -- 1. Update V28 demo users (184 users) to realistic Vietnamese Gmail addresses
    WITH v28_expected AS (
        SELECT v63_v28_uuid('user:' || n)::text AS user_id,
               (ARRAY['nguyen.minh.anh', 'tran.gia.bao', 'le.hoang.nam', 'pham.khanh.linh',
                       'vu.duc.huy', 'do.ngoc.mai', 'bui.quang.hung', 'ho.thanh.ha',
                       'ngo.nhat.minh', 'duong.thao.vy', 'phan.quoc.bao', 'huynh.gia.han',
                       'dang.minh.khang', 'ly.phuong.anh', 'doan.tuan.kiet', 'cao.bich.ngoc'])[((n - 1) % 16) + 1]
               || '.' || lpad(n::text, 3, '0') || '@gmail.com' AS email
        FROM generate_series(1, 184) AS n
    )
    UPDATE users u
    SET email = v28_expected.email,
        updated_at = CURRENT_TIMESTAMP
    FROM v28_expected
    WHERE u.user_id = v28_expected.user_id;

    -- 2. Update V62 August users (6 users) to realistic Vietnamese Gmail addresses
    WITH v62_expected AS (
        SELECT v63_uuid('new-user:' || n)::text AS user_id,
               (ARRAY[
                   'tuan.phamminh94@gmail.com',
                   'ha.nguyenthu96@gmail.com',
                   'long.tranhoang97@gmail.com',
                   'linh.lekhanh99@gmail.com',
                   'nam.dangbao98@gmail.com',
                   'yen.hoanghai95@gmail.com'
               ])[n] AS email
        FROM generate_series(1, 6) AS n
    )
    UPDATE users u
    SET email = v62_expected.email,
        updated_at = CURRENT_TIMESTAMP
    FROM v62_expected
    WHERE u.user_id = v62_expected.user_id;

    -- 3. Catch-all: Update any remaining non-@gmail.com or demo/synthetic emails to clean @gmail.com
    UPDATE users u
    SET email = 'user.' || SUBSTRING(REPLACE(u.user_id, '-', ''), 1, 8) || '@gmail.com',
        updated_at = CURRENT_TIMESTAMP
    WHERE u.email NOT LIKE '%@gmail.com'
       OR u.email ILIKE '%demo%'
       OR u.email ILIKE '%skillsprint%'
       OR u.email ILIKE '%invalid%'
       OR u.email ILIKE '%example%';

    -- 4. Postcondition Assertion: Ensure zero non-@gmail.com or fake/demo email domains remain
    IF EXISTS (
        SELECT 1 FROM users
        WHERE email NOT LIKE '%@gmail.com'
           OR email ILIKE '%v28.demo%'
           OR email ILIKE '%august.user%'
           OR email ILIKE '%skillsprint%'
           OR email ILIKE '%invalid%'
           OR email ILIKE '%example%'
    ) THEN
        RAISE EXCEPTION 'V63 postcondition failed; non-gmail or synthetic email addresses still exist in users table';
    END IF;
END $$;

DROP FUNCTION v63_uuid(TEXT);
DROP FUNCTION v63_v28_uuid(TEXT);
