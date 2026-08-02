-- Completes the 01-03 August demo window with the user-facing records that drive
-- calendar, roadmap, quiz, notification, progress, room-chat and admin views.
-- The migration deliberately operates only on the deterministic V36/V37 seed cohort.

CREATE FUNCTION v61_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v61:' || seed), 1, 8) || '-' || substr(md5('skillsprint-v61:' || seed), 9, 4) || '-' || substr(md5('skillsprint-v61:' || seed), 13, 4) || '-' || substr(md5('skillsprint-v61:' || seed), 17, 4) || '-' || substr(md5('skillsprint-v61:' || seed), 21, 12))::uuid;
$$;
CREATE FUNCTION v61_v36_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' || substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' || substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' || substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' || substr(md5('skillsprint-v36:' || seed), 21, 12))::uuid;
$$;
CREATE FUNCTION v61_v37_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v37:' || seed), 1, 8) || '-' || substr(md5('skillsprint-v37:' || seed), 9, 4) || '-' || substr(md5('skillsprint-v37:' || seed), 13, 4) || '-' || substr(md5('skillsprint-v37:' || seed), 17, 4) || '-' || substr(md5('skillsprint-v37:' || seed), 21, 12))::uuid;
$$;
CREATE FUNCTION v61_v28_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' || substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' || substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' || substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' || substr(md5('skillsprint-v28:' || seed), 21, 12))::uuid;
$$;

DO $$
BEGIN
    IF (SELECT count(*) FROM users WHERE user_id IN (SELECT v61_v36_uuid('user:' || n)::text FROM (VALUES (4),(17),(29),(46)) AS learner(n))) <> 4
       OR (SELECT count(*) FROM study_workspaces WHERE workspace_id IN (SELECT v61_v37_uuid('workspace:' || n) FROM (VALUES (4),(17),(29),(46)) AS learner(n))) <> 4
       OR (SELECT count(*) FROM point_events WHERE source_id LIKE 'v60-august-learning-%') <> 12 THEN
        RAISE EXCEPTION 'V61 requires V36/V37 and V60 seed data before completing user/admin activity';
    END IF;
    IF EXISTS (SELECT 1 FROM roadmaps WHERE roadmap_id = v61_uuid('roadmap:4')) THEN
        RAISE EXCEPTION 'V61 activity already exists; do not apply this migration to a partially seeded database';
    END IF;
END $$;

CREATE TEMP TABLE v61_learners ON COMMIT DROP AS
SELECT user_no, v61_v36_uuid('user:' || user_no)::text AS user_id, v61_v37_uuid('workspace:' || user_no) AS workspace_id,
       subject, roadmap_title
FROM (VALUES
    (4,  'Java Backend', 'Củng cố Java Backend và Spring Boot'),
    (17, 'ReactJS', 'Xây dựng giao diện React có trạng thái rõ ràng'),
    (29, 'SQL', 'Ôn SQL thực hành theo tình huống'),
    (46, 'Tiếng Anh', 'Duy trì phản xạ Tiếng Anh giao tiếp')
) AS learner(user_no, subject, roadmap_title);

INSERT INTO learning_structure_versions (structure_version_id, workspace_id, version_no, status, generated_by, ai_model, confidence_score, input_summary, warnings, created_at, confirmed_at)
SELECT v61_uuid('structure:' || user_no), workspace_id, 1, 'CONFIRMED', 'SYSTEM', 'seed-v61', 96.00,
       'Cấu trúc học ngắn hạn cho hoạt động đầu tháng 8: ' || subject, '[]'::jsonb,
       TIMESTAMPTZ '2026-08-01 07:00:00+07', TIMESTAMPTZ '2026-08-01 07:05:00+07'
FROM v61_learners;

INSERT INTO roadmaps (roadmap_id, workspace_id, structure_version_id, user_id, title, description, current_step_id, total_steps, completed_steps, progress_percent, version_no, status, generated_at, updated_at)
SELECT v61_uuid('roadmap:' || user_no), workspace_id, v61_uuid('structure:' || user_no), user_id,
       roadmap_title, 'Roadmap có tiến độ thật, quiz và task được liên kết theo từng bước.',
       NULL, 3, 1, 33.33, 1, 'ACTIVE', TIMESTAMPTZ '2026-08-01 07:05:00+07', TIMESTAMPTZ '2026-08-03 21:30:00+07'
FROM v61_learners;

INSERT INTO roadmap_steps (step_id, roadmap_id, workspace_id, title, subtitle, summary, what_to_learn, key_concepts, learning_outcomes, recommended_focus, difficulty, estimated_study_time, estimated_minutes, sequence_no, status, completed_at, created_at, updated_at)
SELECT v61_uuid('step:' || learner.user_no || ':' || step.sequence_no), v61_uuid('roadmap:' || learner.user_no), learner.workspace_id,
       CASE step.sequence_no WHEN 1 THEN 'Ôn nền tảng và xác định phần còn yếu' WHEN 2 THEN 'Thực hành có hướng dẫn' ELSE 'Tổng hợp và tự kiểm tra' END,
       learner.subject || ' · giai đoạn ' || step.sequence_no,
       'Bước học có mục tiêu rõ ràng, phù hợp với một phiên học ngắn trong ngày.',
       jsonb_build_array('Đọc lại khái niệm chính', 'Làm ví dụ nhỏ'), jsonb_build_array('Tư duy hệ thống', 'Luyện tập có phản hồi'),
       jsonb_build_array('Tự giải thích được nội dung', 'Hoàn thành bài kiểm tra ngắn'), jsonb_build_array('Tập trung một việc mỗi phiên'),
       'MEDIUM', '45 phút', 45, step.sequence_no,
       CASE step.sequence_no WHEN 1 THEN 'COMPLETED' WHEN 2 THEN 'CURRENT' ELSE 'UPCOMING' END,
       CASE WHEN step.sequence_no = 1 THEN TIMESTAMPTZ '2026-08-01 20:40:00+07' ELSE NULL END,
       TIMESTAMPTZ '2026-08-01 07:10:00+07', TIMESTAMPTZ '2026-08-03 21:30:00+07'
FROM v61_learners learner CROSS JOIN (VALUES (1),(2),(3)) AS step(sequence_no);

UPDATE roadmaps roadmap
SET current_step_id = v61_uuid('step:' || learner.user_no || ':2')
FROM v61_learners learner
WHERE roadmap.roadmap_id = v61_uuid('roadmap:' || learner.user_no);

-- Every current step has a usable five-question quiz, while each completed step has a real attempt.
INSERT INTO quizzes (quiz_id, user_id, workspace_id, roadmap_step_id, title, passing_score, question_count, status, created_at, updated_at)
SELECT v61_uuid('quiz:' || learner.user_no || ':' || step.sequence_no), learner.user_id, learner.workspace_id,
       v61_uuid('step:' || learner.user_no || ':' || step.sequence_no), 'Quiz ' || learner.subject || ' - phần ' || step.sequence_no,
       70, 5, 'ACTIVE', TIMESTAMPTZ '2026-08-01 07:15:00+07', TIMESTAMPTZ '2026-08-03 21:30:00+07'
FROM v61_learners learner CROSS JOIN (VALUES (1),(2)) AS step(sequence_no);

INSERT INTO quiz_questions (question_id, quiz_id, type, question_text, explanation, sequence_no, created_at, updated_at)
SELECT v61_uuid('question:' || learner.user_no || ':' || quiz.sequence_no || ':' || question.sequence_no), v61_uuid('quiz:' || learner.user_no || ':' || quiz.sequence_no),
       'SINGLE_CHOICE', learner.subject || ' - câu hỏi ôn tập số ' || question.sequence_no,
       'Đáp án đúng đi kèm giải thích ngắn để người học tự đối chiếu.', question.sequence_no,
       TIMESTAMPTZ '2026-08-02 08:00:00+07', TIMESTAMPTZ '2026-08-02 08:00:00+07'
FROM v61_learners learner CROSS JOIN (VALUES (1),(2)) AS quiz(sequence_no) CROSS JOIN generate_series(1, 5) AS question(sequence_no);

INSERT INTO quiz_options (option_id, question_id, label, option_text, is_correct, sequence_no, created_at, updated_at)
SELECT v61_uuid('option:' || learner.user_no || ':' || quiz.sequence_no || ':' || question.sequence_no || ':' || option.sequence_no),
       v61_uuid('question:' || learner.user_no || ':' || quiz.sequence_no || ':' || question.sequence_no), chr(64 + option.sequence_no),
       CASE WHEN option.sequence_no = 1 THEN 'Phương án đúng, bám sát nội dung vừa học.' ELSE 'Phương án cần xem lại trong phần giải thích.' END,
       option.sequence_no = 1, option.sequence_no, TIMESTAMPTZ '2026-08-02 08:00:00+07', TIMESTAMPTZ '2026-08-02 08:00:00+07'
FROM v61_learners learner CROSS JOIN (VALUES (1),(2)) AS quiz(sequence_no) CROSS JOIN generate_series(1, 5) AS question(sequence_no) CROSS JOIN generate_series(1, 4) AS option(sequence_no);

INSERT INTO quiz_attempts (attempt_id, quiz_id, user_id, score, passed, correct_count, question_count, status, submitted_at, created_at, updated_at)
SELECT v61_uuid('attempt:' || user_no), v61_uuid('quiz:' || user_no || ':1'), user_id,
       CASE user_no WHEN 17 THEN 80 WHEN 46 THEN 100 ELSE 90 END, TRUE,
       CASE user_no WHEN 17 THEN 4 ELSE 5 END, 5, 'PASSED', TIMESTAMPTZ '2026-08-01 20:35:00+07',
       TIMESTAMPTZ '2026-08-01 20:35:00+07', TIMESTAMPTZ '2026-08-01 20:35:00+07'
FROM v61_learners;

CREATE TEMP TABLE v61_tasks ON COMMIT DROP AS
SELECT row_no, learner.user_no, learner.user_id, learner.workspace_id, task_date, start_time::time, end_time::time, duration_minutes,
       title, category, priority, status, source, completed_at
FROM v61_learners learner
CROSS JOIN LATERAL (VALUES
    (1, DATE '2026-08-01', '19:30', '20:15', 45, 'Ôn lại ghi chú của phiên trước', 'REVIEW', 'MEDIUM', 'COMPLETED', 'SYSTEM_GENERATED', TIMESTAMPTZ '2026-08-01 20:20:00+07'),
    (2, DATE '2026-08-02', '08:00', '08:50', 50, 'Thực hành bài tập trọng tâm', 'PRACTICE', 'HIGH', 'COMPLETED', 'AI_GENERATED', TIMESTAMPTZ '2026-08-02 08:55:00+07'),
    (3, DATE '2026-08-03', '20:00', '20:45', 45, 'Làm quiz và ghi lại lỗi sai', 'DEEP_STUDY', 'HIGH', 'TODO', 'AI_GENERATED', NULL)
) AS task(row_no, task_date, start_time, end_time, duration_minutes, title, category, priority, status, source, completed_at);

INSERT INTO calendar_tasks (task_id, workspace_id, roadmap_id, roadmap_step_id, user_id, title, description, task_date, start_time, end_time, duration_minutes, category, priority, status, importance_score, urgency_score, is_important, is_urgent, eisenhower_quadrant, classification_reason, classified_by, classified_at, xp_reward, source, completed_at, overdue_notified, created_at, updated_at)
SELECT v61_uuid('task:' || user_no || ':' || row_no), workspace_id, v61_uuid('roadmap:' || user_no),
       v61_uuid('step:' || user_no || ':' || CASE WHEN row_no = 1 THEN 1 ELSE 2 END), user_id, title,
       'Task được tạo từ roadmap để duy trì nhịp học trong tuần đầu tháng 8.', task_date, start_time, end_time, duration_minutes,
       category, priority, status, 8.50, CASE WHEN row_no = 3 THEN 7.50 ELSE 6.00 END, TRUE, row_no = 3,
       CASE WHEN row_no = 3 THEN 'DO_NOW' ELSE 'SCHEDULE' END, 'Ưu tiên theo kế hoạch học và thời hạn trong ngày.', 'RULE_BASED',
       task_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh', CASE WHEN row_no = 2 THEN 60 ELSE 40 END, source, completed_at, FALSE,
       task_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh', COALESCE(completed_at, task_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
FROM v61_tasks;

INSERT INTO point_events (point_event_id, user_id, workspace_id, event_type, source_type, source_id, points, description, event_date, week_start_date, month_start_date, created_at, updated_at)
SELECT v61_uuid('task-point:' || task.user_no || ':' || task.row_no), task.user_id, task.workspace_id,
       'TASK_COMPLETED', 'CALENDAR_TASK', 'v61-calendar-task-' || task.user_no || '-' || task.row_no,
       CASE WHEN task.row_no = 2 THEN 60 ELSE 40 END, 'Hoàn thành task trong lịch học', task.task_date,
       date_trunc('week', task.task_date)::date, date_trunc('month', task.task_date)::date, task.completed_at, task.completed_at
FROM v61_tasks task WHERE task.status = 'COMPLETED';

INSERT INTO progress_logs (log_id, workspace_id, log_date, steps_completed_today, tasks_completed_today, minutes_studied_today, notes, created_at)
SELECT v61_uuid('progress:' || learner.user_no || ':' || day.log_date), learner.workspace_id, day.log_date,
       CASE WHEN day.log_date = DATE '2026-08-01' THEN 1 ELSE 0 END, 1,
       CASE day.log_date WHEN DATE '2026-08-01' THEN 42 WHEN DATE '2026-08-02' THEN 50 ELSE 45 END,
       'Đã duy trì lịch học theo kế hoạch.', day.log_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh' + INTERVAL '21 hours'
FROM v61_learners learner CROSS JOIN (VALUES (DATE '2026-08-01'),(DATE '2026-08-02'),(DATE '2026-08-03')) AS day(log_date);

INSERT INTO workspace_progress (progress_id, workspace_id, total_steps, completed_steps, total_tasks, completed_tasks, completion_percent, last_calculated_at)
SELECT v61_uuid('workspace-progress:' || learner.user_no), learner.workspace_id, 3, 1, 3, 2, 50.00, TIMESTAMPTZ '2026-08-03 21:30:00+07'
FROM v61_learners learner
ON CONFLICT (workspace_id) DO UPDATE SET total_steps = EXCLUDED.total_steps, completed_steps = EXCLUDED.completed_steps,
    total_tasks = EXCLUDED.total_tasks, completed_tasks = EXCLUDED.completed_tasks, completion_percent = EXCLUDED.completion_percent,
    last_calculated_at = EXCLUDED.last_calculated_at;

INSERT INTO notifications (notification_id, user_id, workspace_id, type, title, message, is_read, read_at, created_at)
SELECT v61_uuid('notification:' || learner.user_no || ':' || notice.row_no), learner.user_id, learner.workspace_id,
       notice.type, notice.title, notice.message, notice.is_read,
       CASE WHEN notice.is_read THEN notice.created_at + INTERVAL '18 minutes' ELSE NULL END, notice.created_at
FROM v61_learners learner CROSS JOIN (VALUES
    (1, 'ROADMAP_READY', 'Roadmap đã sẵn sàng', 'Lộ trình học đầu tháng đã được cập nhật theo mục tiêu của bạn.', TRUE, TIMESTAMPTZ '2026-08-01 07:20:00+07'),
    (2, 'TASK_REMINDER', 'Nhắc lịch học tối nay', 'Bạn còn một task ôn tập ngắn trong lịch hôm nay.', FALSE, TIMESTAMPTZ '2026-08-03 19:15:00+07'),
    (3, 'AI_SCHEDULE_READY', 'Lịch học gợi ý đã sẵn sàng', 'Hệ thống đã sắp xếp các phiên học tiếp theo theo thời gian bạn thường học.', FALSE, TIMESTAMPTZ '2026-08-03 21:35:00+07')
) AS notice(row_no, type, title, message, is_read, created_at);

-- Members and messages are inserted together so websocket and room-admin views see valid participants.
INSERT INTO community_room_members (member_id, room_id, user_id, role, banned, status, created_at, updated_at)
SELECT v61_uuid('room-member:' || learner.user_no), v61_v28_uuid('room:6'), learner.user_id, 'MEMBER', FALSE, 'ACTIVE',
       TIMESTAMPTZ '2026-08-01 09:00:00+07', TIMESTAMPTZ '2026-08-01 09:00:00+07'
FROM v61_learners learner
ON CONFLICT (room_id, user_id) DO UPDATE SET banned = FALSE, status = 'ACTIVE', left_at = NULL, removed_at = NULL, updated_at = EXCLUDED.updated_at;

INSERT INTO community_chat_messages (message_id, room_id, sender_id, raw_content, masked_content, hidden, report_count, sent_at)
SELECT v61_uuid('chat:' || learner.user_no || ':' || message.row_no), v61_v28_uuid('room:6'), learner.user_id,
       message.content, message.content, FALSE, 0, message.sent_at
FROM v61_learners learner CROSS JOIN (VALUES
    (1, 'Chào mọi người, mình vừa hoàn thành phiên ôn tập đầu tháng. Phần chia task theo roadmap khá dễ theo dõi.', TIMESTAMPTZ '2026-08-01 20:50:00+07'),
    (2, 'Mình đang ghi lại những câu quiz sai để xem lại tối mai. Cách này giúp nhớ lâu hơn.', TIMESTAMPTZ '2026-08-02 21:15:00+07'),
    (3, 'Ai có mẹo giữ nhịp học đều cuối tuần thì chia sẻ cùng mình nhé.', TIMESTAMPTZ '2026-08-03 21:45:00+07')
) AS message(row_no, content, sent_at);

UPDATE community_rooms room
SET member_count = (SELECT count(*) FROM community_room_members member WHERE member.room_id = room.room_id AND member.status = 'ACTIVE')
WHERE room.room_id = v61_v28_uuid('room:6');

UPDATE user_point_summaries summary
SET total_points = aggregate.total_points, current_week_points = aggregate.week_points, current_week_start_date = DATE '2026-07-27',
    current_month_points = aggregate.month_points, current_month_start_date = DATE '2026-08-01', streak_days = GREATEST(summary.streak_days, 3),
    last_point_date = aggregate.last_point_date, updated_at = aggregate.last_event_at
FROM (
    SELECT event.user_id, sum(event.points) AS total_points,
           sum(event.points) FILTER (WHERE event.week_start_date = DATE '2026-07-27') AS week_points,
           sum(event.points) FILTER (WHERE event.month_start_date = DATE '2026-08-01') AS month_points,
           max(event.event_date) AS last_point_date, max(event.created_at) AS last_event_at
    FROM point_events event WHERE event.user_id IN (SELECT user_id FROM v61_learners) GROUP BY event.user_id
) aggregate WHERE summary.user_id = aggregate.user_id;

INSERT INTO system_announcements (announcement_id, enabled, title, message, type, start_at, end_at, updated_by, created_at, updated_at)
SELECT v61_uuid('announcement:august'), TRUE, 'Khởi động tháng 8 cùng SkillSprint',
       'Hãy duy trì một phiên học ngắn mỗi ngày để hoàn thành mục tiêu đầu tháng.', 'INFO',
       TIMESTAMPTZ '2026-08-01 00:00:00+07', TIMESTAMPTZ '2026-08-04 00:00:00+07', admin.user_id,
       TIMESTAMPTZ '2026-08-01 07:00:00+07', TIMESTAMPTZ '2026-08-01 07:00:00+07'
FROM LATERAL (
    SELECT user_role.user_id FROM user_roles user_role JOIN roles role ON role.role_id = user_role.role_id
    JOIN users account ON account.user_id = user_role.user_id
    WHERE role.role_name = 'ADMIN' AND account.status = 'ACTIVE' ORDER BY user_role.granted_at NULLS LAST LIMIT 1
) admin;

DO $$
BEGIN
    IF (SELECT count(*) FROM roadmaps WHERE roadmap_id IN (SELECT v61_uuid('roadmap:' || n) FROM (VALUES (4),(17),(29),(46)) AS learner(n))) <> 4
       OR (SELECT count(*) FROM roadmap_steps WHERE roadmap_id IN (SELECT v61_uuid('roadmap:' || n) FROM (VALUES (4),(17),(29),(46)) AS learner(n))) <> 12
       OR (SELECT count(*) FROM calendar_tasks WHERE task_id IN (SELECT v61_uuid('task:' || learner.n || ':' || task.n) FROM (VALUES (4),(17),(29),(46)) AS learner(n) CROSS JOIN (VALUES (1),(2),(3)) AS task(n))) <> 12
       OR (SELECT count(*) FROM quiz_attempts WHERE attempt_id IN (SELECT v61_uuid('attempt:' || n) FROM (VALUES (4),(17),(29),(46)) AS learner(n))) <> 4
       OR (SELECT count(*) FROM notifications WHERE notification_id IN (SELECT v61_uuid('notification:' || learner.n || ':' || notice.n) FROM (VALUES (4),(17),(29),(46)) AS learner(n) CROSS JOIN (VALUES (1),(2),(3)) AS notice(n))) <> 12
       OR (SELECT count(*) FROM community_chat_messages WHERE message_id IN (SELECT v61_uuid('chat:' || learner.n || ':' || message.n) FROM (VALUES (4),(17),(29),(46)) AS learner(n) CROSS JOIN (VALUES (1),(2),(3)) AS message(n))) <> 12
       OR (SELECT count(*) FROM point_events WHERE source_id LIKE 'v61-calendar-task-%') <> 8 THEN
        RAISE EXCEPTION 'V61 postcondition failed; user-facing and admin activity are not synchronized';
    END IF;
END $$;

DROP FUNCTION v61_v28_uuid(TEXT);
DROP FUNCTION v61_v37_uuid(TEXT);
DROP FUNCTION v61_v36_uuid(TEXT);
DROP FUNCTION v61_uuid(TEXT);
