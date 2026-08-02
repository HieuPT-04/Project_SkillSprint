-- V66 seeded marketplace pack versions using SUBMITTED and UNDER_REVIEW, but
-- MarketplacePackVersionStatus persists PENDING_REVIEW for both moderation
-- states. Hibernate cannot deserialize the unsupported values when an admin
-- report loads its referenced version, causing the report queue to return 500.
-- Limit the correction to V66's deterministic rows; never touch user-created
-- marketplace content.

CREATE FUNCTION v67_v66_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v66:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v66:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v66:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v66:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v66:' || seed), 21, 12)
    )::uuid;
$$;

UPDATE marketplace_pack_versions version
SET status = 'PENDING_REVIEW',
    updated_at = CURRENT_TIMESTAMP
WHERE version.version_id IN (
    SELECT v67_v66_uuid('version:' || seed.row_no)
    FROM generate_series(1, 4) AS seed(row_no)
)
  AND version.status IN ('SUBMITTED', 'UNDER_REVIEW');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM marketplace_pack_versions version
        WHERE version.version_id IN (
            SELECT v67_v66_uuid('version:' || seed.row_no)
            FROM generate_series(1, 4) AS seed(row_no)
        )
          AND version.status IN ('SUBMITTED', 'UNDER_REVIEW')
    ) THEN
        RAISE EXCEPTION 'V67 failed to normalize V66 marketplace moderation status values';
    END IF;

    IF (
        SELECT count(*)
        FROM marketplace_pack_versions version
        WHERE version.version_id IN (
            SELECT v67_v66_uuid('version:' || seed.row_no)
            FROM generate_series(1, 4) AS seed(row_no)
        )
          AND version.status = 'PENDING_REVIEW'
    ) <> 4 THEN
        RAISE EXCEPTION 'V67 expected exactly four V66 versions in PENDING_REVIEW';
    END IF;
END $$;

DROP FUNCTION v67_v66_uuid(TEXT);
