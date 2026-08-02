-- Seeds realistic Marketplace Moderation Items (Duyệt Quiz Pack) and Content Violation Reports (Báo cáo Marketplace)
-- submitted by active creators and learners between August 1 and 3, 2026.

CREATE FUNCTION v66_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v66:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v66:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v66:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v66:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v66:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v66_v36_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 21, 12))::uuid;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM marketplace_pack_versions WHERE description LIKE '%(V66 Moderation)%') THEN
        RAISE EXCEPTION 'V66 moderation seed already applied';
    END IF;
END $$;

-- 1. Create Pending Moderation Quiz Packs (Duyệt Quiz Pack - Status SUBMITTED / UNDER_REVIEW)
CREATE TEMP TABLE v66_pending_packs ON COMMIT DROP AS
SELECT row_no,
       v66_v36_uuid('user:' || creator_no)::text AS creator_id,
       title, subject, description, price_coins,
       chapter_count, quiz_count, question_count,
       creator_validation_score, quality_score, quality_status,
       status, submitted_at
FROM (VALUES
    (1,  5, 'Bộ Đề Luyện Thi AWS Certified Solutions Architect (SAA-C03) 2026', 'Điện toán đám mây', 'Tổng hợp 120+ câu hỏi tình huống thực tế về VPC, IAM, EC2 AutoScaling & Serverless Architecture (V66 Moderation).', 60, 4, 8, 120, 98, 96, 'PASSED', 'SUBMITTED', TIMESTAMPTZ '2026-08-03 08:30:00+07'),
    (2, 12, '100+ Câu Hỏi Phỏng Vấn System Design & High Availability Architecture', 'Khoa học máy tính', 'Bộ câu hỏi chọn lọc phân tích Caching, Load Balancer, Sharding & Distributed Consensus cho Senior Dev (V66 Moderation).', 80, 5, 10, 100, 95, 94, 'PASSED', 'SUBMITTED', TIMESTAMPTZ '2026-08-02 15:45:00+07'),
    (3, 22, 'Chinh Phục Spring Boot 3 & Microservices Real-World Design Patterns', 'Lập trình Backend', 'Trắc nghiệm thực chiến về Spring Security 6, OAuth2, Resiliency4j, Kafka & Distributed Tracing (V66 Moderation).', 70, 6, 12, 140, 96, 95, 'PASSED', 'SUBMITTED', TIMESTAMPTZ '2026-08-02 10:20:00+07'),
    (4, 33, 'Bộ Đề Tiếng Anh Chuyên Nông / IELTS Academic Reading & Vocabulary', 'Ngoại ngữ', 'Ngân hàng câu hỏi ngữ pháp, từ vựng và bài đọc hiểu chuyên sâu với lời giải chi tiết chuẩn IELTS 7.5+ (V66 Moderation).', 50, 4, 8, 90, 92, 91, 'PASSED', 'UNDER_REVIEW', TIMESTAMPTZ '2026-08-01 16:10:00+07')
) AS p(row_no, creator_no, title, subject, description, price_coins, chapter_count, quiz_count, question_count, creator_validation_score, quality_score, quality_status, status, submitted_at);

INSERT INTO marketplace_packs (
    pack_id, creator_id, title, description, subject, status, price_coins,
    created_at, updated_at
)
SELECT v66_uuid('pack:' || row_no), creator_id, title, description, subject, status, price_coins,
       submitted_at, submitted_at
FROM v66_pending_packs;

INSERT INTO marketplace_pack_versions (
    version_id, pack_id, version_no, update_type, title, description, subject, price_coins,
    chapter_count, quiz_count, question_count, creator_validation_score, quality_score, quality_status,
    saleable, status, content_json, published_at, created_at, updated_at
)
SELECT v66_uuid('version:' || row_no), v66_uuid('pack:' || row_no), 1, 'MAJOR', title, description, subject, price_coins,
       chapter_count, quiz_count, question_count, creator_validation_score, quality_score, quality_status,
       TRUE, status, '{"chapters": []}'::jsonb, NULL, submitted_at, submitted_at
FROM v66_pending_packs;

-- 2. Seed Realistic Content Reports (Báo cáo Marketplace)
CREATE TEMP TABLE v66_content_reports ON COMMIT DROP AS
SELECT row_no,
       v66_v36_uuid('user:' || reporter_no)::text AS reporter_id,
       v66_uuid('version:' || pack_version_no) AS pack_version_id,
       target_type, target_ref, category, description, status,
       reviewed_by_no, reviewed_at, resolution_note, created_at
FROM (VALUES
    -- OPEN (Chờ xử lý)
    (1, 14, 1, 'QUESTION', 'Q-AWS-14', 'INCORRECT_ANSWER', 'Giải thích câu #14 về VPC Peering Route Table bị lặp phương án, cần làm rõ cổng routing.', 'OPEN', NULL, NULL, NULL, TIMESTAMPTZ '2026-08-03 09:40:00+07'),
    (2, 28, 2, 'QUESTION', 'Q-SYS-28', 'AMBIGUOUS',        'Câu hỏi về Consistent Hashing thiếu giả định số lượng virtual nodes trong đáp án B.', 'OPEN', NULL, NULL, NULL, TIMESTAMPTZ '2026-08-02 17:10:00+07'),
    (3, 42, 3, 'QUESTION', 'Q-SPG-05', 'BROKEN',           'Công thức hiển thị mô hình Distributed Transaction bị mất kí tự SVG trên trình duyệt Safari.', 'OPEN', NULL, NULL, NULL, TIMESTAMPTZ '2026-08-02 11:30:00+07'),

    -- IN_REVIEW (Đang xem xét)
    (4, 19, 1, 'CHAPTER',  'CH-02',    'MISLEADING',       'Tài liệu tham khảo chương 2 dẫn link hình ảnh chưa cập nhật phiên bản AWS Console 2026.', 'IN_REVIEW', NULL, NULL, NULL, TIMESTAMPTZ '2026-08-01 14:20:00+07'),

    -- RESOLVED (Đã xử lý)
    (5, 35, 2, 'VERSION',  NULL,       'DUPLICATE',        'Một số câu hỏi lý thuyết Caching trùng với bài test miễn phí trong chương mở đầu.', 'RESOLVED', 5, TIMESTAMPTZ '2026-07-30 16:00:00+07', 'Đã thông báo cho Creator điều chỉnh và bổ sung câu hỏi mới thay thế.', TIMESTAMPTZ '2026-07-30 10:15:00+07'),
    (6, 52, 3, 'CHAPTER',  'CH-04',    'COPYRIGHT',        'Nội dung hình ảnh sơ đồ kiến trúc tham chiếu từ tài liệu giảng dạy chính thức.', 'RESOLVED', 5, TIMESTAMPTZ '2026-07-29 11:30:00+07', 'Creator đã xuất trình giấy phép trích dẫn nguồn hợp lệ.', TIMESTAMPTZ '2026-07-29 09:00:00+07'),

    -- DISMISSED (Bác bỏ)
    (7, 68, 4, 'VERSION',  NULL,       'OTHER',            'Ý kiến cá nhân cho rằng mức giá 50 Coin là cao so với số lượng câu hỏi.', 'DISMISSED', 5, TIMESTAMPTZ '2026-07-28 15:45:00+07', 'Mức giá do Creator tự thiết lập và phù hợp với khung giá quy định trên Marketplace.', TIMESTAMPTZ '2026-07-28 14:00:00+07')
) AS r(row_no, reporter_no, pack_version_no, target_type, target_ref, category, description, status, reviewed_by_no, reviewed_at, resolution_note, created_at);

INSERT INTO marketplace_content_reports (
    report_id, reporter_id, pack_version_id, target_type, target_ref, category,
    description, status, reviewed_by, reviewed_at, resolution_note,
    created_at, updated_at
)
SELECT v66_uuid('report:' || rep.row_no), rep.reporter_id, rep.pack_version_id, rep.target_type, rep.target_ref, rep.category,
       rep.description, rep.status,
       CASE WHEN rep.reviewed_by_no IS NOT NULL THEN v66_v36_uuid('user:' || rep.reviewed_by_no)::text ELSE NULL END,
       rep.reviewed_at, rep.resolution_note, rep.created_at, COALESCE(rep.reviewed_at, rep.created_at)
FROM v66_content_reports rep;

-- Postcondition Assertion to guarantee consistency
DO $$
BEGIN
    IF (SELECT count(*) FROM marketplace_pack_versions WHERE status IN ('SUBMITTED', 'UNDER_REVIEW')) < 3
       OR (SELECT count(*) FROM marketplace_content_reports WHERE status = 'OPEN') < 3 THEN
        RAISE EXCEPTION 'V66 postcondition failed; marketplace moderation and report seeding is incomplete';
    END IF;
END $$;

DROP FUNCTION v66_v36_uuid(TEXT);
DROP FUNCTION v66_uuid(TEXT);
