-- Populate the dynamic "This week" leaderboard with learning-only XP.
-- The week is resolved in Asia/Ho_Chi_Minh at migration runtime so a demo
-- deployed in a later week never displays an empty weekly leaderboard.

CREATE FUNCTION v45_seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v45:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v45:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v45:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v45:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v45:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v45_v36_uuid(seed TEXT)
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

CREATE FUNCTION v45_v37_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v37:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v37:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v37:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v37:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v37:' || seed), 21, 12)
    )::uuid;
$$;

DO $$
DECLARE
    v_today DATE := (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date;
    v_week_start DATE;
    v_month_start DATE;
    v_days_available INTEGER;
    v_rank INTEGER;
    v_event_no INTEGER;
    v_event_count INTEGER;
    v_user_row INTEGER;
    v_user_id VARCHAR(100);
    v_workspace_id UUID;
    v_event_date DATE;
    v_event_at TIMESTAMPTZ;
    v_points INTEGER;
    v_event_type TEXT;
    v_source_type TEXT;
    v_description TEXT;
BEGIN
    v_week_start := date_trunc('week', v_today::timestamp)::date;
    v_month_start := date_trunc('month', v_today::timestamp)::date;
    v_days_available := (v_today - v_week_start) + 1;

    IF EXISTS (SELECT 1 FROM point_events WHERE source_id LIKE 'v45-weekly-%') THEN
        RAISE EXCEPTION 'V45 weekly XP events already exist; do not apply to a partially seeded database';
    END IF;

    IF (SELECT count(*) FROM user_point_summaries WHERE user_id IN (
            SELECT v45_v36_uuid('user:' || n)::text FROM generate_series(1, 100) AS n
        )) <> 100 THEN
        RAISE EXCEPTION 'V45 requires V37 point summaries for all V36 learners';
    END IF;

    FOR v_rank IN 1..15 LOOP
        v_user_row := ((v_rank * 17 - 1) % 100) + 1;
        v_user_id := v45_v36_uuid('user:' || v_user_row)::text;
        v_workspace_id := v45_v37_uuid('workspace:' || v_user_row);
        v_event_count := 3 + ((16 - v_rank) / 3);

        FOR v_event_no IN 1..v_event_count LOOP
            v_event_date := v_week_start + ((v_event_no + v_rank - 2) % v_days_available);
            v_event_at := (v_event_date::timestamp + TIME '08:00:00'
                + ((v_rank * 19 + v_event_no * 23) % 132) * INTERVAL '5 minutes')
                AT TIME ZONE 'Asia/Ho_Chi_Minh';

            IF v_event_no % 5 = 0 THEN
                v_points := 700;
                v_event_type := 'ROADMAP_COMPLETED';
                v_source_type := 'ROADMAP';
                v_description := 'Hoàn thành một roadmap trong tuần này';
            ELSIF v_event_no % 3 = 0 THEN
                v_points := 120;
                v_event_type := 'QUIZ_EXCELLENT';
                v_source_type := 'QUIZ';
                v_description := 'Đạt điểm xuất sắc trong quiz tuần này';
            ELSIF v_event_no % 2 = 0 THEN
                v_points := 80;
                v_event_type := 'QUIZ_PASSED';
                v_source_type := 'QUIZ';
                v_description := 'Hoàn thành quiz tuần này';
            ELSE
                v_points := 120;
                v_event_type := 'ROADMAP_STEP_COMPLETED';
                v_source_type := 'ROADMAP_STEP';
                v_description := 'Hoàn thành roadmap step tuần này';
            END IF;

            INSERT INTO point_events (
                point_event_id, user_id, workspace_id, event_type, source_type, source_id,
                points, description, event_date, week_start_date, month_start_date, created_at, updated_at
            ) VALUES (
                v45_seed_uuid(format('event:%s:%s', v_rank, v_event_no)),
                v_user_id, v_workspace_id, v_event_type, v_source_type,
                format('v45-weekly-%s-%s', v_rank, v_event_no),
                v_points, v_description, v_event_date, v_week_start, v_month_start, v_event_at, v_event_at
            );
        END LOOP;
    END LOOP;

    UPDATE user_point_summaries summary
    SET total_points = COALESCE((
            SELECT sum(event.points) FROM point_events event WHERE event.user_id = summary.user_id
        ), 0),
        current_week_points = COALESCE((
            SELECT sum(event.points) FROM point_events event
            WHERE event.user_id = summary.user_id AND event.week_start_date = v_week_start
        ), 0),
        current_week_start_date = v_week_start,
        current_month_points = COALESCE((
            SELECT sum(event.points) FROM point_events event
            WHERE event.user_id = summary.user_id AND event.month_start_date = v_month_start
        ), 0),
        current_month_start_date = v_month_start,
        streak_days = GREATEST(summary.streak_days, LEAST(v_days_available, 7)),
        last_point_date = GREATEST(summary.last_point_date, v_today),
        updated_at = CURRENT_TIMESTAMP
    WHERE summary.user_id IN (
        SELECT v45_v36_uuid('user:' || (((rank * 17 - 1) % 100) + 1))::text
        FROM generate_series(1, 15) AS rank
    );

    IF (SELECT count(DISTINCT user_id) FROM point_events WHERE source_id LIKE 'v45-weekly-%') <> 15
       OR (SELECT count(*) FROM point_events WHERE source_id LIKE 'v45-weekly-%'
           AND week_start_date = v_week_start) < 45
       OR EXISTS (
            SELECT 1 FROM point_events
            WHERE source_id LIKE 'v45-weekly-%'
              AND (event_type NOT IN ('ROADMAP_STEP_COMPLETED', 'ROADMAP_COMPLETED', 'QUIZ_PASSED', 'QUIZ_EXCELLENT')
                   OR source_type NOT IN ('ROADMAP_STEP', 'ROADMAP', 'QUIZ'))
       )
       OR (SELECT count(*) FROM user_point_summaries
           WHERE user_id IN (
               SELECT v45_v36_uuid('user:' || (((rank * 17 - 1) % 100) + 1))::text
               FROM generate_series(1, 15) AS rank
           )
             AND current_week_start_date = v_week_start
             AND current_week_points > 0) <> 15 THEN
        RAISE EXCEPTION 'V45 postcondition failed; weekly learning leaderboard seed is rolled back';
    END IF;
END $$;

DROP FUNCTION v45_v37_uuid(TEXT);
DROP FUNCTION v45_v36_uuid(TEXT);
DROP FUNCTION v45_seed_uuid(TEXT);
