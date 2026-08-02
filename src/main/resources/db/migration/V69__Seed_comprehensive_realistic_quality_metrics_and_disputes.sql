-- V69: Seeds comprehensive realistic quality metrics (Learner progress, Ratings, Content Reports, Ranked Attempts, Refund Disputes)
-- for ALL published Quiz Pack versions, and adds 6+ new detailed pending moderation packs.

CREATE FUNCTION v69_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v69:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v69:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v69:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v69:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v69:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v69_v36_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 21, 12))::uuid;
$$;

-- 1. Rename any dummy titles in marketplace_pack_versions & marketplace_items to professional Vietnamese titles
UPDATE marketplace_pack_versions
SET title = 'Xây Dựng Đồ Án Fullstack E-Commerce Vận Hành Thực Tế 2026'
WHERE title ILIKE '%ádasd%';

UPDATE marketplace_items
SET title = 'Xây Dựng Đồ Án Fullstack E-Commerce Vận Hành Thực Tế 2026'
WHERE title ILIKE '%ádasd%';

UPDATE marketplace_pack_versions
SET title = 'Đồ Án Tốt Nghiệp: Hệ Thống Microservices & Distributed AI'
WHERE title = 'Đồ án' OR title = 'Đồ án 3D';

UPDATE marketplace_items
SET title = 'Đồ Án Tốt Nghiệp: Hệ Thống Microservices & Distributed AI'
WHERE title = 'Đồ án' OR title = 'Đồ án 3D';


-- 2. Seed Learner Version Progress (marketplace_version_progress) for ALL published versions
DO $$
DECLARE
    v_rec RECORD;
    v_buyer_id VARCHAR(100);
    v_progress_id UUID;
    v_learner_idx INTEGER;
    v_total_learners INTEGER;
    v_completed_count INTEGER;
    v_is_completed BOOLEAN;
    v_percent NUMERIC(5, 2);
    v_seed_offset INTEGER := 0;
BEGIN
    FOR v_rec IN 
        SELECT version_id, chapter_count, quiz_count, created_at
        FROM marketplace_pack_versions
        WHERE status = 'PUBLISHED' OR saleable = TRUE
    LOOP
        v_seed_offset := v_seed_offset + 1;
        v_total_learners := 25 + (v_seed_offset * 7) % 36;
        v_completed_count := (v_total_learners * (70 + (v_seed_offset * 3) % 25)) / 100;

        FOR v_learner_idx IN 1..v_total_learners LOOP
            v_buyer_id := v69_v36_uuid('user:' || (1 + ((v_seed_offset * 5 + v_learner_idx) % 75)));
            v_progress_id := v69_uuid('progress:' || v_rec.version_id || ':' || v_learner_idx);
            v_is_completed := v_learner_idx <= v_completed_count;
            
            IF v_is_completed THEN
                v_percent := 100.00;
            ELSE
                v_percent := 20.00 + ((v_learner_idx * 13) % 75);
            END IF;

            INSERT INTO marketplace_version_progress (
                progress_id, buyer_id, pack_version_id, completed_quiz_count, completed_chapter_count,
                completion_percent, first_activity_at, last_activity_at, created_at, updated_at
            ) VALUES (
                v_progress_id, v_buyer_id, v_rec.version_id,
                CASE WHEN v_is_completed THEN v_rec.quiz_count ELSE GREATEST(1, v_rec.quiz_count / 2) END,
                CASE WHEN v_is_completed THEN v_rec.chapter_count ELSE GREATEST(1, v_rec.chapter_count / 2) END,
                v_percent,
                v_rec.created_at + (v_learner_idx * INTERVAL '2 hours'),
                v_rec.created_at + (v_learner_idx * INTERVAL '5 hours') + INTERVAL '1 day',
                v_rec.created_at + (v_learner_idx * INTERVAL '2 hours'),
                v_rec.created_at + (v_learner_idx * INTERVAL '5 hours') + INTERVAL '1 day'
            ) ON CONFLICT (buyer_id, pack_version_id) DO UPDATE
            SET completion_percent = EXCLUDED.completion_percent,
                completed_quiz_count = EXCLUDED.completed_quiz_count,
                completed_chapter_count = EXCLUDED.completed_chapter_count;
        END LOOP;
    END LOOP;
END $$;


-- 3. Seed Marketplace Reviews (marketplace_reviews) for ALL published versions with valid foreign key items
DO $$
DECLARE
    v_rec RECORD;
    v_buyer_id VARCHAR(100);
    v_review_id UUID;
    v_review_idx INTEGER;
    v_review_count INTEGER;
    v_rating INTEGER;
    v_comment TEXT;
    v_seed_offset INTEGER := 0;
    v_comments TEXT[] := ARRAY[
        'Bộ đề rất bám sát thực tế, câu hỏi tình huống giải thích kĩ lưỡng từng đáp án.',
        'Kiến thức hệ thống rõ ràng, bố cục từng chương dễ theo dõi và ôn tập.',
        'Giá trị nhất ở phần giải thích lỗi sai thường gặp và các case study thực chiến.',
        'Rất đáng tiền! Giúp mình củng cố lỗ hổng kiến thức trước khi đi phỏng vấn.',
        'Nội dung cập nhật mới nhất, có hình ảnh minh họa bài học trực quan.',
        'Các câu hỏi phân loại tốt từ mức cơ bản đến nâng cao. Đánh giá 5 sao!'
    ];
BEGIN
    FOR v_rec IN 
        SELECT mpv.version_id, mi.item_id, mpv.created_at
        FROM marketplace_pack_versions mpv
        JOIN marketplace_items mi ON mi.item_id = COALESCE(mpv.legacy_item_id, (SELECT mp.legacy_item_id FROM marketplace_packs mp WHERE mp.pack_id = mpv.pack_id))
        WHERE mpv.status = 'PUBLISHED' OR mpv.saleable = TRUE
    LOOP
        v_seed_offset := v_seed_offset + 1;
        v_review_count := 8 + (v_seed_offset * 3) % 15;

        FOR v_review_idx IN 1..v_review_count LOOP
            v_buyer_id := v69_v36_uuid('user:' || (10 + ((v_seed_offset * 3 + v_review_idx) % 65)));
            v_review_id := v69_uuid('review:' || v_rec.version_id || ':' || v_review_idx);
            v_rating := CASE WHEN v_review_idx % 5 = 0 THEN 4 ELSE 5 END;
            v_comment := v_comments[1 + (v_review_idx % array_length(v_comments, 1))];

            INSERT INTO marketplace_reviews (
                review_id, item_id, pack_version_id, user_id, rating, comment, created_at, updated_at
            ) VALUES (
                v_review_id, v_rec.item_id,
                v_rec.version_id, v_buyer_id, v_rating, v_comment,
                v_rec.created_at + (v_review_idx * INTERVAL '4 hours') + INTERVAL '2 days',
                v_rec.created_at + (v_review_idx * INTERVAL '4 hours') + INTERVAL '2 days'
            ) ON CONFLICT (user_id, item_id) DO UPDATE
            SET rating = EXCLUDED.rating, comment = EXCLUDED.comment, pack_version_id = EXCLUDED.pack_version_id;
        END LOOP;
    END LOOP;
END $$;


-- 4. Seed Ranked Definitions & Ranked Attempts (marketplace_ranked_attempts) for ALL published versions
DO $$
DECLARE
    v_rec RECORD;
    v_def_id UUID;
    v_buyer_id VARCHAR(100);
    v_attempt_id UUID;
    v_attempt_idx INTEGER;
    v_attempt_count INTEGER;
    v_suspicious_count INTEGER;
    v_is_suspicious BOOLEAN;
    v_seed_offset INTEGER := 0;
BEGIN
    FOR v_rec IN 
        SELECT version_id, created_at
        FROM marketplace_pack_versions
        WHERE status = 'PUBLISHED' OR saleable = TRUE
    LOOP
        v_seed_offset := v_seed_offset + 1;
        v_def_id := v69_uuid('ranked-def:' || v_rec.version_id);

        INSERT INTO marketplace_ranked_quiz_definitions (
            definition_id, pack_version_id, title, duration_minutes, question_count,
            pass_score, questions_json, created_at, updated_at
        ) VALUES (
            v_def_id, v_rec.version_id, 'Thử Thách Xếp Hạng Top Ranked', 30, 20, 70,
            '[]'::jsonb, v_rec.created_at, v_rec.created_at
        ) ON CONFLICT (pack_version_id) DO NOTHING;

        v_attempt_count := 20 + (v_seed_offset * 5) % 25;
        v_suspicious_count := 1 + (v_seed_offset % 2);

        FOR v_attempt_idx IN 1..v_attempt_count LOOP
            v_buyer_id := v69_v36_uuid('user:' || (5 + ((v_seed_offset * 4 + v_attempt_idx) % 70)));
            v_attempt_id := v69_uuid('ranked-attempt:' || v_rec.version_id || ':' || v_attempt_idx);
            v_is_suspicious := v_attempt_idx <= v_suspicious_count;

            INSERT INTO marketplace_ranked_attempts (
                attempt_id, buyer_id, pack_version_id, definition_id, attempt_date, attempt_number,
                status, started_at, expires_at, completed_at, question_snapshot_json, answer_snapshot_json,
                score, correct_count, duration_seconds, suspicious, leaderboard_eligible, created_at, updated_at
            ) VALUES (
                v_attempt_id, v_buyer_id, v_rec.version_id, v_def_id,
                CURRENT_DATE - (v_attempt_idx % 10), 1, 'COMPLETED',
                v_rec.created_at + (v_attempt_idx * INTERVAL '1 hour'),
                v_rec.created_at + (v_attempt_idx * INTERVAL '1 hour') + INTERVAL '30 minutes',
                v_rec.created_at + (v_attempt_idx * INTERVAL '1 hour') + (CASE WHEN v_is_suspicious THEN INTERVAL '15 seconds' ELSE INTERVAL '18 minutes' END),
                '[]'::jsonb, '[]'::jsonb,
                CASE WHEN v_is_suspicious THEN 100 ELSE 75 + (v_attempt_idx % 25) END,
                CASE WHEN v_is_suspicious THEN 20 ELSE 15 + (v_attempt_idx % 5) END,
                CASE WHEN v_is_suspicious THEN 15 ELSE 1080 END,
                v_is_suspicious, NOT v_is_suspicious,
                v_rec.created_at + (v_attempt_idx * INTERVAL '1 hour'),
                v_rec.created_at + (v_attempt_idx * INTERVAL '1 hour') + INTERVAL '20 minutes'
            ) ON CONFLICT (buyer_id, pack_version_id, attempt_date, attempt_number) DO NOTHING;
        END LOOP;
    END LOOP;
END $$;


-- 5. Seed Content Reports (marketplace_content_reports) for published versions
DO $$
DECLARE
    v_rec RECORD;
    v_reporter_id VARCHAR(100);
    v_report_id UUID;
    v_seed_offset INTEGER := 0;
BEGIN
    FOR v_rec IN 
        SELECT version_id, created_at
        FROM marketplace_pack_versions
        WHERE status = 'PUBLISHED' OR saleable = TRUE
    LOOP
        v_seed_offset := v_seed_offset + 1;
        v_reporter_id := v69_v36_uuid('user:' || (12 + (v_seed_offset % 50)));
        v_report_id := v69_uuid('report:' || v_rec.version_id);

        INSERT INTO marketplace_content_reports (
            report_id, reporter_id, pack_version_id, target_type, target_ref, category,
            description, status, reviewed_by, reviewed_at, resolution_note, created_at, updated_at
        ) VALUES (
            v_report_id, v_reporter_id, v_rec.version_id, 'QUESTION', 'Q-REF-01',
            CASE WHEN v_seed_offset % 2 = 0 THEN 'AMBIGUOUS' ELSE 'INCORRECT_ANSWER' END,
            'Cần làm rõ đáp án phân biệt giữa Synchronous call và Asynchronous Event-Driven trong câu #3.',
            CASE WHEN v_seed_offset % 3 = 0 THEN 'OPEN' ELSE 'RESOLVED' END,
            CASE WHEN v_seed_offset % 3 = 0 THEN NULL ELSE v69_v36_uuid('user:5')::text END,
            CASE WHEN v_seed_offset % 3 = 0 THEN NULL ELSE v_rec.created_at + INTERVAL '2 days' END,
            CASE WHEN v_seed_offset % 3 = 0 THEN NULL ELSE 'Đã kiểm tra và điều chỉnh câu từ rõ ràng hơn.' END,
            v_rec.created_at + INTERVAL '1 day',
            v_rec.created_at + INTERVAL '2 days'
        ) ON CONFLICT DO NOTHING;
    END LOOP;
END $$;


-- 6. Seed additional Pending Moderation Quiz Packs (Duyệt Quiz Pack - PENDING_REVIEW)
CREATE TEMP TABLE v69_new_pending ON COMMIT DROP AS
SELECT row_no,
       v69_v36_uuid('user:' || creator_no)::text AS creator_id,
       title, subject, description, price_coins,
       chapter_count, quiz_count, question_count, creator_validation_score
FROM (VALUES
    (10,  6, 'Chinh Phục Flutter & Dart: Building Cross-Platform Mobile Apps', 'Lập trình Mobile', 'Bộ 100+ câu hỏi chuyên sâu về State Management (Bloc, Provider), Isolate Async, Widget Lifecycle & Native Channels.', 65, 4, 8, 120, 96),
    (11, 14, 'Kiến Trúc AI/ML Production & LLM Fine-Tuning Thực Chiến 2026', 'Trí tuệ nhân tạo', 'Trắc nghiệm tình huống RAG, Vector Database (Milvus/Qdrant), Quantization, LoRA & Model Deployment với vLLM.', 85, 5, 10, 150, 97),
    (12, 24, 'DevOps Mastery: Docker, Kubernetes & ArgoCD GitOps Pipeline', 'DevOps & Cloud', 'Ngân hàng 110 câu hỏi K8s Ingress, Pod Security Standard, Persistent Volume, Helm Chart & CI/CD Automated Rollout.', 75, 4, 8, 110, 95),
    (13, 31, 'Chuyên Gia Bảo Mật Web & Penetration Testing OWASP Top 10', 'An toàn thông tin', 'Trắc nghiệm thực chiến phát hiện lỗ hổng XSS, CSRF, JWT Misconfiguration, OAuth2 Flow & Security Auditing.', 80, 5, 10, 130, 98),
    (14, 45, 'Mastering Next.js 15 App Router, React Server Components & Turbopack', 'Lập trình Frontend', 'Tổng hợp câu hỏi Server Actions, Parallel Routes, ISR, Dynamic Caching & Performance Optimization.', 70, 4, 8, 100, 94),
    (15, 53, 'Bộ Đề Tiếng Anh Chuyên Nông Công Nghệ (IT Technical Communication)', 'Ngoại ngữ', 'Ngân hàng từ vựng, thuật ngữ phần mềm, bài đọc đọc hiểu tài liệu API & giao tiếp Agile / Scrum quốc tế.', 50, 4, 8, 90, 93)
) AS p(row_no, creator_no, title, subject, description, price_coins, chapter_count, quiz_count, question_count, creator_validation_score);

DO $$
DECLARE
    p RECORD;
    v_pack_id UUID;
    v_version_id UUID;
    v_item_id UUID;
    v_ws_id UUID;
    v_content JSONB;
BEGIN
    v_content := '{
      "chapters": [
        {
          "chapterId": "ch-p1",
          "sequenceNo": 1,
          "title": "Chương 1: Kiến trúc tổng quan & Cấu hình nâng cao",
          "summary": "Nắm vững nguyên lý cốt lõi, mô hình phân tầng và thiết lập môi trường chuẩn sản xuất.",
          "quizzes": [
            {
              "quizId": "quiz-p1-1",
              "title": "Quiz 1.1: Trắc nghiệm đánh giá năng lực & Kỹ năng thực chiến",
              "questions": [
                {
                  "questionId": "qp-101",
                  "question": "Phương pháp nào sau đây giúp tối ưu hóa hiệu năng ứng dụng khi xử lý hàng triệu request đồng thời?",
                  "type": "MULTIPLE_CHOICE",
                  "explanation": "Áp dụng Asynchronous I/O, Connection Pooling và Caching đa tầng là giải pháp tiêu chuẩn tối ưu throughput.",
                  "evidence": {
                    "sourceStepId": "step-perf-01",
                    "sourceChunkIds": ["chunk-high-throughput"],
                    "explanation": "Trích tài liệu System Architecture Performance Guidelines."
                  },
                  "options": [
                    { "optionId": "op-1", "label": "A", "text": "Asynchronous Non-Blocking I/O kết hợp Connection Pooling", "correct": true },
                    { "optionId": "op-2", "label": "B", "text": "Tăng Synchronous Thread Count không giới hạn", "correct": false },
                    { "optionId": "op-3", "label": "C", "text": "Đọc ghi trực tiếp Disk File mỗi request", "correct": false },
                    { "optionId": "op-4", "label": "D", "text": "Tắt toàn bộ Index trên Database", "correct": false }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }'::jsonb;

    FOR p IN SELECT * FROM v69_new_pending LOOP
        v_pack_id := v69_uuid('pack:pending:' || p.row_no);
        v_version_id := v69_uuid('version:pending:' || p.row_no);
        v_item_id := v69_uuid('item:pending:' || p.row_no);

        v_ws_id := COALESCE(
            (SELECT workspace_id FROM study_workspaces WHERE user_id = p.creator_id AND status <> 'DELETED' LIMIT 1),
            v69_v36_uuid('workspace:1')
        );

        INSERT INTO marketplace_packs (pack_id, creator_id, source_workspace_id, legacy_item_id, created_at, updated_at)
        VALUES (v_pack_id, p.creator_id, v_ws_id, v_item_id, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day')
        ON CONFLICT (pack_id) DO NOTHING;

        INSERT INTO marketplace_pack_versions (
            version_id, pack_id, version_no, update_type, legacy_item_id, title, description, subject, price_coins,
            chapter_count, quiz_count, question_count, creator_validation_score, quality_score, quality_status,
            saleable, status, content_json, published_at, created_at, updated_at
        ) VALUES (
            v_version_id, v_pack_id, 1, 'MAJOR', v_item_id, p.title, p.description, p.subject, p.price_coins,
            p.chapter_count, p.quiz_count, p.question_count, p.creator_validation_score, 96, 'PASSED',
            TRUE, 'PENDING_REVIEW', v_content, NULL, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'
        ) ON CONFLICT (version_id) DO NOTHING;

        INSERT INTO marketplace_items (
            item_id, creator_id, source_workspace_id, title, description, subject, price_coins,
            status, creator_validation_score, created_at, updated_at
        ) VALUES (
            v_item_id, p.creator_id, v_ws_id, p.title, p.description, p.subject, p.price_coins,
            'PENDING_REVIEW', p.creator_validation_score, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'
        ) ON CONFLICT (item_id) DO NOTHING;

        INSERT INTO marketplace_quiz_pack_snapshots (
            snapshot_id, item_id, chapter_count, quiz_count, question_count, content_json, created_at, updated_at
        ) VALUES (
            v69_uuid('snapshot:pending:' || p.row_no), v_item_id, p.chapter_count, p.quiz_count, p.question_count, v_content,
            CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'
        ) ON CONFLICT (item_id) DO NOTHING;
    END LOOP;
END $$;

DROP FUNCTION v69_v36_uuid(TEXT);
DROP FUNCTION v69_uuid(TEXT);
