-- Add a varied feedback queue for the V36 learner cohort. The queue keeps a
-- realistic operating mix: new reports, items being investigated, and replies
-- that have already been resolved by an existing active administrator.

CREATE FUNCTION v38_seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v38:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v38:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v38:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v38:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v38:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v38_v36_uuid(seed TEXT)
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
    v_admin_id VARCHAR(100);
    v_row INTEGER;
    v_user_id VARCHAR(100);
    v_status TEXT;
    v_created_at TIMESTAMPTZ;
    v_replied_at TIMESTAMPTZ;
    v_type TEXT;
    v_title TEXT;
    v_content TEXT;
    v_related_url TEXT;
    v_admin_note TEXT;
    v_admin_reply TEXT;
BEGIN
    SELECT user_role.user_id
    INTO v_admin_id
    FROM user_roles user_role
    JOIN roles role ON role.role_id = user_role.role_id
    JOIN users app_user ON app_user.user_id = user_role.user_id
    WHERE role.role_name = 'ADMIN'
      AND app_user.status = 'ACTIVE'
    ORDER BY user_role.granted_at NULLS LAST, user_role.user_id
    LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'V38 requires one existing ACTIVE ADMIN user to reply to seeded feedback';
    END IF;

    IF EXISTS (SELECT 1 FROM feedbacks WHERE feedback_id IN (
            SELECT v38_seed_uuid('feedback:' || n) FROM generate_series(1, 24) AS n
        )) THEN
        RAISE EXCEPTION 'V38 feedback rows already exist; do not apply this migration to a partially seeded database';
    END IF;

    FOR v_row IN 1..24 LOOP
        v_user_id := v38_v36_uuid('user:' || (((v_row * 7 - 1) % 100) + 1))::text;
        v_created_at := TIMESTAMPTZ '2026-07-27 16:20:00+07'
            - ((v_row - 1) * INTERVAL '22 hours')
            - ((v_row * 17 % 73) * INTERVAL '5 minutes');
        v_status := CASE
            WHEN v_row <= 6 THEN 'OPEN'
            WHEN v_row <= 12 THEN 'IN_PROGRESS'
            ELSE 'CLOSED'
        END;
        v_type := (ARRAY[
            'BUG', 'IMPROVEMENT', 'QUESTION', 'OTHER',
            'BUG', 'IMPROVEMENT', 'QUESTION', 'BUG',
            'IMPROVEMENT', 'OTHER', 'QUESTION', 'BUG',
            'IMPROVEMENT', 'BUG', 'QUESTION', 'OTHER',
            'BUG', 'IMPROVEMENT', 'QUESTION', 'BUG',
            'OTHER', 'IMPROVEMENT', 'BUG', 'QUESTION'
        ])[v_row];
        v_title := (ARRAY[
            'Nút lưu mục tiêu bị che trên màn hình nhỏ',
            'Đề xuất nhắc lại lịch học vào cuối ngày',
            'Có thể đổi múi giờ cho lịch học không?',
            'Cảm ơn tính năng chia nhỏ mục tiêu',
            'Trang chi tiết quiz tải chậm sau khi làm xong',
            'Mong muốn lọc roadmap theo chủ đề',
            'XP được cộng ở thời điểm nào?',
            'Không mở được tài liệu PDF trên Safari iPhone',
            'Đề xuất hiển thị streak ở trang chủ',
            'Góp ý về giọng đọc của AI Tutor',
            'Gói Basic có dùng được AI Tutor không?',
            'Lịch Pomodoro bị lệch sau khi đổi ngày',
            'Đề xuất thêm ví dụ cho phần SQL Join',
            'Thông báo hoàn thành task gửi trùng',
            'Có thể xuất lịch học ra Google Calendar không?',
            'Chia sẻ trải nghiệm học React tuần đầu',
            'Nút quay lại bị mất sau khi nộp quiz',
            'Đề xuất lưu bộ lọc ở trang marketplace',
            'Điểm quiz 90% được tính bao nhiêu XP?',
            'Biểu đồ thời gian học chưa hiển thị hôm nay',
            'Giao diện workspace khá dễ dùng',
            'Đề xuất thêm Dark Mode cho trang học',
            'Ảnh đại diện chưa cập nhật sau khi tải lên',
            'Làm sao để hủy gói đang dùng?'
        ])[v_row];
        v_content := (ARRAY[
            'Trên điện thoại Android, khi mở form tạo mục tiêu thì nút Lưu nằm sát cạnh dưới và khó bấm. Mong đội ngũ kiểm tra giúp.',
            'Mình hay học sau giờ làm, nếu có thông báo nhắc các task chưa hoàn thành khoảng 20 giờ thì sẽ dễ duy trì hơn.',
            'Mình sắp đi công tác vài tuần. Phần lịch học có cho phép đổi sang múi giờ khác mà không làm thay đổi các task đã tạo không?',
            'Tính năng tách mục tiêu lớn thành các phiên ngắn giúp mình bắt đầu dễ hơn. Cảm ơn đội ngũ đã làm phần này.',
            'Sau khi nộp quiz 20 câu, trang kết quả mất khá lâu mới hiện ra. Mạng của mình vẫn ổn với các trang khác.',
            'Hiện tại mình có nhiều roadmap, mong có thêm bộ lọc theo chủ đề hoặc trạng thái để tìm lại nhanh hơn.',
            'Mình vừa hoàn thành một step và thấy XP tăng lên. Cho mình hỏi XP từ quiz và roadmap có được cộng ngay không?',
            'Tài liệu PDF mở bình thường trên laptop nhưng Safari iPhone chỉ hiện màn hình trắng. Mình đã thử tải lại vài lần.',
            'Nếu streak được hiển thị ngay trang chủ thì mình sẽ dễ theo dõi chuỗi học liên tục hơn.',
            'Phần giải thích của AI Tutor khá rõ. Nếu có thêm lựa chọn giọng đọc chậm hơn cho đoạn dài thì sẽ dễ nghe hơn.',
            'Mình đang dùng Free và thấy nút AI Tutor bị khóa. Gói Basic có mở tính năng này hay cần nâng lên Premium?',
            'Sau khi kéo task Pomodoro sang ngày hôm sau, lịch đôi lúc vẫn hiển thị task ở cả hai ngày.',
            'Phần SQL Join có thể thêm một ví dụ dữ liệu nhỏ kèm kết quả truy vấn để người mới dễ theo dõi hơn không?',
            'Mỗi lần hoàn thành một task, mình nhận được hai thông báo giống nhau trong khoảng vài giây.',
            'Mình muốn đồng bộ các phiên học đã lên lịch sang Google Calendar để không bị trùng với lịch cá nhân.',
            'Roadmap React có độ dài vừa phải và quiz sau mỗi phần giúp mình ôn lại kiến thức khá hiệu quả.',
            'Sau khi nộp quiz trên mobile, nút quay lại không còn xuất hiện nên mình phải quay về trang chủ rồi mở lại workspace.',
            'Khi marketplace có nhiều bộ đề, nếu hệ thống nhớ bộ lọc môn học và mức giá lần trước thì sẽ tiện hơn.',
            'Mình đạt 90 phần trăm ở quiz. Mình muốn xác nhận đây là mốc nhận XP xuất sắc hay chỉ là XP đạt quiz.',
            'Mình đã học một phiên sáng nay nhưng biểu đồ tổng quan vẫn đang ghi nhận 0 phút cho ngày hôm nay.',
            'Phần workspace và checklist rõ ràng, mình mới dùng vài ngày nhưng đã dễ theo dõi tiến độ hơn trước.',
            'Buổi tối giao diện sáng khá nhiều. Nếu có Dark Mode cho màn học và quiz thì trải nghiệm sẽ thoải mái hơn.',
            'Mình đã tải ảnh mới lên hồ sơ nhưng sau khi lưu thì avatar cũ vẫn hiển thị ở góc trên.',
            'Mình không thấy nút hủy gói trong phần tài khoản. Nhờ hướng dẫn giúp quy trình và thời điểm gói hết hiệu lực.'
        ])[v_row];
        v_related_url := (ARRAY[
            '/goals/new', '/calendar', '/calendar', NULL,
            '/quizzes/result', '/roadmaps', '/leaderboard', '/materials',
            '/dashboard', '/ai-tutor', '/subscription', '/calendar',
            '/roadmaps/sql-join', '/notifications', '/calendar', '/roadmaps',
            '/quizzes/result', '/marketplace', '/leaderboard', '/dashboard',
            '/study', '/study', '/profile', '/subscription'
        ])[v_row];

        IF v_status = 'OPEN' THEN
            v_admin_note := NULL;
            v_admin_reply := NULL;
            v_replied_at := NULL;
        ELSIF v_status = 'IN_PROGRESS' THEN
            v_admin_note := 'Đã ghi nhận, đang tái hiện và phân công kiểm tra.';
            v_admin_reply := (ARRAY[
                'Cảm ơn bạn đã báo chi tiết. Đội ngũ đang kiểm tra nguyên nhân và sẽ cập nhật ngay khi có kết quả.',
                'Góp ý của bạn đã được chuyển vào danh sách cải tiến cho phiên bản kế tiếp.',
                'Chúng tôi đã ghi nhận câu hỏi và đang rà soát lại phần hướng dẫn hiển thị trong ứng dụng.',
                'Đội ngũ đã tái hiện được vấn đề trên một số thiết bị và đang xử lý.',
                'Cảm ơn bạn đã chia sẻ. Chúng tôi đang đánh giá phương án phù hợp để bổ sung.',
                'Vấn đề đã được ghi nhận và đang được kiểm tra cùng nhóm kỹ thuật.'
            ])[v_row - 6];
            v_replied_at := v_created_at + INTERVAL '6 hours' + ((v_row % 4) * INTERVAL '2 hours');
        ELSE
            v_admin_note := 'Đã kiểm tra và hoàn tất xử lý phản hồi.';
            v_admin_reply := (ARRAY[
                'Cảm ơn bạn đã phản hồi. Đội ngũ đã tối ưu lại luồng hiển thị và nhờ bạn thử cập nhật phiên bản mới nhất.',
                'Đề xuất đã được ghi nhận vào backlog nội dung. Chúng tôi sẽ bổ sung ví dụ trong đợt cập nhật tiếp theo.',
                'Mốc XP đã được làm rõ trong phần hướng dẫn. Điểm sẽ được cộng ngay sau khi kết quả hợp lệ được ghi nhận.',
                'Chúng tôi đã điều chỉnh lại lịch xử lý task khi thay đổi ngày. Cảm ơn bạn đã giúp phát hiện sớm.',
                'Tính năng này đã được cập nhật trong khu vực cài đặt lịch học. Bạn có thể thử kết nối lại lịch cá nhân.',
                'Rất vui vì trải nghiệm học đáp ứng nhu cầu của bạn. Cảm ơn bạn đã chia sẻ góp ý tích cực.',
                'Đội ngũ đã khắc phục thao tác quay lại sau khi nộp quiz trên màn hình nhỏ.',
                'Bộ lọc marketplace hiện được giữ lại trong phiên truy cập để việc tìm kiếm thuận tiện hơn.',
                'Chúng tôi đã cập nhật tooltip XP ở màn kết quả quiz để giải thích rõ các mốc điểm.',
                'Dữ liệu phiên học đã được đồng bộ lại. Nếu bạn vẫn gặp tình trạng này, hãy gửi thời điểm xảy ra để chúng tôi kiểm tra thêm.',
                'Cảm ơn bạn. Đề xuất Dark Mode đã được đưa vào kế hoạch cải tiến giao diện.',
                'Hướng dẫn hủy gói đã được bổ sung trong trang tài khoản. Gói đang dùng sẽ giữ quyền lợi đến hết chu kỳ đã thanh toán.'
            ])[v_row - 12];
            v_replied_at := v_created_at + INTERVAL '1 day' + ((v_row % 6) * INTERVAL '3 hours');
        END IF;

        INSERT INTO feedbacks (
            feedback_id, user_id, type, title, content, related_url, image_object_key,
            status, admin_note, admin_reply, replied_by_user_id, replied_at, created_at, updated_at
        ) VALUES (
            v38_seed_uuid('feedback:' || v_row), v_user_id, v_type, v_title, v_content, v_related_url, NULL,
            v_status, v_admin_note, v_admin_reply,
            CASE WHEN v_replied_at IS NULL THEN NULL ELSE v_admin_id END, v_replied_at,
            v_created_at, COALESCE(v_replied_at, v_created_at)
        );
    END LOOP;

    IF (SELECT count(*) FROM feedbacks WHERE feedback_id IN (
            SELECT v38_seed_uuid('feedback:' || n) FROM generate_series(1, 24) AS n
        )) <> 24
       OR (SELECT count(*) FROM feedbacks WHERE feedback_id IN (
            SELECT v38_seed_uuid('feedback:' || n) FROM generate_series(1, 24) AS n
        ) AND status = 'OPEN') <> 6
       OR (SELECT count(*) FROM feedbacks WHERE feedback_id IN (
            SELECT v38_seed_uuid('feedback:' || n) FROM generate_series(1, 24) AS n
        ) AND status = 'IN_PROGRESS') <> 6
       OR (SELECT count(*) FROM feedbacks WHERE feedback_id IN (
            SELECT v38_seed_uuid('feedback:' || n) FROM generate_series(1, 24) AS n
        ) AND status = 'CLOSED') <> 12
       OR (SELECT count(*) FROM feedbacks WHERE feedback_id IN (
            SELECT v38_seed_uuid('feedback:' || n) FROM generate_series(1, 24) AS n
        ) AND ((status = 'OPEN' AND replied_at IS NOT NULL)
             OR (status IN ('IN_PROGRESS', 'CLOSED') AND (replied_at IS NULL OR replied_by_user_id <> v_admin_id)))) > 0 THEN
        RAISE EXCEPTION 'V38 postcondition failed; realistic feedback seed is rolled back';
    END IF;
END $$;

DROP FUNCTION v38_v36_uuid(TEXT);
DROP FUNCTION v38_seed_uuid(TEXT);
