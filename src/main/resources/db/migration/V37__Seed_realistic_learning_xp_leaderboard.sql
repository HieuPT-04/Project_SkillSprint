-- Populate the all-time leaderboard for the V36 learner cohort with XP earned
-- exclusively from learning activity. The distribution is intentionally
-- long-tailed: a handful of committed learners lead, while most accounts have
-- moderate or early-stage progress.

CREATE FUNCTION v37_seed_uuid(seed TEXT)
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

-- V36 user ids are deterministic and this helper lets V37 refer to the same
-- cohort after V36 has removed its temporary UUID function.
CREATE FUNCTION v37_v36_uuid(seed TEXT)
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

DO $$
DECLARE
    v_row INTEGER;
    v_activity_rank INTEGER;
    v_user_id VARCHAR(100);
    v_workspace_id UUID;
    v_registered_at TIMESTAMPTZ;
    v_registered_date DATE;
    v_event_date DATE;
    v_event_at TIMESTAMPTZ;
    v_event_no INTEGER;
    v_event_total INTEGER;
    v_step_count INTEGER;
    v_quiz_count INTEGER;
    v_roadmap_count INTEGER;
    v_points INTEGER;
    v_event_type TEXT;
    v_source_type TEXT;
    v_description TEXT;
    v_streak_days INTEGER;
BEGIN
    IF to_regclass('public.point_events') IS NULL
       OR to_regclass('public.user_point_summaries') IS NULL THEN
        RAISE EXCEPTION 'V37 requires point_events and user_point_summaries tables';
    END IF;

    IF EXISTS (SELECT 1 FROM point_events WHERE source_id LIKE 'v37-study-%') THEN
        RAISE EXCEPTION 'V37 learning XP events already exist; do not apply this migration to a partially seeded database';
    END IF;

    IF (SELECT count(*) FROM users WHERE user_id IN (
            SELECT v37_v36_uuid('user:' || n)::text FROM generate_series(1, 100) AS n
        )) <> 100 THEN
        RAISE EXCEPTION 'V37 requires all 100 V36 learner accounts';
    END IF;

    FOR v_row IN 1..100 LOOP
        v_user_id := v37_v36_uuid('user:' || v_row)::text;
        v_workspace_id := v37_seed_uuid('workspace:' || v_row);
        SELECT created_at INTO v_registered_at FROM users WHERE user_id = v_user_id;
        v_registered_date := (v_registered_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date;

        INSERT INTO study_workspaces (
            workspace_id, user_id, name, description, status, created_at, updated_at
        ) VALUES (
            v_workspace_id,
            v_user_id,
            (ARRAY['Java Backend','ReactJS & TypeScript','SQL thực chiến','Tiếng Anh giao tiếp',
                   'Data Structures','UI/UX Foundation','Spring Boot API','Business English',
                   'Python cơ bản','Kỹ năng phỏng vấn'])[((v_row - 1) % 10) + 1],
            'Lộ trình học cá nhân được dùng để ghi nhận tiến độ và XP.',
            'ACTIVE', v_registered_at + INTERVAL '10 minutes', v_registered_at + INTERVAL '10 minutes'
        );

        -- A stable shuffled rank avoids tying high XP mechanically to early registrations.
        v_activity_rank := (v_row * 37) % 101;
        v_roadmap_count := CASE
            WHEN v_activity_rank <= 3 THEN 6 - v_activity_rank
            WHEN v_activity_rank <= 12 THEN 3
            WHEN v_activity_rank <= 30 THEN 2
            WHEN v_activity_rank <= 60 THEN 1
            ELSE 0
        END;
        v_step_count := CASE
            WHEN v_activity_rank <= 3 THEN 18 - v_activity_rank
            WHEN v_activity_rank <= 12 THEN 8 + (v_row % 5)
            WHEN v_activity_rank <= 30 THEN 4 + (v_row % 4)
            WHEN v_activity_rank <= 60 THEN 2 + (v_row % 3)
            WHEN v_activity_rank <= 85 THEN 1 + (v_row % 2)
            ELSE 0
        END;
        v_quiz_count := CASE
            WHEN v_activity_rank <= 3 THEN 22 - v_activity_rank
            WHEN v_activity_rank <= 12 THEN 9 + (v_row % 5)
            WHEN v_activity_rank <= 30 THEN 5 + (v_row % 4)
            WHEN v_activity_rank <= 60 THEN 3 + (v_row % 3)
            WHEN v_activity_rank <= 85 THEN 2 + (v_row % 2)
            ELSE 1
        END;
        v_event_total := v_step_count + v_quiz_count + v_roadmap_count;

        FOR v_event_no IN 1..v_event_total LOOP
            v_event_date := v_registered_date + (
                v_event_no * (DATE '2026-07-27' - v_registered_date) / (v_event_total + 1)
            );
            v_event_at := (v_event_date::timestamp + INTERVAL '08:00:00'
                + ((v_event_no * 17 + v_row * 11) % 120) * INTERVAL '5 minutes')
                AT TIME ZONE 'Asia/Ho_Chi_Minh';

            IF v_event_no <= v_step_count THEN
                v_points := 120;
                v_event_type := 'ROADMAP_STEP_COMPLETED';
                v_source_type := 'ROADMAP_STEP';
                v_description := 'Hoàn thành roadmap step';
            ELSIF v_event_no <= v_step_count + v_quiz_count THEN
                v_source_type := 'QUIZ';
                IF (v_event_no + v_row) % 3 = 0 THEN
                    v_points := 120;
                    v_event_type := 'QUIZ_EXCELLENT';
                    v_description := 'Đạt điểm xuất sắc trong quiz';
                ELSE
                    v_points := 80;
                    v_event_type := 'QUIZ_PASSED';
                    v_description := 'Đạt điểm quiz';
                END IF;
            ELSE
                v_points := 700;
                v_event_type := 'ROADMAP_COMPLETED';
                v_source_type := 'ROADMAP';
                v_description := 'Hoàn thành toàn bộ roadmap';
            END IF;

            INSERT INTO point_events (
                point_event_id, user_id, workspace_id, event_type, source_type, source_id,
                points, description, event_date, week_start_date, month_start_date, created_at, updated_at
            ) VALUES (
                v37_seed_uuid(format('event:%s:%s', v_row, v_event_no)),
                v_user_id, v_workspace_id, v_event_type, v_source_type,
                'v37-study-' || v_row || '-' || v_event_no,
                v_points, v_description, v_event_date,
                date_trunc('week', v_event_date)::date, date_trunc('month', v_event_date)::date,
                v_event_at, v_event_at
            );
        END LOOP;

        v_streak_days := CASE
            WHEN v_activity_rank <= 3 THEN 10 + (v_row % 10)
            WHEN v_activity_rank <= 12 THEN 6 + (v_row % 6)
            WHEN v_activity_rank <= 30 THEN 3 + (v_row % 4)
            WHEN v_activity_rank <= 60 THEN 2 + (v_row % 3)
            ELSE 1
        END;

        INSERT INTO user_point_summaries (
            user_id, total_points, current_week_points, current_week_start_date,
            current_month_points, current_month_start_date, streak_days, last_point_date,
            created_at, updated_at
        )
        SELECT
            v_user_id,
            COALESCE(sum(event.points), 0),
            COALESCE(sum(event.points) FILTER (WHERE event.week_start_date = DATE '2026-07-27'), 0),
            DATE '2026-07-27',
            COALESCE(sum(event.points) FILTER (WHERE event.month_start_date = DATE '2026-07-01'), 0),
            DATE '2026-07-01',
            v_streak_days,
            max(event.event_date),
            v_registered_at,
            max(event.created_at)
        FROM point_events event
        WHERE event.user_id = v_user_id
          AND event.source_id LIKE 'v37-study-%';
    END LOOP;

    IF (SELECT count(*) FROM study_workspaces WHERE workspace_id IN (
            SELECT v37_seed_uuid('workspace:' || n) FROM generate_series(1, 100) AS n
        )) <> 100
       OR (SELECT count(*) FROM user_point_summaries WHERE user_id IN (
            SELECT v37_v36_uuid('user:' || n)::text FROM generate_series(1, 100) AS n
        ) AND total_points > 0) <> 100
       OR (SELECT count(DISTINCT user_id) FROM point_events WHERE source_id LIKE 'v37-study-%') <> 100
       OR EXISTS (
            SELECT 1
            FROM point_events
            WHERE source_id LIKE 'v37-study-%'
              AND (event_type NOT IN ('ROADMAP_STEP_COMPLETED', 'ROADMAP_COMPLETED', 'QUIZ_PASSED', 'QUIZ_EXCELLENT')
                   OR source_type NOT IN ('ROADMAP_STEP', 'ROADMAP', 'QUIZ'))
       )
       OR EXISTS (
            SELECT 1
            FROM user_point_summaries summary
            WHERE summary.user_id IN (
                SELECT v37_v36_uuid('user:' || n)::text FROM generate_series(1, 100) AS n
            )
              AND summary.total_points <> COALESCE((
                    SELECT sum(event.points)
                    FROM point_events event
                    WHERE event.user_id = summary.user_id
                      AND event.source_id LIKE 'v37-study-%'
              ), 0)
       ) THEN
        RAISE EXCEPTION 'V37 postcondition failed; learning XP leaderboard seed is rolled back';
    END IF;
END $$;

DROP FUNCTION v37_v36_uuid(TEXT);
DROP FUNCTION v37_seed_uuid(TEXT);
