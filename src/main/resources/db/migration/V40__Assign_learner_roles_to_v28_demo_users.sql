-- The V28 demo cohort was created directly in users and therefore bypassed the
-- normal UserSyncService role assignment. Give every non-admin V28 demo account
-- its global LEARNER role so the Admin user-summary tabs reconcile with total users.

CREATE FUNCTION v40_v28_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v40_seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v40:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v40:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v40:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v40:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v40:' || seed), 21, 12)
    )::uuid;
$$;

DO $$
DECLARE
    v_learner_role UUID;
BEGIN
    SELECT role_id INTO v_learner_role
    FROM roles
    WHERE role_name = 'LEARNER'
    LIMIT 1;

    IF v_learner_role IS NULL THEN
        RAISE EXCEPTION 'V40 requires the LEARNER role';
    END IF;

    INSERT INTO user_roles (user_role_id, user_id, role_id, granted_at)
    SELECT
        v40_seed_uuid('learner-role:' || seed.ordinal),
        v40_v28_uuid('user:' || seed.ordinal)::text,
        v_learner_role,
        user_row.created_at
    FROM generate_series(1, 184) AS seed(ordinal)
    JOIN users user_row ON user_row.user_id = v40_v28_uuid('user:' || seed.ordinal)::text
    WHERE NOT EXISTS (
        SELECT 1
        FROM user_roles existing_role
        WHERE existing_role.user_id = user_row.user_id
          AND existing_role.workspace_id IS NULL
    );

    IF (SELECT count(*) FROM user_roles user_role
        WHERE user_role.user_id IN (
            SELECT v40_v28_uuid('user:' || n)::text FROM generate_series(1, 184) AS n
        )
          AND user_role.role_id = v_learner_role
          AND user_role.workspace_id IS NULL) <> 184 THEN
        RAISE EXCEPTION 'V40 postcondition failed; V28 demo users are missing global LEARNER roles';
    END IF;
END $$;

DROP FUNCTION v40_seed_uuid(TEXT);
DROP FUNCTION v40_v28_uuid(TEXT);
