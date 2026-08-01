-- Keep the demo-user list visually consistent in the admin UI.  Only the
-- deterministic V28/V36 seed cohorts are touched; real users are never
-- selected by an email-domain predicate.

CREATE FUNCTION v58_v28_uuid(seed TEXT)
RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (
        substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v58_v36_uuid(seed TEXT)
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
    v_seed_user_count INTEGER;
    v_collision_count INTEGER;
BEGIN
    WITH expected_emails AS (
        SELECT
            v58_v28_uuid('user:' || row_number)::text AS user_id,
            'v28.demo.' || lpad(row_number::text, 3, '0') || '@gmail.com' AS email
        FROM generate_series(1, 184) AS row_number

        UNION ALL

        SELECT
            v58_v36_uuid('user:' || row_number)::text AS user_id,
            (ARRAY['minh.anh','gia.huy','thao.nguyen','quoc.bao','khanh.linh','duc.minh','phuong.anh','tuan.kiet','ngoc.han','hai.nam'])[((row_number - 1) / 10) + 1]
                || '.' ||
            (ARRAY['nguyen','tran','le','pham','hoang','vo','dang','bui','do','ho'])[((row_number - 1) % 10) + 1]
                || lpad(row_number::text, 3, '0') || '@gmail.com' AS email
        FROM generate_series(1, 100) AS row_number
    )
    SELECT count(*) INTO v_seed_user_count
    FROM users u
    JOIN expected_emails expected ON expected.user_id = u.user_id;

    IF v_seed_user_count <> 284 THEN
        RAISE EXCEPTION 'V58 requires all 284 V28/V36 demo users, found %', v_seed_user_count;
    END IF;

    WITH expected_emails AS (
        SELECT v58_v28_uuid('user:' || row_number)::text AS user_id,
               'v28.demo.' || lpad(row_number::text, 3, '0') || '@gmail.com' AS email
        FROM generate_series(1, 184) AS row_number
        UNION ALL
        SELECT v58_v36_uuid('user:' || row_number)::text AS user_id,
               (ARRAY['minh.anh','gia.huy','thao.nguyen','quoc.bao','khanh.linh','duc.minh','phuong.anh','tuan.kiet','ngoc.han','hai.nam'])[((row_number - 1) / 10) + 1]
                   || '.' ||
               (ARRAY['nguyen','tran','le','pham','hoang','vo','dang','bui','do','ho'])[((row_number - 1) % 10) + 1]
                   || lpad(row_number::text, 3, '0') || '@gmail.com' AS email
        FROM generate_series(1, 100) AS row_number
    )
    SELECT count(*) INTO v_collision_count
    FROM expected_emails expected
    JOIN users u ON lower(u.email) = lower(expected.email)
        AND u.user_id <> expected.user_id;

    IF v_collision_count <> 0 THEN
        RAISE EXCEPTION 'V58 would overwrite % existing non-demo Gmail address(es)', v_collision_count;
    END IF;

    WITH expected_emails AS (
        SELECT v58_v28_uuid('user:' || row_number)::text AS user_id,
               'v28.demo.' || lpad(row_number::text, 3, '0') || '@gmail.com' AS email
        FROM generate_series(1, 184) AS row_number
        UNION ALL
        SELECT v58_v36_uuid('user:' || row_number)::text AS user_id,
               (ARRAY['minh.anh','gia.huy','thao.nguyen','quoc.bao','khanh.linh','duc.minh','phuong.anh','tuan.kiet','ngoc.han','hai.nam'])[((row_number - 1) / 10) + 1]
                   || '.' ||
               (ARRAY['nguyen','tran','le','pham','hoang','vo','dang','bui','do','ho'])[((row_number - 1) % 10) + 1]
                   || lpad(row_number::text, 3, '0') || '@gmail.com' AS email
        FROM generate_series(1, 100) AS row_number
    )
    UPDATE users u
    SET email = expected.email,
        updated_at = GREATEST(u.updated_at, CURRENT_TIMESTAMP)
    FROM expected_emails expected
    WHERE u.user_id = expected.user_id
      AND u.email IS DISTINCT FROM expected.email;

    IF (SELECT count(*)
        FROM users
        WHERE user_id IN (
            SELECT v58_v28_uuid('user:' || row_number)::text FROM generate_series(1, 184) AS row_number
            UNION ALL
            SELECT v58_v36_uuid('user:' || row_number)::text FROM generate_series(1, 100) AS row_number
        )
          AND email LIKE '%@gmail.com') <> 284 THEN
        RAISE EXCEPTION 'V58 postcondition failed: not every demo account has a Gmail address';
    END IF;
END $$;

DROP FUNCTION v58_v36_uuid(TEXT);
DROP FUNCTION v58_v28_uuid(TEXT);
