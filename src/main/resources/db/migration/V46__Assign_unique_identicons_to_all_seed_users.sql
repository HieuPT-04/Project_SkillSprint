-- Give every user created by the V28 and V36 demo seeds a unique deterministic
-- identicon. V26 and V27 enrich existing records only; neither creates users.
-- Assets are generated and uploaded first by tools/SeedAvatarAssetUploader.java.
-- The target set is derived from both deterministic seed UUID schemes, so no
-- account outside those two cohorts can be selected.

CREATE FUNCTION v46_seed_uuid(namespace_prefix TEXT, seed TEXT)
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

WITH seed_users AS (
    SELECT v46_seed_uuid('skillsprint-v28', 'user:' || ordinal)::text AS user_id,
           format('seed-assets/avatars/identicon-v1/v28-%s.png', lpad(ordinal::text, 3, '0')) AS avatar_object_key
    FROM generate_series(1, 184) AS ordinal

    UNION ALL

    SELECT v46_seed_uuid('skillsprint-v36', 'user:' || ordinal)::text AS user_id,
           format('seed-assets/avatars/identicon-v1/v36-%s.png', lpad(ordinal::text, 3, '0')) AS avatar_object_key
    FROM generate_series(1, 100) AS ordinal
)
UPDATE users u
SET avatar_object_key = seed_users.avatar_object_key,
    updated_at = GREATEST(u.updated_at, TIMESTAMPTZ '2026-07-28 11:15:00+07')
FROM seed_users
WHERE u.user_id = seed_users.user_id;

DO $$
DECLARE
    v_seeded_avatar_count INTEGER;
    v_real_user_touched_count INTEGER;
BEGIN
    SELECT count(*)
    INTO v_seeded_avatar_count
    FROM users u
    WHERE u.avatar_object_key LIKE 'seed-assets/avatars/identicon-v1/%'
      AND u.user_id IN (
          SELECT v46_seed_uuid('skillsprint-v28', 'user:' || ordinal)::text
          FROM generate_series(1, 184) AS ordinal
          UNION ALL
          SELECT v46_seed_uuid('skillsprint-v36', 'user:' || ordinal)::text
          FROM generate_series(1, 100) AS ordinal
      );

    IF v_seeded_avatar_count <> 284 THEN
        RAISE EXCEPTION 'V46 expected unique identicons for 284 seed users, found %', v_seeded_avatar_count;
    END IF;

    SELECT count(*)
    INTO v_real_user_touched_count
    FROM users u
    WHERE u.avatar_object_key LIKE 'seed-assets/avatars/identicon-v1/%'
      AND u.user_id NOT IN (
          SELECT v46_seed_uuid('skillsprint-v28', 'user:' || ordinal)::text
          FROM generate_series(1, 184) AS ordinal
          UNION ALL
          SELECT v46_seed_uuid('skillsprint-v36', 'user:' || ordinal)::text
          FROM generate_series(1, 100) AS ordinal
      );

    IF v_real_user_touched_count <> 0 THEN
        RAISE EXCEPTION 'V46 assigned a seed identicon to % non-seed users', v_real_user_touched_count;
    END IF;
END $$;

DROP FUNCTION v46_seed_uuid(TEXT, TEXT);
