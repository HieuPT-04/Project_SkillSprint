-- Expanded, transparent presentation scenario for the community moderation queue.
-- Records remain linked to real application tables so queue totals, per-item
-- counters, comments and reports can be reviewed together.

CREATE FUNCTION v73_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v73:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v73:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v73:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v73:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v73:' || seed), 21, 12))::uuid;
$$;

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
        LIMIT 6
    ) selected_users;

    v_user_count := COALESCE(array_length(v_user_ids, 1), 0);
    IF v_user_count = 0 THEN
        RAISE EXCEPTION 'V73 requires at least one active user for the moderation queue';
    END IF;

    INSERT INTO community_posts (
        post_id, user_id, content, hashtags, status, like_count, comment_count, report_count, created_at, updated_at
    )
    SELECT
        v73_uuid('post:' || item.row_no),
        v_user_ids[1 + ((item.row_no - 1) % v_user_count)],
        item.content, item.hashtags, 'PENDING_MODERATION', 0, 0, 0,
        CURRENT_TIMESTAMP - item.created_offset,
        CURRENT_TIMESTAMP - item.created_offset
    FROM (VALUES
        (1, 'Mình đang lập kế hoạch ôn Git theo từng buổi. Mọi người thường bắt đầu từ phần nào để dễ thực hành nhất?', '#git #versioncontrol #hoctap', INTERVAL '3 days 6 hours'),
        (2, 'Sau khi hoàn thành vài bài Java cơ bản, mình muốn tìm bài tập nhỏ để luyện tư duy hướng đối tượng.', '#java #oop #practice', INTERVAL '2 days 18 hours'),
        (3, 'Chia sẻ checklist trước khi nộp bài nhóm: chạy test, kiểm tra README và thống nhất cách đặt tên nhánh.', '#teamwork #testing #gitflow', INTERVAL '1 day 22 hours'),
        (4, 'Mình thử ghi lại thời gian học theo phiên 50 phút và thấy dễ duy trì mục tiêu tuần hơn.', '#studyhabit #focus #productivity', INTERVAL '1 day 14 hours'),
        (5, 'Có bạn nào gợi ý tài liệu dễ hiểu về REST API và cách thiết kế endpoint cho người mới không?', '#api #backend #question', INTERVAL '1 day 3 hours'),
        (6, 'Mình đã tổng hợp các lệnh Docker hay dùng khi chạy dự án local, mong mọi người góp ý thêm.', '#docker #devops #notes', INTERVAL '18 hours'),
        (7, 'Khi làm quiz SQL, mình thường sai ở phần JOIN. Có cách nào luyện theo từng dạng bài không?', '#sql #quiz #learning', INTERVAL '11 hours'),
        (8, 'Mình muốn tạo một lộ trình React bốn tuần, ưu tiên các chủ đề cần dùng nhiều khi làm project.', '#react #frontend #roadmap', INTERVAL '6 hours'),
        (9, 'Mọi người thường lưu tài liệu học theo thư mục hay công cụ ghi chú nào để tìm lại nhanh?', '#notes #knowledge #question', INTERVAL '2 hours 20 minutes')
    ) AS item(row_no, content, hashtags, created_offset)
    ON CONFLICT (post_id) DO NOTHING;

    INSERT INTO post_likes (like_id, post_id, user_id, created_at, updated_at)
    SELECT
        v73_uuid('like:' || post_no || ':' || liker_no),
        v73_uuid('post:' || post_no),
        v_user_ids[1 + ((post_no + liker_no - 1) % v_user_count)],
        CURRENT_TIMESTAMP - ((post_no * 55 + liker_no * 9) * INTERVAL '1 minute'),
        CURRENT_TIMESTAMP - ((post_no * 55 + liker_no * 9) * INTERVAL '1 minute')
    FROM generate_series(1, 9) AS post_no
    CROSS JOIN generate_series(1, LEAST(3, v_user_count)) AS liker_no
    ON CONFLICT (post_id, user_id) DO NOTHING;

    INSERT INTO post_comments (
        comment_id, post_id, user_id, content, status, report_count, created_at, updated_at
    )
    SELECT
        v73_uuid('comment:' || item.row_no),
        v73_uuid('post:' || item.post_no),
        v_user_ids[1 + ((item.row_no + 1) % v_user_count)],
        item.content, 'PENDING_MODERATION', 0,
        CURRENT_TIMESTAMP - item.created_offset,
        CURRENT_TIMESTAMP - item.created_offset
    FROM (VALUES
        (1, 1, 'Bạn có thể bắt đầu bằng commit, branch và pull request rồi làm một repository nhỏ để luyện.', INTERVAL '2 days 21 hours'),
        (2, 2, 'Các bài quản lý danh sách công việc hoặc thư viện nhỏ sẽ giúp luyện class và interface khá tốt.', INTERVAL '2 days 9 hours'),
        (3, 3, 'Nhóm mình cũng thêm bước review thay đổi trước khi merge để hạn chế lỗi vặt.', INTERVAL '1 day 17 hours'),
        (4, 5, 'Mình thấy bắt đầu bằng cách đặt tên resource rõ ràng rồi mới đến phân trang và filter sẽ dễ hơn.', INTERVAL '22 hours'),
        (5, 6, 'Nếu có thể, bạn thêm cả ví dụ docker compose để người mới chạy nhanh hơn.', INTERVAL '14 hours'),
        (6, 7, 'Mình luyện theo thứ tự SELECT, JOIN rồi đến GROUP BY; làm lại câu sai sau mỗi buổi.', INTERVAL '7 hours'),
        (7, 8, 'Bạn nên dành một tuần cho component và state, sau đó mới đến routing và gọi API.', INTERVAL '3 hours 10 minutes')
    ) AS item(row_no, post_no, content, created_offset)
    ON CONFLICT (comment_id) DO NOTHING;

    INSERT INTO content_reports (
        report_id, target_type, target_id, reporter_id, reason, status, created_at, updated_at
    ) VALUES
        (v73_uuid('report:post:3'), 'POST', v73_uuid('post:3'), v_user_ids[1], 'Cần kiểm tra nội dung trước khi công khai theo luồng kiểm duyệt.', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '1 day 8 hours', CURRENT_TIMESTAMP - INTERVAL '1 day 8 hours'),
        (v73_uuid('report:post:7'), 'POST', v73_uuid('post:7'), v_user_ids[1 + (1 % v_user_count)], 'Cần xác minh ngữ cảnh nội dung trước khi hiển thị.', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '9 hours', CURRENT_TIMESTAMP - INTERVAL '9 hours'),
        (v73_uuid('report:post:9'), 'POST', v73_uuid('post:9'), v_user_ids[1 + (2 % v_user_count)], 'Đề nghị quản trị viên xem lại bài đăng theo quy trình.', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '1 hour 40 minutes', CURRENT_TIMESTAMP - INTERVAL '1 hour 40 minutes'),
        (v73_uuid('report:comment:2'), 'COMMENT', v73_uuid('comment:2'), v_user_ids[1 + (3 % v_user_count)], 'Cần xem lại bình luận trước khi duyệt hiển thị.', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '2 days 3 hours', CURRENT_TIMESTAMP - INTERVAL '2 days 3 hours'),
        (v73_uuid('report:comment:5'), 'COMMENT', v73_uuid('comment:5'), v_user_ids[1 + (4 % v_user_count)], 'Bình luận được đưa vào hàng đợi kiểm tra.', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '12 hours', CURRENT_TIMESTAMP - INTERVAL '12 hours')
    ON CONFLICT (report_id) DO NOTHING;

    UPDATE community_posts post
    SET like_count = (SELECT count(*) FROM post_likes liked WHERE liked.post_id = post.post_id),
        comment_count = (SELECT count(*) FROM post_comments comment WHERE comment.post_id = post.post_id AND comment.status = 'VISIBLE'),
        report_count = (SELECT count(*) FROM content_reports report WHERE report.target_type = 'POST' AND report.target_id = post.post_id),
        updated_at = CURRENT_TIMESTAMP;

    UPDATE post_comments comment
    SET report_count = (SELECT count(*) FROM content_reports report WHERE report.target_type = 'COMMENT' AND report.target_id = comment.comment_id),
        updated_at = CURRENT_TIMESTAMP;

    IF (SELECT count(*) FROM community_posts WHERE post_id IN (SELECT v73_uuid('post:' || n) FROM generate_series(1, 9) AS n) AND status = 'PENDING_MODERATION') <> 9
       OR (SELECT count(*) FROM post_comments WHERE comment_id IN (SELECT v73_uuid('comment:' || n) FROM generate_series(1, 7) AS n) AND status = 'PENDING_MODERATION') <> 7
       OR (SELECT count(*) FROM content_reports WHERE report_id IN (
            v73_uuid('report:post:3'), v73_uuid('report:post:7'), v73_uuid('report:post:9'),
            v73_uuid('report:comment:2'), v73_uuid('report:comment:5')
       ) AND status = 'PENDING') <> 5 THEN
        RAISE EXCEPTION 'V73 postcondition failed; moderation queue scenario is incomplete';
    END IF;
END $$;

DROP FUNCTION IF EXISTS v73_uuid(text);
