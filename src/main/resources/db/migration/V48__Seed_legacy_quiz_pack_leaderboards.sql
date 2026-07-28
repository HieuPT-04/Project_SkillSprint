-- The item-level Full Pack Challenge endpoint reads marketplace_quiz_attempts,
-- while V41 seeds the newer version-level ranked-attempt endpoint. Seed this
-- legacy read model as well so the Quiz Pack detail leaderboard is populated.
-- Targets are only the three deterministic V28 demo packs surfaced by V43.

CREATE FUNCTION v48_seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v48:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v48:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v48:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v48:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v48:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v48_v28_uuid(seed TEXT)
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

DO $$
DECLARE
    v_target RECORD;
    v_rank INTEGER;
    v_buyer_id VARCHAR(100);
    v_score INTEGER;
    v_correct_count INTEGER;
    v_duration_seconds BIGINT;
    v_completed_at TIMESTAMPTZ;
BEGIN
    FOR v_target IN
        SELECT item.item_id, version.version_id
        FROM marketplace_items item
        JOIN marketplace_pack_versions version ON version.legacy_item_id = item.item_id
        WHERE item.item_id IN (
            v48_v28_uuid('item:3'),
            v48_v28_uuid('item:4'),
            v48_v28_uuid('item:5')
        )
          AND version.status = 'PUBLISHED'
          AND EXISTS (
              SELECT 1
              FROM marketplace_quiz_pack_snapshots snapshot
              WHERE snapshot.item_id = item.item_id
                AND snapshot.question_count = 20
          )
        ORDER BY item.item_id
    LOOP
        FOR v_rank IN 1..10 LOOP
            SELECT entitlement.buyer_id
            INTO v_buyer_id
            FROM marketplace_entitlements entitlement
            WHERE entitlement.pack_version_id = v_target.version_id
              AND entitlement.status = 'ACTIVE'
              AND NOT EXISTS (
                  SELECT 1
                  FROM marketplace_quiz_attempts existing_attempt
                  WHERE existing_attempt.item_id = v_target.item_id
                    AND existing_attempt.user_id = entitlement.buyer_id
                    AND existing_attempt.attempt_type = 'RANKED'
                    AND existing_attempt.suspicious = FALSE
              )
            ORDER BY entitlement.granted_at, entitlement.buyer_id
            LIMIT 1 OFFSET 0;

            IF v_buyer_id IS NULL THEN
                RAISE EXCEPTION 'V48 requires 10 unused active demo entitlements for Quiz Pack %', v_target.item_id;
            END IF;

            v_correct_count := (ARRAY[20, 19, 19, 18, 18, 17, 17, 16, 16, 15])[v_rank];
            v_score := ROUND(v_correct_count * 100.0 / 20)::integer;
            v_duration_seconds := (ARRAY[302, 338, 371, 419, 463, 518, 572, 638, 704, 781])[v_rank]
                + (get_byte(decode(md5(v_buyer_id), 'hex'), 0) % 29);
            v_completed_at := CURRENT_TIMESTAMP
                - ((v_rank - 1) * INTERVAL '5 hours 15 minutes')
                - ((get_byte(decode(md5(v_buyer_id), 'hex'), 1) % 47) * INTERVAL '1 minute');

            INSERT INTO marketplace_quiz_attempts (
                attempt_id, item_id, pack_version_id, user_id, attempt_type,
                score, correct_count, question_count, duration_seconds, suspicious,
                completed_at, created_at, updated_at
            ) VALUES (
                v48_seed_uuid(format('legacy-leaderboard:%s:%s', v_target.item_id, v_rank)),
                v_target.item_id, v_target.version_id, v_buyer_id, 'RANKED',
                v_score, v_correct_count, 20, v_duration_seconds, FALSE,
                v_completed_at, v_completed_at, v_completed_at
            ) ON CONFLICT (attempt_id) DO NOTHING;
        END LOOP;
    END LOOP;

    IF (SELECT count(*)
        FROM marketplace_items item
        WHERE item.item_id IN (
            v48_v28_uuid('item:3'),
            v48_v28_uuid('item:4'),
            v48_v28_uuid('item:5')
        )) <> 3 THEN
        RAISE EXCEPTION 'V48 expected the three V43 Quiz Pack items';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM marketplace_items item
        WHERE item.item_id IN (
            v48_v28_uuid('item:3'),
            v48_v28_uuid('item:4'),
            v48_v28_uuid('item:5')
        )
          AND (SELECT count(DISTINCT attempt.user_id)
               FROM marketplace_quiz_attempts attempt
               WHERE attempt.item_id = item.item_id
                 AND attempt.attempt_type = 'RANKED'
                 AND attempt.suspicious = FALSE) < 10
    ) THEN
        RAISE EXCEPTION 'V48 postcondition failed; legacy Quiz Pack leaderboard is incomplete';
    END IF;
END $$;

DROP FUNCTION v48_v28_uuid(TEXT);
DROP FUNCTION v48_seed_uuid(TEXT);
