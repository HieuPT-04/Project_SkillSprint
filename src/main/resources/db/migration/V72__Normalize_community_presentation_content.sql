-- Presentation-environment content normalization.
-- This keeps the seeded community and support flows useful for review without
-- presenting the records as production activity. The application labels the
-- environment separately; no production records are created by this migration.

CREATE FUNCTION v72_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v72:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v72:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v72:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v72:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v72:' || seed), 21, 12))::uuid;
$$;

-- Replace old V28 placeholder text with room-specific descriptions.
UPDATE community_rooms
SET description = CASE name
    WHEN 'Java & Spring Boot' THEN 'Trao đổi lộ trình Java, Spring Boot và các bài tập backend thực hành.'
    WHEN 'TOEIC & IELTS' THEN 'Cùng luyện kỹ năng tiếng Anh, chia sẻ tài liệu và kế hoạch ôn tập.'
    WHEN 'ReactJS & Next.js' THEN 'Thảo luận React, Next.js, UI và các vấn đề thường gặp khi triển khai.'
    WHEN 'IT & Computer Science' THEN 'Không gian trao đổi nền tảng khoa học máy tính, thuật toán và hệ thống.'
    WHEN 'Pomodoro 500 giờ' THEN 'Cùng duy trì nhịp học tập trung, theo dõi mục tiêu và chia sẻ kinh nghiệm.'
    WHEN 'Học viên SkillSprint/FPT' THEN 'Kết nối học viên, trao đổi lộ trình học và hỗ trợ nhau hoàn thành mục tiêu.'
    ELSE 'Không gian trao đổi kiến thức và hỗ trợ học tập theo chủ đề.'
END,
updated_at = CURRENT_TIMESTAMP
WHERE description ILIKE '%demo V28%';

UPDATE community_chat_messages
SET raw_content = regexp_replace(raw_content, 'demo V28', 'học tập', 'gi'),
    masked_content = regexp_replace(masked_content, 'demo V28', 'học tập', 'gi')
WHERE raw_content ILIKE '%demo V28%'
   OR masked_content ILIKE '%demo V28%';

UPDATE community_posts
SET content = regexp_replace(content, 'demo V28', 'học tập', 'gi'),
    updated_at = CURRENT_TIMESTAMP
WHERE content ILIKE '%demo V28%';

UPDATE post_comments
SET content = regexp_replace(content, 'demo V28', 'học tập', 'gi'),
    updated_at = CURRENT_TIMESTAMP
WHERE content ILIKE '%demo V28%';

UPDATE feedbacks
SET content = CASE title
    WHEN 'Đề xuất Dark Mode' THEN 'Mong có thêm giao diện tối để học vào buổi tối dễ chịu hơn.'
    WHEN 'Khen AI Tutor' THEN 'Phần gợi ý học tập hữu ích; mong tiếp tục bổ sung ví dụ theo từng mức độ.'
    WHEN 'Báo lỗi Pomodoro mobile' THEN 'Trên màn hình nhỏ, bộ đếm Pomodoro đôi khi không cập nhật ngay sau khi quay lại ứng dụng.'
    WHEN 'Phản hồi về thanh toán SePay' THEN 'Cần hướng dẫn rõ hơn về thời gian đối soát sau khi hoàn tất thanh toán.'
    ELSE regexp_replace(content, 'demo V28', 'trải nghiệm sử dụng', 'gi')
END,
updated_at = CURRENT_TIMESTAMP
WHERE content ILIKE '%demo V28%';

DO $$
DECLARE
    v_user_ids VARCHAR(100)[];
    v_user_count INTEGER;
BEGIN
    SELECT array_agg(user_id ORDER BY created_at ASC, user_id ASC)
    INTO v_user_ids
    FROM (
        SELECT user_id, created_at
        FROM users
        WHERE status = 'ACTIVE'
        ORDER BY created_at ASC, user_id ASC
        LIMIT 5
    ) selected_users;

    v_user_count := COALESCE(array_length(v_user_ids, 1), 0);
    IF v_user_count = 0 THEN
        RAISE EXCEPTION 'V72 requires at least one active user for the feedback scenario';
    END IF;

    INSERT INTO feedbacks (
        feedback_id, user_id, type, title, content, related_url, image_object_key, status,
        admin_note, admin_reply, replied_by_user_id, replied_at, created_at, updated_at
    )
    SELECT
        v72_uuid('feedback:' || item.row_no),
        v_user_ids[1 + ((item.row_no - 1) % v_user_count)],
        item.type, item.title, item.content, item.related_url, NULL, item.status,
        item.admin_note, item.admin_reply, NULL,
        CASE WHEN item.status = 'OPEN' THEN NULL ELSE item.created_at + INTERVAL '5 hours' END,
        item.created_at,
        CASE WHEN item.status = 'OPEN' THEN item.created_at ELSE item.created_at + INTERVAL '5 hours' END
    FROM (VALUES
        (1, 'QUESTION', 'Lịch sử học theo từng tháng', 'Mình muốn xem tổng thời gian học và số quiz hoàn thành theo từng tháng để tự theo dõi tiến độ.', '/dashboard', 'CLOSED', 'Đã chuyển yêu cầu sang nhóm sản phẩm.', 'Tính năng này đã được ghi nhận trong kế hoạch cải thiện báo cáo học tập.', TIMESTAMPTZ '2026-05-18 19:20:00+07'),
        (2, 'IMPROVEMENT', 'Lưu trạng thái bộ lọc khoá học', 'Sau khi quay lại danh sách khoá học, mình mong các bộ lọc trước đó vẫn được giữ để không phải chọn lại.', '/courses', 'CLOSED', 'Đã xác nhận nhu cầu và đưa vào danh sách cải tiến.', 'Cảm ơn góp ý. Nhóm đang rà soát cách lưu bộ lọc phù hợp trên cả web và mobile.', TIMESTAMPTZ '2026-06-09 10:35:00+07'),
        (3, 'BUG', 'Thông báo hoàn thành bài học bị chậm', 'Sau khi đánh dấu hoàn thành, thông báo đôi khi xuất hiện chậm vài giây dù tiến độ đã được lưu.', '/learning-path', 'IN_PROGRESS', 'Đã có thông tin môi trường và thời điểm xảy ra lỗi.', 'Đội kỹ thuật đang kiểm tra luồng đồng bộ tiến độ để khắc phục.', TIMESTAMPTZ '2026-07-02 21:10:00+07'),
        (4, 'QUESTION', 'Cách xem lại kết quả quiz đã làm', 'Mình cần mở lại kết quả các quiz gần đây để xem câu nào đã trả lời sai và đọc phần giải thích.', '/quiz-history', 'IN_PROGRESS', 'Đã chuyển câu hỏi đến nhóm phụ trách trải nghiệm học tập.', 'Bạn có thể xem lịch sử quiz; nhóm sẽ bổ sung hướng dẫn trực quan hơn trên màn hình này.', TIMESTAMPTZ '2026-07-19 14:25:00+07'),
        (5, 'BUG', 'Tệp đính kèm chưa hiện sau khi tải lên', 'Sau khi tải tài liệu PDF, danh sách tài nguyên chưa hiển thị ngay cho đến khi làm mới trang.', '/resources', 'OPEN', NULL, NULL, TIMESTAMPTZ '2026-08-03 08:40:00+07')
    ) AS item(row_no, type, title, content, related_url, status, admin_note, admin_reply, created_at)
    ON CONFLICT (feedback_id) DO NOTHING;

    IF (SELECT count(*)
        FROM feedbacks
        WHERE feedback_id IN (SELECT v72_uuid('feedback:' || n) FROM generate_series(1, 5) AS n)) <> 5 THEN
        RAISE EXCEPTION 'V72 postcondition failed; feedback presentation scenario is incomplete';
    END IF;
END $$;

DROP FUNCTION IF EXISTS v72_uuid(text);
