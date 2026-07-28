-- Assign generated avatar assets only to the isolated V28 and V36 demo cohorts.
-- The four private PNGs must be uploaded first by tools/SeedAvatarAssetUploader.java.
-- No existing or real user is selected by this migration.

CREATE FUNCTION v42_seed_uuid(namespace_prefix TEXT, seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5(namespace_prefix || ':' || seed), 1, 8) || '-' ||
        substr(md5(namespace_prefix || ':' || seed), 9, 4) || '-' ||
        substr(md5(namespace_prefix || ':' || seed), 13, 4) || '-' ||
        substr(md5(namespace_prefix || ':' || seed), 17, 4) || '-' ||
        substr(md5(namespace_prefix || ':' || seed), 21, 12)
    )::uuid;
$$;

WITH seeded_users AS (
    SELECT v42_seed_uuid('skillsprint-v28', 'user:' || row_number)::text AS user_id,
           row_number AS avatar_number
    FROM generate_series(1, 184) AS row_number

    UNION ALL

    SELECT v42_seed_uuid('skillsprint-v36', 'user:' || row_number)::text AS user_id,
           184 + row_number AS avatar_number
    FROM generate_series(1, 100) AS row_number
),
expected AS (
    SELECT user_id,
           'seed-assets/avatars/avatar-' ||
               lpad((((avatar_number - 1) % 4) + 1)::text, 2, '0') || '.png' AS avatar_object_key
    FROM seeded_users
)
UPDATE users u
SET avatar_object_key = expected.avatar_object_key,
    updated_at = GREATEST(u.updated_at, TIMESTAMPTZ '2026-07-28 10:30:00+07')
FROM expected
WHERE u.user_id = expected.user_id;

DO $$
DECLARE
    v_seeded_avatar_count INTEGER;
    v_real_user_touched_count INTEGER;
BEGIN
    SELECT count(*)
    INTO v_seeded_avatar_count
    FROM users u
    WHERE u.avatar_object_key LIKE 'seed-assets/avatars/avatar-%.png'
      AND u.user_id IN (
          SELECT v42_seed_uuid('skillsprint-v28', 'user:' || row_number)::text
          FROM generate_series(1, 184) AS row_number
          UNION ALL
          SELECT v42_seed_uuid('skillsprint-v36', 'user:' || row_number)::text
          FROM generate_series(1, 100) AS row_number
      );

    IF v_seeded_avatar_count <> 284 THEN
        RAISE EXCEPTION 'V42 expected avatars for exactly 284 seed users, found %', v_seeded_avatar_count;
    END IF;

    SELECT count(*)
    INTO v_real_user_touched_count
    FROM users u
    WHERE u.avatar_object_key LIKE 'seed-assets/avatars/avatar-%.png'
      AND u.user_id NOT IN (
          SELECT v42_seed_uuid('skillsprint-v28', 'user:' || row_number)::text
          FROM generate_series(1, 184) AS row_number
          UNION ALL
          SELECT v42_seed_uuid('skillsprint-v36', 'user:' || row_number)::text
          FROM generate_series(1, 100) AS row_number
      );

    IF v_real_user_touched_count <> 0 THEN
        RAISE EXCEPTION 'V42 assigned a seed avatar to % non-seed users', v_real_user_touched_count;
    END IF;
END $$;
