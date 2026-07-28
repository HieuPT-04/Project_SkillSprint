-- V36 was already applied with technically named seed addresses such as
-- seed36.ho.hai-nam100@skillsprint.invalid. Keep the reserved .invalid domain
-- while making the addresses look like normal learner accounts in admin UI.

CREATE FUNCTION v44_v36_uuid(seed TEXT)
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

WITH seeded_users AS (
    SELECT
        v44_v36_uuid('user:' || row_number)::text AS user_id,
        (ARRAY['minh.anh','gia.huy','thao.nguyen','quoc.bao','khanh.linh','duc.minh','phuong.anh','tuan.kiet','ngoc.han','hai.nam'])[((row_number - 1) / 10) + 1]
            || '.' ||
        (ARRAY['nguyen','tran','le','pham','hoang','vo','dang','bui','do','ho'])[((row_number - 1) % 10) + 1]
            || lpad(row_number::text, 3, '0') || '@demo.skillsprint.invalid' AS email
    FROM generate_series(1, 100) AS row_number
)
UPDATE users u
SET email = seeded_users.email,
    updated_at = GREATEST(u.updated_at, TIMESTAMPTZ '2026-07-28 12:45:00+07')
FROM seeded_users
WHERE u.user_id = seeded_users.user_id;

DO $$
DECLARE
    v_human_readable_count INTEGER;
    v_legacy_address_count INTEGER;
BEGIN
    SELECT count(*)
    INTO v_human_readable_count
    FROM users u
    WHERE u.user_id IN (
        SELECT v44_v36_uuid('user:' || row_number)::text
        FROM generate_series(1, 100) AS row_number
    )
      AND u.email LIKE '%@demo.skillsprint.invalid';

    IF v_human_readable_count <> 100 THEN
        RAISE EXCEPTION 'V44 expected 100 human-readable V36 email addresses, found %', v_human_readable_count;
    END IF;

    SELECT count(*)
    INTO v_legacy_address_count
    FROM users u
    WHERE u.user_id IN (
        SELECT v44_v36_uuid('user:' || row_number)::text
        FROM generate_series(1, 100) AS row_number
    )
      AND u.email LIKE 'seed36.%@skillsprint.invalid';

    IF v_legacy_address_count <> 0 THEN
        RAISE EXCEPTION 'V44 found % legacy V36 email addresses', v_legacy_address_count;
    END IF;
END $$;

DROP FUNCTION v44_v36_uuid(TEXT);
