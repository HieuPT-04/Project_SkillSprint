-- Populate leaderboard-eligible Full Pack Challenge attempts. Each attempt is
-- backed by an existing ACTIVE entitlement and the ranked-definition snapshot,
-- so the marketplace leaderboard uses the same completed-attempt contract as
-- the application rather than synthetic aggregate scores.

CREATE FUNCTION v41_seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v41:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v41:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v41:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v41:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v41:' || seed), 21, 12)
    )::uuid;
$$;

DO $$
DECLARE
    v_definition RECORD;
    v_rank INTEGER;
    v_buyer_id VARCHAR(100);
    v_questions JSONB;
    v_answers JSONB;
    v_submitted_answers JSONB;
    v_question_count INTEGER;
    v_correct_count INTEGER;
    v_score INTEGER;
    v_duration_seconds BIGINT;
    v_started_at TIMESTAMPTZ;
BEGIN
    CREATE TEMP TABLE v41_seed_definitions ON COMMIT DROP AS
    SELECT definition.definition_id,
           definition.pack_version_id,
           definition.total_question_count
    FROM marketplace_ranked_quiz_definitions definition
    WHERE EXISTS (
        SELECT 1
        FROM marketplace_entitlements entitlement
        WHERE entitlement.pack_version_id = definition.pack_version_id
          AND entitlement.status = 'ACTIVE'
    );

    IF NOT EXISTS (SELECT 1 FROM v41_seed_definitions) THEN
        RAISE EXCEPTION 'V41 requires at least one ranked Quiz Pack with an ACTIVE entitlement';
    END IF;

    FOR v_definition IN SELECT * FROM v41_seed_definitions LOOP
        v_question_count := v_definition.total_question_count;

        -- Build the exact safe-question and answer snapshot shape used when a
        -- learner starts a challenge, restricted to the saved ranked selection.
        SELECT jsonb_build_object('questions', jsonb_agg(jsonb_build_object(
            'questionId', question.value ->> 'questionId',
            'type', question.value ->> 'type',
            'text', question.value ->> 'text',
            'options', (
                SELECT jsonb_agg(jsonb_build_object(
                    'optionId', option.value ->> 'optionId',
                    'label', option.value ->> 'label',
                    'text', option.value ->> 'text'
                ) ORDER BY option.ordinality)
                FROM jsonb_array_elements(question.value -> 'options') WITH ORDINALITY option(value, ordinality)
            )
        ) ORDER BY selection.step_order, selection.selection_order))
        INTO v_questions
        FROM marketplace_ranked_question_selections selection
        JOIN marketplace_pack_versions pack_version ON pack_version.version_id = v_definition.pack_version_id
        CROSS JOIN LATERAL jsonb_array_elements(pack_version.content_json -> 'chapters') chapter(value)
        CROSS JOIN LATERAL jsonb_array_elements(chapter.value -> 'quiz' -> 'questions') question(value)
        WHERE selection.definition_id = v_definition.definition_id
          AND selection.question_id::text = question.value ->> 'questionId';

        SELECT jsonb_build_object('answers', jsonb_agg(jsonb_build_object(
            'questionId', question.value ->> 'questionId',
            'correctOptionId', option.value ->> 'optionId'
        ) ORDER BY selection.step_order, selection.selection_order))
        INTO v_answers
        FROM marketplace_ranked_question_selections selection
        JOIN marketplace_pack_versions pack_version ON pack_version.version_id = v_definition.pack_version_id
        CROSS JOIN LATERAL jsonb_array_elements(pack_version.content_json -> 'chapters') chapter(value)
        CROSS JOIN LATERAL jsonb_array_elements(chapter.value -> 'quiz' -> 'questions') question(value)
        CROSS JOIN LATERAL jsonb_array_elements(question.value -> 'options') option(value)
        WHERE selection.definition_id = v_definition.definition_id
          AND selection.question_id::text = question.value ->> 'questionId'
          AND (option.value ->> 'correct')::boolean;

        IF v_questions IS NULL
           OR v_answers IS NULL
           OR jsonb_array_length(v_questions -> 'questions') <> v_question_count
           OR jsonb_array_length(v_answers -> 'answers') <> v_question_count THEN
            RAISE EXCEPTION 'V41 cannot build a ranked snapshot for definition %', v_definition.definition_id;
        END IF;

        FOR v_rank IN 1..10 LOOP
            SELECT entitlement.buyer_id
            INTO v_buyer_id
            FROM marketplace_entitlements entitlement
            WHERE entitlement.pack_version_id = v_definition.pack_version_id
              AND entitlement.status = 'ACTIVE'
              AND NOT EXISTS (
                  SELECT 1
                  FROM marketplace_ranked_attempts attempt
                  WHERE attempt.buyer_id = entitlement.buyer_id
                    AND attempt.pack_version_id = v_definition.pack_version_id
                    AND attempt.leaderboard_eligible = TRUE
              )
            ORDER BY entitlement.granted_at, entitlement.buyer_id
            OFFSET 0
            LIMIT 1;

            EXIT WHEN v_buyer_id IS NULL;

            v_correct_count := LEAST(v_question_count, GREATEST(1, ROUND(
                v_question_count * (ARRAY[100, 95, 95, 90, 90, 85, 85, 80, 80, 75])[v_rank] / 100.0
            )::integer));
            v_score := ROUND(v_correct_count * 100.0 / v_question_count)::integer;
            v_duration_seconds := (ARRAY[314, 357, 401, 446, 492, 538, 584, 641, 705, 782])[v_rank]
                + ((get_byte(decode(md5(v_buyer_id), 'hex'), 0) % 37)::bigint);
            v_started_at := TIMESTAMPTZ '2026-07-27 19:10:00+07'
                - ((v_rank - 1) * INTERVAL '7 hours 20 minutes')
                - ((get_byte(decode(md5(v_buyer_id), 'hex'), 1) % 83) * INTERVAL '1 minute');

            WITH selected_questions AS (
                SELECT selection.step_order,
                       selection.selection_order,
                       question.value AS question_json,
                       row_number() OVER (ORDER BY selection.step_order, selection.selection_order) AS ordinal
                FROM marketplace_ranked_question_selections selection
                JOIN marketplace_pack_versions pack_version ON pack_version.version_id = v_definition.pack_version_id
                CROSS JOIN LATERAL jsonb_array_elements(pack_version.content_json -> 'chapters') chapter(value)
                CROSS JOIN LATERAL jsonb_array_elements(chapter.value -> 'quiz' -> 'questions') question(value)
                WHERE selection.definition_id = v_definition.definition_id
                  AND selection.question_id::text = question.value ->> 'questionId'
            )
            SELECT jsonb_build_object('answers', jsonb_agg(jsonb_build_object(
                'questionId', selected.question_json ->> 'questionId',
                'optionId', (
                    SELECT option.value ->> 'optionId'
                    FROM jsonb_array_elements(selected.question_json -> 'options') option(value)
                    WHERE CASE
                        WHEN selected.ordinal <= v_correct_count THEN (option.value ->> 'correct')::boolean
                        ELSE NOT (option.value ->> 'correct')::boolean
                    END
                    ORDER BY option.value ->> 'optionId'
                    LIMIT 1
                )
            ) ORDER BY selected.ordinal))
            INTO v_submitted_answers
            FROM selected_questions selected;

            INSERT INTO marketplace_ranked_attempts (
                attempt_id, buyer_id, pack_version_id, definition_id, attempt_date, attempt_number, status,
                started_at, expires_at, completed_at, question_snapshot_json, answer_snapshot_json,
                submitted_answers_json, idempotency_key, request_fingerprint, score, correct_count,
                duration_seconds, suspicious, leaderboard_eligible, created_at, updated_at
            ) VALUES (
                v41_seed_uuid(format('attempt:%s:%s', v_definition.definition_id, v_rank)),
                v_buyer_id, v_definition.pack_version_id, v_definition.definition_id,
                DATE '2026-07-27' - ((v_rank - 1) / 2), 100 + v_rank, 'COMPLETED',
                v_started_at, v_started_at + INTERVAL '1 hour', v_started_at + (v_duration_seconds * INTERVAL '1 second'),
                v_questions, v_answers, v_submitted_answers,
                v41_seed_uuid(format('attempt-key:%s:%s', v_definition.definition_id, v_rank)),
                md5('v41-ranked:' || v_definition.definition_id || ':' || v_rank)
                    || md5('v41-ranked-fingerprint:' || v_definition.definition_id || ':' || v_rank),
                v_score, v_correct_count, v_duration_seconds, FALSE, TRUE,
                v_started_at, v_started_at + (v_duration_seconds * INTERVAL '1 second')
            );
        END LOOP;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM v41_seed_definitions definition
        WHERE (SELECT count(DISTINCT entitlement.buyer_id)
               FROM marketplace_entitlements entitlement
               WHERE entitlement.pack_version_id = definition.pack_version_id
                 AND entitlement.status = 'ACTIVE') >= 10
          AND (SELECT count(*)
               FROM marketplace_ranked_attempts attempt
               WHERE attempt.pack_version_id = definition.pack_version_id
                 AND attempt.status = 'COMPLETED'
                 AND attempt.suspicious = FALSE
                 AND attempt.leaderboard_eligible = TRUE) < 10
    )
       OR EXISTS (
            SELECT 1
            FROM marketplace_ranked_attempts attempt
            WHERE attempt.attempt_id IN (
                SELECT v41_seed_uuid(format('attempt:%s:%s', definition.definition_id, rank.ordinal))
                FROM v41_seed_definitions definition
                CROSS JOIN generate_series(1, 10) AS rank(ordinal)
            )
              AND (attempt.status <> 'COMPLETED'
                   OR attempt.suspicious = TRUE
                   OR attempt.leaderboard_eligible = FALSE)
       ) THEN
        RAISE EXCEPTION 'V41 postcondition failed; Quiz Pack leaderboard seed is rolled back';
    END IF;
END $$;

DROP FUNCTION v41_seed_uuid(TEXT);
