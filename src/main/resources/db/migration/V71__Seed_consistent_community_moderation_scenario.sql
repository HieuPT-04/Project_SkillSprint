-- Transparent sample scenario for reviewing the community and moderation flows.
-- The records are linked through the same post, comment, like and report tables
-- used by the application; counters are reconciled from those source rows.

CREATE FUNCTION v71_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v71:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v71:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v71:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v71:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v71:' || seed), 21, 12))::uuid;
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
        RAISE EXCEPTION 'V71 requires at least one active user for the community scenario';
    END IF;

    INSERT INTO community_posts (
        post_id, user_id, content, hashtags, status, like_count, comment_count, report_count, created_at, updated_at
    )
    SELECT
        v71_uuid('post:' || item.row_no),
        v_user_ids[1 + ((item.row_no - 1) % v_user_count)],
        item.content,
        item.hashtags,
        item.status,
        0, 0, 0,
        CURRENT_TIMESTAMP - item.created_offset,
        CURRENT_TIMESTAMP - item.created_offset
    FROM (VALUES
        (1, 'Mình vừa tách kế hoạch học Spring Boot thành các mục nhỏ theo từng tuần. Cách này giúp theo dõi phần API và kiểm thử rõ hơn.', '#springboot #backend #kehoachhoc', 'APPROVED', INTERVAL '3 days 4 hours'),
        (2, 'Sau khi làm lại bộ câu hỏi SQL, mình nhận ra nên ghi chú cả nguyên nhân chọn đáp án sai để ôn lại nhanh hơn.', '#sql #quiz #onthi', 'APPROVED', INTERVAL '2 days 7 hours'),
        (3, 'Chia sẻ cách mọi người giữ nhịp học khi lịch làm việc bận nhé. Mình đang thử khung 45 phút tập trung và 10 phút nghỉ.', '#pomodoro #productivity #studyhabit', 'APPROVED', INTERVAL '1 day 5 hours'),
        (4, 'Mình đã hoàn thành phần cơ bản của roadmap React và đang tổng hợp lại các lỗi thường gặp khi dùng useEffect.', '#react #roadmap #frontend', 'APPROVED', INTERVAL '19 hours'),
        (5, 'Có bạn nào có tài liệu nhập môn Docker dễ thực hành không? Mình muốn bắt đầu bằng một project nhỏ.', '#docker #devops #hoctap', 'APPROVED', INTERVAL '8 hours'),
        (6, 'Mình muốn hỏi cách tổ chức thư mục cho bài tập nhóm để mọi người review code thuận tiện hơn.', '#teamwork #git #question', 'PENDING_MODERATION', INTERVAL '2 hours 10 minutes'),
        (7, 'Xin gợi ý lộ trình ôn SQL trong hai tuần trước khi phỏng vấn thực tập.', '#sql #interview #question', 'PENDING_MODERATION', INTERVAL '1 hour 25 minutes'),
        (8, 'Mình tổng hợp một số nguồn học thuật toán; nhờ mọi người góp ý xem nội dung có phù hợp cho người mới không.', '#dsa #learning #resource', 'PENDING_MODERATION', INTERVAL '42 minutes')
    ) AS item(row_no, content, hashtags, status, created_offset)
    ON CONFLICT (post_id) DO NOTHING;

    INSERT INTO post_comments (
        comment_id, post_id, user_id, content, status, report_count, created_at, updated_at
    )
    SELECT
        v71_uuid('comment:' || item.row_no),
        v71_uuid('post:' || item.post_no),
        v_user_ids[1 + ((item.row_no - 1) % v_user_count)],
        item.content,
        item.status,
        0,
        CURRENT_TIMESTAMP - item.created_offset,
        CURRENT_TIMESTAMP - item.created_offset
    FROM (VALUES
        (1, 1, 'Mình cũng chia theo tuần, cuối tuần dành một buổi để xem lại phần chưa hiểu.', 'VISIBLE', INTERVAL '2 days 20 hours'),
        (2, 1, 'Nếu có checklist theo endpoint thì lúc kiểm thử sẽ dễ bám hơn nhiều.', 'VISIBLE', INTERVAL '2 days 12 hours'),
        (3, 2, 'Mình dùng một bảng lỗi sai riêng, sau mỗi lần làm lại chỉ xem những câu đã đánh dấu.', 'VISIBLE', INTERVAL '1 day 19 hours'),
        (4, 3, 'Khung 45/10 hợp với mình hơn Pomodoro 25 phút vì đỡ bị ngắt mạch khi đang đọc tài liệu.', 'VISIBLE', INTERVAL '21 hours'),
        (5, 4, 'Bạn có thể ghi lại dependency array cạnh từng useEffect, lúc review sẽ nhìn ra vấn đề nhanh hơn.', 'VISIBLE', INTERVAL '15 hours'),
        (6, 5, 'Docker Getting Started và bài thực hành compose nhỏ là điểm bắt đầu ổn.', 'VISIBLE', INTERVAL '5 hours'),
        (7, 6, 'Mình có một vài gợi ý về cấu trúc repository, chờ quản trị viên duyệt bài nhé.', 'PENDING_MODERATION', INTERVAL '1 hour 12 minutes'),
        (8, 7, 'Bạn nên xen kẽ truy vấn cơ bản với bài window function để đỡ bị quá tải.', 'PENDING_MODERATION', INTERVAL '48 minutes')
    ) AS item(row_no, post_no, content, status, created_offset)
    ON CONFLICT (comment_id) DO NOTHING;

    INSERT INTO post_likes (like_id, post_id, user_id, created_at, updated_at)
    SELECT
        v71_uuid('like:' || post_no || ':' || liker.ordinality),
        v71_uuid('post:' || post_no),
        liker.user_id,
        CURRENT_TIMESTAMP - ((post_no * 4 + liker.ordinality) * INTERVAL '31 minutes'),
        CURRENT_TIMESTAMP - ((post_no * 4 + liker.ordinality) * INTERVAL '31 minutes')
    FROM generate_series(1, 5) AS post_no
    CROSS JOIN unnest(v_user_ids) WITH ORDINALITY AS liker(user_id, ordinality)
    ON CONFLICT (post_id, user_id) DO NOTHING;

    INSERT INTO content_reports (
        report_id, target_type, target_id, reporter_id, reason, status, created_at, updated_at
    ) VALUES
        (
            v71_uuid('report:post:6'), 'POST', v71_uuid('post:6'), v_user_ids[1],
            'Cần kiểm tra thêm ngữ cảnh trước khi công khai bài đăng.', 'PENDING',
            CURRENT_TIMESTAMP - INTERVAL '58 minutes', CURRENT_TIMESTAMP - INTERVAL '58 minutes'
        ),
        (
            v71_uuid('report:comment:7'), 'COMMENT', v71_uuid('comment:7'), v_user_ids[v_user_count],
            'Cần xác minh nội dung bình luận trước khi hiển thị.', 'PENDING',
            CURRENT_TIMESTAMP - INTERVAL '34 minutes', CURRENT_TIMESTAMP - INTERVAL '34 minutes'
        )
    ON CONFLICT (report_id) DO NOTHING;

    UPDATE community_posts post
    SET
        comment_count = (SELECT count(*) FROM post_comments comment WHERE comment.post_id = post.post_id AND comment.status = 'VISIBLE'),
        like_count = (SELECT count(*) FROM post_likes liked WHERE liked.post_id = post.post_id),
        report_count = (SELECT count(*) FROM content_reports report WHERE report.target_type = 'POST' AND report.target_id = post.post_id),
        updated_at = CURRENT_TIMESTAMP;

    UPDATE post_comments comment
    SET
        report_count = (SELECT count(*) FROM content_reports report WHERE report.target_type = 'COMMENT' AND report.target_id = comment.comment_id),
        updated_at = CURRENT_TIMESTAMP;

    IF (SELECT count(*) FROM community_posts WHERE post_id IN (SELECT v71_uuid('post:' || n) FROM generate_series(6, 8) AS n) AND status = 'PENDING_MODERATION') <> 3
       OR (SELECT count(*) FROM post_comments WHERE comment_id IN (SELECT v71_uuid('comment:' || n) FROM generate_series(7, 8) AS n) AND status = 'PENDING_MODERATION') <> 2
       OR (SELECT count(*) FROM content_reports WHERE report_id IN (v71_uuid('report:post:6'), v71_uuid('report:comment:7')) AND status = 'PENDING') <> 2 THEN
        RAISE EXCEPTION 'V71 postcondition failed; community moderation scenario is incomplete';
    END IF;
END $$;

DROP FUNCTION IF EXISTS v71_uuid(text);
