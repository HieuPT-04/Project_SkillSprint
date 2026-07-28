-- Enrich the visible demo experience with more reviews and feedback. Every
-- writer is selected only from the deterministic V28/V36 seed cohorts.

CREATE FUNCTION v49_seed_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v49:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v49:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v49:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v49:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v49:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v49_v28_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v49_v36_uuid(seed TEXT)
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
    v_pack RECORD;
    v_review_no INTEGER;
    v_reviewer_id VARCHAR(100);
    v_admin_id VARCHAR(100);
    v_feedback_no INTEGER;
    v_feedback_user_id VARCHAR(100);
    v_status TEXT;
    v_created_at TIMESTAMPTZ;
    v_rating INTEGER;
    v_review_comment TEXT;
    v_feedback_title TEXT;
    v_feedback_content TEXT;
    v_feedback_type TEXT;
    v_related_url TEXT;
    v_reply TEXT;
BEGIN
    SELECT ur.user_id
    INTO v_admin_id
    FROM user_roles ur
    JOIN roles r ON r.role_id = ur.role_id
    JOIN users u ON u.user_id = ur.user_id
    WHERE r.role_name = 'ADMIN'
      AND ur.workspace_id IS NULL
      AND u.status = 'ACTIVE'
    ORDER BY ur.granted_at NULLS LAST, ur.user_id
    LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'V49 requires one ACTIVE ADMIN user for feedback replies';
    END IF;

    -- Add twelve new, unique reviewers to each V43 catalogue pack. V28 already
    -- supplies three earlier reviews, yielding at least fifteen per pack.
    FOR v_pack IN
        SELECT item.item_id, version.version_id, item.subject
        FROM marketplace_items item
        JOIN marketplace_pack_versions version ON version.legacy_item_id = item.item_id
        WHERE item.item_id IN (
            v49_v28_uuid('item:3'),
            v49_v28_uuid('item:4'),
            v49_v28_uuid('item:5')
        )
          AND version.status = 'PUBLISHED'
        ORDER BY item.item_id
    LOOP
        FOR v_review_no IN 1..12 LOOP
            SELECT entitlement.buyer_id
            INTO v_reviewer_id
            FROM marketplace_entitlements entitlement
            WHERE entitlement.pack_version_id = v_pack.version_id
              AND entitlement.status = 'ACTIVE'
              AND NOT EXISTS (
                  SELECT 1
                  FROM marketplace_reviews review
                  WHERE review.user_id = entitlement.buyer_id
                    AND review.pack_version_id = v_pack.version_id
              )
            ORDER BY entitlement.granted_at, entitlement.buyer_id
            LIMIT 1;

            IF v_reviewer_id IS NULL THEN
                RAISE EXCEPTION 'V49 requires 12 unused demo reviewers for Quiz Pack %', v_pack.item_id;
            END IF;

            v_rating := (ARRAY[5, 5, 4, 5, 4, 5, 5, 4, 5, 5, 4, 5])[v_review_no];
            v_review_comment := (ARRAY[
                'Bộ câu hỏi đi thẳng vào các tình huống hay gặp. Phần giải thích giúp mình nhớ được lý do chọn đáp án, không chỉ học thuộc.',
                'Làm hết một lượt mất vừa đủ thời gian cho một buổi ôn ngắn. Mức độ câu hỏi tăng dần khá hợp lý.',
                'Nội dung rõ ràng và có tính ứng dụng. Mình mong sau này có thêm vài câu khó hơn ở phần cuối.',
                'Phần Full Pack Challenge tạo động lực ôn lại toàn bộ kiến thức. Bảng xếp hạng cũng làm trải nghiệm thú vị hơn.',
                'Giải thích sau mỗi câu dễ hiểu, đặc biệt hữu ích khi mình chọn sai ở các câu đánh lừa.',
                'Bố cục bốn chương dễ theo dõi. Mình có thể quay lại đúng chủ đề còn yếu để luyện thêm.',
                'Câu hỏi không dài dòng, đủ bối cảnh để suy luận. Phù hợp để tự kiểm tra trước buổi phỏng vấn.',
                'Giá trị nhất là phần ví dụ gắn với tình huống thực tế. Nếu có bookmark câu khó thì sẽ tiện hơn.',
                'Mình đã làm hai lượt và thấy phần tổng hợp kiến thức khá sát nhu cầu ôn tập của mình.',
                'Giao diện làm quiz gọn, trên điện thoại vẫn dễ thao tác. Điểm và thời gian được hiển thị rõ.',
                'Nội dung tốt, chỉ có một vài câu mình phải đọc kỹ mới hiểu hết ngữ cảnh. Nhìn chung rất đáng thử.',
                'Bộ đề có độ bao phủ tốt cho người đang cần hệ thống lại kiến thức trong thời gian ngắn.'
            ])[v_review_no];

            INSERT INTO marketplace_reviews (
                review_id, item_id, pack_version_id, user_id, rating, comment, created_at, updated_at
            ) VALUES (
                v49_seed_uuid(format('review:%s:%s', v_pack.item_id, v_review_no)),
                v_pack.item_id, v_pack.version_id, v_reviewer_id, v_rating, v_review_comment,
                CURRENT_TIMESTAMP - ((v_review_no * 19 + get_byte(decode(md5(v_reviewer_id), 'hex'), 0)) * INTERVAL '1 hour'),
                CURRENT_TIMESTAMP - ((v_review_no * 19 + get_byte(decode(md5(v_reviewer_id), 'hex'), 0)) * INTERVAL '1 hour')
            );
        END LOOP;
    END LOOP;

    FOR v_feedback_no IN 1..30 LOOP
        v_feedback_user_id := v49_v36_uuid('user:' || (((v_feedback_no * 13 - 1) % 100) + 1))::text;
        v_created_at := CURRENT_TIMESTAMP
            - ((v_feedback_no * 9 + 2) * INTERVAL '7 hours')
            - ((v_feedback_no * 11 % 53) * INTERVAL '1 minute');
        v_status := CASE
            WHEN v_feedback_no <= 8 THEN 'OPEN'
            WHEN v_feedback_no <= 15 THEN 'IN_PROGRESS'
            ELSE 'CLOSED'
        END;
        v_feedback_type := (ARRAY[
            'BUG','IMPROVEMENT','QUESTION','OTHER','BUG','IMPROVEMENT','QUESTION','BUG','OTHER','IMPROVEMENT',
            'QUESTION','BUG','IMPROVEMENT','OTHER','QUESTION','BUG','IMPROVEMENT','QUESTION','BUG','OTHER',
            'IMPROVEMENT','QUESTION','BUG','OTHER','IMPROVEMENT','QUESTION','BUG','IMPROVEMENT','OTHER','QUESTION'
        ])[v_feedback_no];
        v_feedback_title := (ARRAY[
            'Kết quả quiz chưa hiển thị ngay sau khi nộp',
            'Đề xuất lưu ghi chú theo từng chương',
            'Có thể xem lại các câu đã trả lời sai không?',
            'Trải nghiệm Full Pack Challenge rất hữu ích',
            'Thanh tiến độ roadmap bị đứng sau khi hoàn thành step',
            'Đề xuất nhắc lịch học theo khung giờ đã chọn',
            'Coin trong marketplace được dùng cho những mục nào?',
            'Bộ lọc môn học trên marketplace đôi lúc tự reset',
            'Cảm ơn vì phần giải thích đáp án chi tiết',
            'Mong có chế độ chỉ luyện câu sai',
            'XP của Full Pack Challenge được tính thế nào?',
            'Trang hồ sơ bị trễ khi đổi ảnh đại diện',
            'Đề xuất thêm thống kê thời gian theo chủ đề',
            'AI Tutor giải thích ngắn gọn và dễ hiểu',
            'Có thể tải lịch học ra file không?',
            'Thông báo hoàn thành quiz xuất hiện hai lần',
            'Đề xuất thêm mục tiêu học theo tuần',
            'Gói Skill Builder có bao gồm marketplace không?',
            'Nút tiếp tục học không đúng roadmap gần nhất',
            'Giao diện mobile của trang leaderboard khá rõ ràng',
            'Đề xuất gợi ý lộ trình theo mục tiêu nghề nghiệp',
            'Khi nào streak được tính là một ngày hợp lệ?',
            'Biểu đồ XP tuần này chưa cập nhật sau khi làm quiz',
            'Cảm nhận tích cực về phần chia nhỏ roadmap',
            'Đề xuất ẩn các pack đã mua khỏi danh sách mặc định',
            'Có thể đổi ngôn ngữ hiển thị cho một vài thuật ngữ không?',
            'Trang chi tiết pack tải chậm khi mạng yếu',
            'Đề xuất thêm đánh dấu câu hỏi cần xem lại',
            'Lịch sử thanh toán dễ theo dõi và rõ trạng thái',
            'Cần hướng dẫn cách dùng Coin sau khi nạp'
        ])[v_feedback_no];
        v_feedback_content := (ARRAY[
            'Sau khi nộp quiz mình phải tải lại trang mới thấy điểm. Mong đội ngũ kiểm tra giúp luồng cập nhật kết quả.',
            'Nếu mỗi chương có khu vực ghi chú nhỏ thì mình có thể lưu lại công thức hoặc mẹo làm bài khi đang học.',
            'Mình muốn xem lại danh sách câu sai sau khi kết thúc để ôn đúng phần kiến thức còn thiếu.',
            'Cách gom toàn bộ câu hỏi vào một thử thách cuối pack giúp mình biết mình đã nắm bài tới đâu.',
            'Mình hoàn thành một step nhưng thanh tiến độ chưa đổi ngay, phải chuyển trang rồi quay lại mới đúng.',
            'Nếu hệ thống nhắc trước giờ học khoảng mười lăm phút thì mình sẽ dễ duy trì lịch hơn.',
            'Mình muốn xác nhận Coin có thể dùng để mua pack, nâng cấp pack hay chỉ dùng cho một loại nội dung.',
            'Sau khi mở chi tiết một pack rồi quay lại marketplace, bộ lọc đã chọn đôi khi không còn được giữ lại.',
            'Phần giải thích cho biết vì sao các lựa chọn khác sai nên việc tự ôn dễ hơn rất nhiều.',
            'Mình thường làm lại các câu sai. Một chế độ lọc riêng sẽ tiết kiệm thời gian tìm kiếm.',
            'Cho mình hỏi XP của thử thách cuối pack có cộng thêm ngoài XP quiz thông thường không?',
            'Mình đã thay ảnh ở hồ sơ nhưng avatar cũ vẫn còn trong vài phút ở thanh điều hướng.',
            'Mình muốn biết tuần này dành bao nhiêu thời gian cho từng nhóm kiến thức để điều chỉnh kế hoạch.',
            'AI Tutor không trả lời quá dài nhưng vẫn có ví dụ, phù hợp lúc mình đang cần nhắc lại nhanh.',
            'Mình cần xuất lịch để đối chiếu với lịch cá nhân. File hoặc liên kết đồng bộ đều hữu ích.',
            'Sau khi nộp quiz thành công, hai thông báo giống nhau xuất hiện liên tiếp trên màn hình.',
            'Mình muốn đặt mục tiêu số giờ học hoặc số quiz cho từng tuần thay vì chỉ theo roadmap.',
            'Mình đang cân nhắc nâng gói và muốn biết quyền truy cập marketplace nằm ở gói nào.',
            'Nút tiếp tục học đưa mình tới một roadmap cũ dù hôm qua mình vừa học ở roadmap khác.',
            'Bảng xếp hạng hiển thị điểm và thời gian rõ, đọc được tốt trên màn hình điện thoại.',
            'Nếu có vài mẫu lộ trình cho Backend, Data hoặc tiếng Anh thì người mới bắt đầu sẽ dễ chọn hơn.',
            'Mình học muộn buổi tối nên muốn biết mốc giờ nào được tính cho streak của ngày hôm đó.',
            'Mình vừa hoàn thành quiz đạt điểm cao nhưng biểu đồ XP tuần vẫn chưa thay đổi sau vài phút.',
            'Chia roadmap thành các phần nhỏ khiến mình bớt bị ngợp khi bắt đầu một chủ đề mới.',
            'Sau khi mua pack, mình vẫn muốn nhìn thấy pack đó nhưng có tùy chọn ẩn mặc định để danh sách gọn hơn.',
            'Một vài thuật ngữ chuyên môn nếu có chú thích tiếng Việt sẽ thân thiện hơn với người mới.',
            'Khi kết nối không ổn định, ảnh và nội dung pack xuất hiện chậm. Mong có trạng thái tải rõ hơn.',
            'Mình thường đánh dấu những câu chưa chắc để quay lại. Tính năng này sẽ hữu ích trong lúc luyện đề.',
            'Lịch sử thanh toán cho biết rõ giao dịch thành công, chờ xử lý và thất bại nên mình yên tâm hơn.',
            'Mình đã nạp Coin thành công nhưng chưa rõ các bước tiếp theo để mua một Quiz Pack.'
        ])[v_feedback_no];
        v_related_url := (ARRAY[
            '/quizzes/result','/roadmaps','/quizzes/result','/marketplace','/roadmaps','/calendar','/marketplace','/marketplace','/marketplace','/quizzes/result',
            '/marketplace','/profile','/dashboard','/ai-tutor','/calendar','/notifications','/goals','/subscription','/dashboard','/leaderboard',
            '/roadmaps','/leaderboard','/leaderboard','/roadmaps','/marketplace','/settings','/marketplace','/quizzes/result','/payments','/marketplace'
        ])[v_feedback_no];

        v_reply := CASE
            WHEN v_status = 'OPEN' THEN NULL
            WHEN v_status = 'IN_PROGRESS' THEN 'Cảm ơn bạn đã gửi thông tin chi tiết. Đội ngũ đã ghi nhận và đang kiểm tra để cập nhật sớm nhất.'
            ELSE 'Cảm ơn phản hồi của bạn. Nội dung đã được kiểm tra và ghi nhận trong đợt cải tiến gần nhất.'
        END;

        INSERT INTO feedbacks (
            feedback_id, user_id, type, title, content, related_url, image_object_key,
            status, admin_note, admin_reply, replied_by_user_id, replied_at, created_at, updated_at
        ) VALUES (
            v49_seed_uuid('feedback:' || v_feedback_no), v_feedback_user_id,
            v_feedback_type, v_feedback_title, v_feedback_content, v_related_url, NULL,
            v_status,
            CASE WHEN v_status = 'OPEN' THEN NULL ELSE 'Đã tiếp nhận phản hồi từ người dùng seed.' END,
            v_reply,
            CASE WHEN v_status = 'OPEN' THEN NULL ELSE v_admin_id END,
            CASE WHEN v_status = 'OPEN' THEN NULL ELSE v_created_at + INTERVAL '5 hours' + ((v_feedback_no % 4) * INTERVAL '2 hours') END,
            v_created_at,
            CASE WHEN v_status = 'OPEN' THEN v_created_at ELSE v_created_at + INTERVAL '5 hours' + ((v_feedback_no % 4) * INTERVAL '2 hours') END
        );
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM marketplace_items item
        WHERE item.item_id IN (
            v49_v28_uuid('item:3'),
            v49_v28_uuid('item:4'),
            v49_v28_uuid('item:5')
        )
          AND (SELECT count(*)
               FROM marketplace_reviews review
               WHERE review.item_id = item.item_id) < 15
    ) THEN
        RAISE EXCEPTION 'V49 postcondition failed; Quiz Pack reviews are incomplete';
    END IF;

    IF (SELECT count(*) FROM feedbacks
        WHERE feedback_id IN (
            SELECT v49_seed_uuid('feedback:' || n) FROM generate_series(1, 30) AS n
        )) <> 30
       OR (SELECT count(*) FROM feedbacks
           WHERE feedback_id IN (
               SELECT v49_seed_uuid('feedback:' || n) FROM generate_series(1, 30) AS n
           ) AND status = 'OPEN') <> 8
       OR (SELECT count(*) FROM feedbacks
           WHERE feedback_id IN (
               SELECT v49_seed_uuid('feedback:' || n) FROM generate_series(1, 30) AS n
           ) AND status = 'IN_PROGRESS') <> 7
       OR (SELECT count(*) FROM feedbacks
           WHERE feedback_id IN (
               SELECT v49_seed_uuid('feedback:' || n) FROM generate_series(1, 30) AS n
           ) AND status = 'CLOSED') <> 15 THEN
        RAISE EXCEPTION 'V49 postcondition failed; feedback seed is incomplete';
    END IF;
END $$;

DROP FUNCTION v49_v36_uuid(TEXT);
DROP FUNCTION v49_v28_uuid(TEXT);
DROP FUNCTION v49_seed_uuid(TEXT);
