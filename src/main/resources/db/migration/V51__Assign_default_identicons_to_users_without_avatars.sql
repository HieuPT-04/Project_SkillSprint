-- Give every account that has never uploaded an avatar a deterministic default
-- identicon. Existing S3 avatar keys are preserved without exception.

UPDATE users
SET avatar_object_key = 'default-identicon:' || user_id,
    updated_at = GREATEST(updated_at, TIMESTAMPTZ '2026-07-28 12:15:00+07')
WHERE avatar_object_key IS NULL
   OR btrim(avatar_object_key) = '';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE avatar_object_key IS NULL
           OR btrim(avatar_object_key) = ''
    ) THEN
        RAISE EXCEPTION 'V51 left at least one user without an avatar key';
    END IF;
END $$;
