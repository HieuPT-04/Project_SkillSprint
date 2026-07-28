-- These five legacy seed accounts were explicitly identified from the demo
-- user list. Use an exact email whitelist rather than a broad "missing avatar"
-- predicate so real accounts are never selected.

WITH visible_legacy_seed_users(email, avatar_object_key) AS (
    VALUES
        ('tan.ngo.98@gmail.com', 'seed-assets/avatars/identicon-v1/v27-legacy-001.png'),
        ('duongtuan.tanbackend@gmail.com', 'seed-assets/avatars/identicon-v1/v27-legacy-002.png'),
        ('nhi.pham.96@gmail.com', 'seed-assets/avatars/identicon-v1/v27-legacy-003.png'),
        ('phuc.2002201@gmail.com', 'seed-assets/avatars/identicon-v1/v27-legacy-004.png'),
        ('tuan.pham.design@gmail.com', 'seed-assets/avatars/identicon-v1/v27-legacy-005.png')
)
UPDATE users u
SET avatar_object_key = target.avatar_object_key,
    updated_at = GREATEST(u.updated_at, TIMESTAMPTZ '2026-07-28 12:00:00+07')
FROM visible_legacy_seed_users target
WHERE lower(u.email) = target.email;

DO $$
DECLARE
    v_assigned_count INTEGER;
BEGIN
    SELECT count(*)
    INTO v_assigned_count
    FROM users
    WHERE lower(email) IN (
        'tan.ngo.98@gmail.com',
        'duongtuan.tanbackend@gmail.com',
        'nhi.pham.96@gmail.com',
        'phuc.2002201@gmail.com',
        'tuan.pham.design@gmail.com'
    )
      AND avatar_object_key LIKE 'seed-assets/avatars/identicon-v1/v27-legacy-%.png';

    IF v_assigned_count <> 5 THEN
        RAISE EXCEPTION 'V50 expected avatars for the five explicitly whitelisted legacy seed users, found %', v_assigned_count;
    END IF;
END $$;
