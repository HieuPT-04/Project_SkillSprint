-- V68: Seeds realistic Marketplace Refund Disputes (Vận hành Marketplace - Tranh chấp hoàn tiền)
-- and populates legacy marketplace_items & snapshots for pending moderation Quiz Packs (Duyệt Quiz Pack).

CREATE FUNCTION v68_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v68:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v68:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v68:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v68:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v68:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v68_v66_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v66:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v66:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v68-v66:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v66:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v66:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v68_v52_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v52:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v52:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v52:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v52:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v52:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v68_v36_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 21, 12))::uuid;
$$;

-- 1. Sync legacy marketplace_items and marketplace_quiz_pack_snapshots for PENDING_REVIEW packs
-- so Admin "Duyệt Quiz Pack" tab lists and displays complete question detail for moderation.

DO $$
DECLARE
    rec RECORD;
    v_item_id UUID;
    v_creator_id VARCHAR(100);
    v_ws_id UUID;
    v_content JSONB;
BEGIN
    -- Chapter & question payload template for pending Quiz Packs
    v_content := '{
      "chapters": [
        {
          "chapterId": "ch-1",
          "sequenceNo": 1,
          "title": "Chương 1: Kiến trúc cốt lõi & Cấu hình môi trường",
          "summary": "Tổng quan về các nguyên lý thiết kế, luồng xử lý dữ liệu và cấu hình hệ thống đạt chuẩn production.",
          "quizzes": [
            {
              "quizId": "quiz-1-1",
              "title": "Quiz 1.1: Trắc nghiệm đánh giá nền tảng & Best Practices",
              "questions": [
                {
                  "questionId": "q-101",
                  "question": "Trong kiến trúc hệ thống phân tán, cơ chế nào đảm bảo tính nhất quán dữ liệu cao nhất giữa các dịch vụ độc lập?",
                  "type": "MULTIPLE_CHOICE",
                  "explanation": "Cơ chế Two-Phase Commit (2PC) hoặc SAGA Pattern kết hợp với Event-Driven Architecture giúp duy trì tính toàn vẹn dữ liệu qua các service.",
                  "evidence": {
                    "sourceStepId": "step-arch-01",
                    "sourceChunkIds": ["chunk-saga-pattern"],
                    "explanation": "Trích từ tài liệu Microservices Design Patterns (Section 4.2)."
                  },
                  "options": [
                    { "optionId": "opt-1", "label": "A", "text": "SAGA Orchestration Pattern với Compensating Transactions", "correct": true },
                    { "optionId": "opt-2", "label": "B", "text": "Global Shared Database Connection Pool", "correct": false },
                    { "optionId": "opt-3", "label": "C", "text": "HTTP Synchronous Direct Calls", "correct": false },
                    { "optionId": "opt-4", "label": "D", "text": "Asynchronous Batch File Export", "correct": false }
                  ]
                },
                {
                  "questionId": "q-102",
                  "question": "Chiến lược caching nào giảm thiểu tối đa latency khi truy vấn dữ liệu có tần suất đọc lớn gấp 100 lần ghi?",
                  "type": "MULTIPLE_CHOICE",
                  "explanation": "Cache-Aside (Lazy Loading) kết hợp Write-Through đảm bảo cache hit rate cao và giảm áp lực cho DB chính.",
                  "evidence": {
                    "sourceStepId": "step-cache-02",
                    "sourceChunkIds": ["chunk-redis-cache"],
                    "explanation": "Trích từ hướng dẫn tối ưu Redis Caching Strategy."
                  },
                  "options": [
                    { "optionId": "opt-102-1", "label": "A", "text": "Cache-Aside pattern kết hợp Redis Cluster", "correct": true },
                    { "optionId": "opt-102-2", "label": "B", "text": "Direct Database Index Scan", "correct": false },
                    { "optionId": "opt-102-3", "label": "C", "text": "No-cache directive trên API Gateway", "correct": false },
                    { "optionId": "opt-102-4", "label": "D", "text": "Write-Back Caching không có TTL", "correct": false }
                  ]
                }
              ]
            }
          ]
        },
        {
          "chapterId": "ch-2",
          "sequenceNo": 2,
          "title": "Chương 2: Tối ưu hiệu năng, Security & Monitoring",
          "summary": "Nâng cao khả năng chịu tải, phân tích chỉ số APM và bảo mật nhiều lớp.",
          "quizzes": [
            {
              "quizId": "quiz-2-1",
              "title": "Quiz 2.1: Kỹ thuật giám sát và phòng thủ chuyên sâu",
              "questions": [
                {
                  "questionId": "q-201",
                  "question": "Để phòng chống tấn công SQL Injection & Remote Code Execution hiệu quả nhất, giải pháp nào là bắt buộc?",
                  "type": "MULTIPLE_CHOICE",
                  "explanation": "Sử dụng Parameterized Queries (Prepared Statements) kết hợp Input Sanitization và WAF rule.",
                  "evidence": {
                    "sourceStepId": "step-sec-03",
                    "sourceChunkIds": ["chunk-owasp-top10"],
                    "explanation": "Chuẩn bảo mật OWASP Top 10 A03:2021-Injection."
                  },
                  "options": [
                    { "optionId": "opt-201-1", "label": "A", "text": "Parameterized Queries / Prepared Statements", "correct": true },
                    { "optionId": "opt-201-2", "label": "B", "text": "String Concatenation trong SQL Query", "correct": false },
                    { "optionId": "opt-201-3", "label": "C", "text": "Tắt Log Error trên Server", "correct": false },
                    { "optionId": "opt-201-4", "label": "D", "text": "Chỉ kiểm tra định dạng ở Frontend", "correct": false }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }'::jsonb;

    FOR rec IN 
        SELECT v.version_id, v.pack_id, v.title, v.description, v.subject, v.price_coins,
               v.chapter_count, v.quiz_count, v.question_count, v.creator_validation_score,
               v.created_at, p.creator_id, p.source_workspace_id
        FROM marketplace_pack_versions v
        JOIN marketplace_packs p ON p.pack_id = v.pack_id
        WHERE v.status = 'PENDING_REVIEW'
    LOOP
        v_item_id := v68_uuid('item:pending:' || rec.version_id);
        
        -- Insert corresponding marketplace_items row if not present
        INSERT INTO marketplace_items (
            item_id, creator_id, source_workspace_id, title, description, subject,
            price_coins, status, creator_validation_score, created_at, updated_at
        ) VALUES (
            v_item_id, rec.creator_id, rec.source_workspace_id, rec.title, rec.description, rec.subject,
            rec.price_coins, 'PENDING_REVIEW', rec.creator_validation_score, rec.created_at, rec.created_at
        ) ON CONFLICT (item_id) DO NOTHING;

        -- Insert corresponding marketplace_quiz_pack_snapshots row
        INSERT INTO marketplace_quiz_pack_snapshots (
            snapshot_id, item_id, chapter_count, quiz_count, question_count, content_json, created_at, updated_at
        ) VALUES (
            v68_uuid('snapshot:pending:' || rec.version_id), v_item_id, rec.chapter_count, rec.quiz_count, rec.question_count, v_content, rec.created_at, rec.created_at
        ) ON CONFLICT (item_id) DO NOTHING;

        -- Update legacy_item_id cross-reference
        UPDATE marketplace_packs SET legacy_item_id = v_item_id WHERE pack_id = rec.pack_id AND legacy_item_id IS NULL;
        UPDATE marketplace_pack_versions SET legacy_item_id = v_item_id WHERE version_id = rec.version_id AND legacy_item_id IS NULL;
    END LOOP;
END $$;


-- 2. Seed Realistic Refund Disputes (Vận hành Marketplace - Tranh chấp hoàn tiền)
-- Covering OPEN, UNDER_REVIEW, APPROVED, REFUNDED, REJECTED statuses with rich Vietnamese descriptions & notes.

CREATE TEMP TABLE v68_disputes_seed (
    dispute_no INTEGER PRIMARY KEY,
    pack_no INTEGER NOT NULL,
    sale_no INTEGER NOT NULL,
    buyer_no INTEGER NOT NULL,
    reason VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    admin_no INTEGER,
    decision_note TEXT,
    decided_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    refund_coin_amount INTEGER,
    created_at TIMESTAMPTZ
) ON COMMIT DROP;

INSERT INTO v68_disputes_seed (
    dispute_no, pack_no, sale_no, buyer_no, reason, description, status,
    admin_no, decision_note, decided_at, refunded_at, refund_coin_amount, created_at
) VALUES
    -- 1. OPEN (Mới) - AWS Certified Quiz Pack
    (1, 1, 1, 10, 'NOT_AS_DESCRIBED', 
     'Nội dung Quiz Pack ghi có 120+ câu hỏi tình huống thực tế nhưng khi mở thực hành chỉ có 80 câu. Một số đáp án chương 2 bị lặp lựa chọn A và B.',
     'OPEN', NULL, NULL, NULL, NULL, NULL, TIMESTAMPTZ '2026-08-03 10:15:00+07'),

    -- 2. OPEN (Mới) - System Design Architecture
    (2, 2, 1, 15, 'TECHNICAL_ISSUE', 
     'Bài thi Ranked trong Quiz Pack bị lỗi hiển thị hình ảnh sơ đồ Caching & Consistent Hashing khiến không thể chọn đáp án chính xác.',
     'OPEN', NULL, NULL, NULL, NULL, NULL, TIMESTAMPTZ '2026-08-02 18:40:00+07'),

    -- 3. UNDER_REVIEW (Đang xem xét) - Spring Boot Microservices
    (3, 3, 1, 20, 'POOR_QUALITY', 
     'Chất lượng câu hỏi chưa thỏa đáng so với giá 70 Coin. Giải thích câu hỏi ngắn ngủi, nhiều chỗ ghi "Xem tài liệu" chứ không giải thích chi tiết.',
     'UNDER_REVIEW', NULL, NULL, NULL, NULL, NULL, TIMESTAMPTZ '2026-08-02 14:20:00+07'),

    -- 4. APPROVED (Đã duyệt, sẵn sàng bấm "Thực hiện hoàn tiền") - React Enterprise Architecture
    (4, 4, 1, 25, 'ACCIDENTAL_PURCHASE', 
     'Tôi nhấp nhầm nút thanh toán khi xem chi tiết Quiz Pack trên ứng dụng di động. Chưa hề mở bài thực hành nào trong gói.',
     'APPROVED', 5, 'Đã xác minh tài khoản người mua chưa truy cập các câu hỏi thực hành. Chấp nhận duyệt hoàn tiền 100% Coin.', TIMESTAMPTZ '2026-08-03 09:00:00+07', NULL, NULL, TIMESTAMPTZ '2026-08-02 11:05:00+07'),

    -- 5. REFUNDED (Đã hoàn tiền thành công) - English IELTS Reading
    (5, 5, 1, 30, 'NOT_AS_DESCRIBED', 
     'Gói trắc nghiệm ghi chuẩn IELTS 7.5+ nhưng câu hỏi quá đơn giản, ngữ pháp sơ cấp không đúng mô tả.',
     'REFUNDED', 5, 'Duyệt yêu cầu hoàn tiền do mô tả gói chưa phản ánh đúng độ khó thực tế.', TIMESTAMPTZ '2026-08-01 16:30:00+07', TIMESTAMPTZ '2026-08-01 17:00:00+07', 50, TIMESTAMPTZ '2026-08-01 11:30:00+07'),

    -- 6. REJECTED (Từ chối hoàn tiền) - Docker & Kubernetes Ops
    (6, 6, 1, 35, 'OTHER', 
     'Tôi muốn hoàn tiền vì đã thi đỗ chứng chỉ K8s rồi nên không cần dùng gói này nữa.',
     'REJECTED', 5, 'Từ chối hoàn tiền: Người mua đã hoàn thành 100% các câu hỏi và lý do hoàn tiền không thuộc chính sách bảo vệ người mua.', TIMESTAMPTZ '2026-07-31 14:00:00+07', NULL, NULL, TIMESTAMPTZ '2026-07-30 09:15:00+07');


DO $$
DECLARE
    d RECORD;
    v_sale_id UUID;
    v_buyer_id VARCHAR(100);
    v_version_id UUID;
    v_dispute_id UUID;
    v_tx_id UUID;
BEGIN
    FOR d IN SELECT * FROM v68_disputes_seed LOOP
        v_dispute_id := v68_uuid('dispute:' || d.dispute_no);
        v_buyer_id := v68_v36_uuid('user:' || d.buyer_no);
        v_version_id := COALESCE(
            (SELECT version_id FROM marketplace_pack_versions WHERE version_no = 1 ORDER BY created_at ASC LIMIT 1 OFFSET (d.pack_no - 1)),
            v68_v52_uuid('version:' || d.pack_no)
        );

        -- Locate or create an backing sale record
        v_sale_id := (SELECT sale_id FROM marketplace_sales WHERE pack_version_id = v_version_id LIMIT 1);
        IF v_sale_id IS NULL THEN
            v_sale_id := v68_uuid('sale:' || d.dispute_no);
            INSERT INTO marketplace_sales (
                sale_id, buyer_id, pack_id, pack_version_id, gross_coin_amount, gross_vnd_amount,
                coin_to_vnd_rate, status, idempotency_key, created_at, updated_at
            ) VALUES (
                v_sale_id, v_buyer_id, 
                (SELECT pack_id FROM marketplace_pack_versions WHERE version_id = v_version_id LIMIT 1),
                v_version_id, COALESCE(d.refund_coin_amount, 50), COALESCE(d.refund_coin_amount, 50) * 1000,
                1000.0000, CASE WHEN d.status = 'REFUNDED' THEN 'REFUNDED' ELSE 'COMPLETED' END,
                'v68-sale-' || d.dispute_no, d.created_at - INTERVAL '1 day', d.created_at - INTERVAL '1 day'
            ) ON CONFLICT (sale_id) DO NOTHING;
        END IF;

        -- For REFUNDED status, generate a mock refund wallet transaction if coins were refunded
        IF d.status = 'REFUNDED' AND COALESCE(d.refund_coin_amount, 0) > 0 THEN
            v_tx_id := v68_uuid('refund-tx:' || d.dispute_no);
            INSERT INTO wallet_transactions (
                transaction_id, wallet_id, direction, amount, balance_before, balance_after,
                reference_type, reference_id, created_at, updated_at
            ) VALUES (
                v_tx_id, v68_v52_uuid('wallet:' || v_buyer_id), 'CREDIT', d.refund_coin_amount,
                100, 100 + d.refund_coin_amount, 'MARKETPLACE_REFUND', v_dispute_id,
                d.refunded_at, d.refunded_at
            ) ON CONFLICT (transaction_id) DO NOTHING;
        ELSE
            v_tx_id := NULL;
        END IF;

        -- Insert into marketplace_refund_disputes table
        INSERT INTO marketplace_refund_disputes (
            dispute_id, sale_id, buyer_id, pack_version_id, reason, description, status,
            admin_actor_id, decision_note, decided_at, refunded_at, refund_coin_amount,
            refund_wallet_transaction_id, created_at, updated_at
        ) VALUES (
            v_dispute_id, v_sale_id, v_buyer_id, v_version_id, d.reason, d.description, d.status,
            CASE WHEN d.admin_no IS NOT NULL THEN v68_v36_uuid('user:' || d.admin_no)::text ELSE NULL END,
            d.decision_note, d.decided_at, d.refunded_at, d.refund_coin_amount,
            v_tx_id, d.created_at, COALESCE(d.refunded_at, d.decided_at, d.created_at)
        ) ON CONFLICT (dispute_id) DO NOTHING;
    END LOOP;
END $$;


-- Postcondition assertion
DO $$
BEGIN
    IF (SELECT count(*) FROM marketplace_refund_disputes) < 5 THEN
        RAISE EXCEPTION 'V68 failed to seed sufficient marketplace refund disputes';
    END IF;
    IF (SELECT count(*) FROM marketplace_items WHERE status = 'PENDING_REVIEW') < 3 THEN
        RAISE EXCEPTION 'V68 failed to populate legacy marketplace_items for moderation';
    END IF;
END $$;

DROP FUNCTION v68_v36_uuid(TEXT);
DROP FUNCTION v68_v52_uuid(TEXT);
DROP FUNCTION v68_v66_uuid(TEXT);
DROP FUNCTION v68_uuid(TEXT);
