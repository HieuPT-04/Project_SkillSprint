-- Normalize all user email addresses in the database to 100% realistic personal @gmail.com addresses.
-- Replaces synthetic/demo/technical patterns (v28.demo..., august.user..., .001, .002, user.a1b2c3d4, @skillsprint.invalid, etc.)
-- with realistic, natural Vietnamese personal Gmail accounts matching user full names.

CREATE FUNCTION v63_v28_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v28:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v63_v36_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 21, 12))::uuid;
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
    -- 1. Update V28 demo users (184 users) to 100% natural, human-looking Gmail addresses
    WITH v28_expected AS (
        SELECT v63_v28_uuid('user:' || n)::text AS user_id,
               (ARRAY['nguyen.minh.anh', 'tran.gia.bao', 'le.hoang.nam', 'pham.khanh.linh',
                       'vu.duc.huy', 'do.ngoc.mai', 'bui.quang.hung', 'ho.thanh.ha',
                       'ngo.nhat.minh', 'duong.thao.vy', 'phan.quoc.bao', 'huynh.gia.han',
                       'dang.minh.khang', 'ly.phuong.anh', 'doan.tuan.kiet', 'cao.bich.ngoc'])[((n - 1) % 16) + 1]
               || CASE WHEN ((n - 1) / 16) = 0 THEN ''
                       ELSE '.' || (ARRAY['95', '98', '97', '99', '94', '2001', 'dev', '96', 'work', '2000', 'tech', 'design', '98', '2002', 'fe', 'be'])[((n - 1) / 16)]
                  END || '@gmail.com' AS email
        FROM generate_series(1, 184) AS n
    )
    UPDATE users u
    SET email = v28_expected.email,
        updated_at = CURRENT_TIMESTAMP
    FROM v28_expected
    WHERE u.user_id = v28_expected.user_id;

    -- 2. Update V36 users (100 users) to natural personal Gmail addresses (removing .001, .002, etc.)
    WITH v36_expected AS (
        SELECT v63_v36_uuid('user:' || n)::text AS user_id,
               (ARRAY['minh.anh','gia.huy','thao.nguyen','quoc.bao','khanh.linh','duc.minh','phuong.anh','tuan.kiet','ngoc.han','hai.nam'])[((n - 1) / 10) + 1]
               || '.' ||
               (ARRAY['nguyen','tran','le','pham','hoang','vo','dang','bui','do','ho'])[((n - 1) % 10) + 1]
               || CASE WHEN (n % 4) = 0 THEN '.98@gmail.com'
                       WHEN (n % 4) = 1 THEN '.95@gmail.com'
                       WHEN (n % 4) = 2 THEN '.99@gmail.com'
                       ELSE '.2001@gmail.com'
                  END AS email
        FROM generate_series(1, 100) AS n
    )
    UPDATE users u
    SET email = v36_expected.email,
        updated_at = CURRENT_TIMESTAMP
    FROM v36_expected
    WHERE u.user_id = v36_expected.user_id;

    -- 3. Update V62 August users (6 users) to realistic personal Vietnamese Gmail addresses
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

    -- 4. Catch-all: Ensure any remaining synthetic/demo emails are updated to realistic handles
    UPDATE users u
    SET email = 'user.' || SUBSTRING(REPLACE(u.user_id, '-', ''), 1, 6) || '@gmail.com',
        updated_at = CURRENT_TIMESTAMP
    WHERE u.email NOT LIKE '%@gmail.com'
       OR u.email ILIKE '%demo%'
       OR u.email ILIKE '%august.user%'
       OR u.email ILIKE '%skillsprint%'
       OR u.email ILIKE '%invalid%'
       OR u.email ILIKE '%example%';

    -- 5. Postcondition Assertion: Ensure zero synthetic or technical email handles exist in users table
    IF EXISTS (
        SELECT 1 FROM users
        WHERE email NOT LIKE '%@gmail.com'
           OR email ILIKE '%v28.demo%'
           OR email ILIKE '%august.user%'
           OR email ILIKE '%skillsprint%'
           OR email ILIKE '%invalid%'
           OR email ILIKE '%example%'
           OR email ~ '\.[0-9]{3}@gmail\.com'
    ) THEN
        RAISE EXCEPTION 'V63 postcondition failed; synthetic or technical email patterns still exist in users table';
    END IF;
END $$;

DROP FUNCTION v63_uuid(TEXT);
DROP FUNCTION v63_v36_uuid(TEXT);
DROP FUNCTION v63_v28_uuid(TEXT);
