-- Normalize the V28/V30 demo cohort after V32 right-sized its subscription
-- revenue. This migration never touches a non-seed user or a legacy record.
-- It preserves financial totals while removing mechanically spaced timestamps,
-- repeated display names and uniform marketplace/community activity.

CREATE FUNCTION v33_v28_uuid(seed TEXT)
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

DO $$
DECLARE
    v_free_plan UUID;
    v_builder_plan UUID;
    v_premium_plan UUID;
BEGIN
    SELECT plan_id INTO v_free_plan FROM service_plans WHERE plan_type = 'FREE' LIMIT 1;
    SELECT plan_id INTO v_builder_plan FROM service_plans WHERE plan_type = 'SKILL_BUILDER' LIMIT 1;
    SELECT plan_id INTO v_premium_plan FROM service_plans WHERE plan_type = 'PREMIUM' LIMIT 1;
    IF v_free_plan IS NULL OR v_builder_plan IS NULL OR v_premium_plan IS NULL THEN
        RAISE EXCEPTION 'V33 requires FREE, SKILL_BUILDER and PREMIUM service plans';
    END IF;

    -- Give the isolated cohort real-looking, unique Vietnamese identities. The
    -- invalid domain keeps these accounts unable to receive real communication.
    WITH demo_users AS (
        SELECT u.user_id, row_number() OVER (ORDER BY u.user_id)::integer AS ordinal
        FROM users u
        WHERE u.user_id IN (
            SELECT v33_v28_uuid('user:' || n)::text FROM generate_series(1, 184) AS n
        )
    )
    UPDATE users u
    SET full_name =
            (ARRAY['Nguyễn','Trần','Lê','Phạm','Hoàng','Vũ','Đỗ','Bùi','Ngô','Dương','Phan','Huỳnh','Đặng','Lý','Đoàn','Cao','Mai','Tạ','Châu','Tô','Lương','Võ','Quách'])[((d.ordinal - 1) % 23) + 1]
            || ' ' ||
            (ARRAY['Minh Anh','Gia Bảo','Hoàng Nam','Khánh Linh','Đức Huy','Ngọc Mai','Quang Hưng','Thảo Vy'])[((d.ordinal - 1) / 23) + 1],
        email =
            (ARRAY['nguyen','tran','le','pham','hoang','vu','do','bui','ngo','duong','phan','huynh','dang','ly','doan','cao','mai','ta','chau','to','luong','vo','quach'])[((d.ordinal - 1) % 23) + 1]
            || '.' ||
            (ARRAY['minh-anh','gia-bao','hoang-nam','khanh-linh','duc-huy','ngoc-mai','quang-hung','thao-vy'])[((d.ordinal - 1) / 23) + 1]
            || '@skillsprint.invalid',
        updated_at = TIMESTAMPTZ '2026-07-26 09:45:00+07'
    FROM demo_users d
    WHERE u.user_id = d.user_id;

    -- The 105 V32-retained payments are genuine current-cycle subscriptions.
    -- Spread them through the final 30 days of the demo window with a stable
    -- per-reference jitter so an active one-month plan remains logically valid.
    CREATE TEMP TABLE v33_subscription_schedule ON COMMIT DROP AS
    SELECT p.payment_id,
           p.user_id,
           p.plan_id,
           p.txn_ref,
           (
               TIMESTAMPTZ '2026-06-27 08:20:00+07'
               + ((row_number() OVER (ORDER BY md5(p.txn_ref)) - 1) * INTERVAL '6 hours 25 minutes')
               + ((get_byte(decode(md5(p.txn_ref), 'hex'), 0) % 145) * INTERVAL '5 minutes')
           ) AS paid_at
    FROM payment_transactions p
    WHERE p.txn_ref LIKE 'V28SUB%';

    UPDATE payment_transactions p
    SET paid_at = s.paid_at,
        expire_at = s.paid_at - INTERVAL '12 minutes',
        created_at = s.paid_at - INTERVAL '12 minutes',
        updated_at = s.paid_at,
        txn_ref = 'SP2026S' || right(s.txn_ref, 4),
        transfer_content = 'SP2026S' || right(s.txn_ref, 4),
        provider_transaction_id = 'SPY26-' || right(p.txn_ref, 4),
        provider_reference_code = 'SPREF26-' || right(p.txn_ref, 4),
        raw_callback_data = jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION', 'verified', true)
    FROM v33_subscription_schedule s
    WHERE p.payment_id = s.payment_id;

    UPDATE platform_treasury_entries t
    SET occurred_at = s.paid_at,
        created_at = s.paid_at,
        updated_at = s.paid_at,
        actor_name_snapshot = u.full_name,
        external_reference = 'SP2026S' || right(s.txn_ref, 4),
        note = 'Subscription package payment received',
        metadata = jsonb_build_object('channel', 'SEPAY', 'purpose', 'SUBSCRIPTION')
    FROM v33_subscription_schedule s
    JOIN users u ON u.user_id = s.user_id
    WHERE t.reference_id = s.payment_id
      AND t.idempotency_key LIKE 'v28-subscription-payment-%';

    UPDATE subscriptions sub
    SET plan_id = COALESCE(s.plan_id, v_free_plan),
        start_date = COALESCE((s.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, DATE '2026-07-01'),
        start_at = COALESCE(s.paid_at, TIMESTAMPTZ '2026-07-01 00:00:00+07'),
        end_date = CASE WHEN s.payment_id IS NULL THEN NULL ELSE (s.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + 31 END,
        end_at = CASE WHEN s.payment_id IS NULL THEN NULL ELSE s.paid_at + INTERVAL '31 days' END,
        status = 'ACTIVE'
    FROM generate_series(1, 184) AS seed(ordinal)
    LEFT JOIN v33_subscription_schedule s ON s.user_id = v33_v28_uuid('user:' || seed.ordinal)::text
    WHERE sub.subscription_id = v33_v28_uuid('subscription:' || seed.ordinal);

    -- Keep the V30 1.5M VND/Coin total, but mix package types and dates over the
    -- full May-July history instead of an exact 36-hour cadence.
    CREATE TEMP TABLE v33_coin_schedule ON COMMIT DROP AS
    SELECT p.payment_id,
           p.user_id,
           p.txn_ref,
           (
               TIMESTAMPTZ '2026-05-06 09:10:00+07'
               + ((row_number() OVER (ORDER BY md5(p.txn_ref)) - 1) * INTERVAL '34 hours 30 minutes')
               + ((get_byte(decode(md5(p.txn_ref), 'hex'), 1) % 205) * INTERVAL '5 minutes')
           ) AS paid_at
    FROM payment_transactions p
    WHERE p.txn_ref LIKE 'V30COIN%';

    UPDATE payment_transactions p
    SET paid_at = s.paid_at,
        expire_at = s.paid_at - INTERVAL '14 minutes',
        created_at = s.paid_at - INTERVAL '14 minutes',
        updated_at = s.paid_at,
        txn_ref = 'SP2026C' || right(s.txn_ref, 4),
        transfer_content = 'SP2026C' || right(s.txn_ref, 4),
        provider_transaction_id = 'SPC26-' || right(p.txn_ref, 4),
        provider_reference_code = 'SPCREF26-' || right(p.txn_ref, 4),
        raw_callback_data = jsonb_build_object('channel', 'SEPAY', 'purpose', 'COIN_TOP_UP', 'verified', true)
    FROM v33_coin_schedule s
    WHERE p.payment_id = s.payment_id;

    UPDATE wallet_transactions wt
    SET created_at = s.paid_at,
        updated_at = s.paid_at
    FROM v33_coin_schedule s
    WHERE wt.reference_id = s.payment_id
      AND wt.reference_type = 'COIN_TOP_UP';

    UPDATE platform_treasury_entries t
    SET occurred_at = s.paid_at,
        created_at = s.paid_at,
        updated_at = s.paid_at,
        counterparty_name_snapshot = u.full_name,
        external_reference = 'SP2026C' || right(s.txn_ref, 4),
        note = 'Coin top-up received',
        metadata = jsonb_build_object('channel', 'SEPAY', 'purpose', 'COIN_TOP_UP')
    FROM v33_coin_schedule s
    JOIN users u ON u.user_id = s.user_id
    WHERE t.reference_id = s.payment_id
      AND t.external_reference LIKE 'V30COIN%';

    -- Sales remain at the V31-corrected Coin/VND rate and 80/20 split, but no
    -- longer arrive at a fixed 13h30 cadence.
    CREATE TEMP TABLE v33_sale_schedule ON COMMIT DROP AS
    SELECT sale.sale_id,
           (
               TIMESTAMPTZ '2026-05-02 10:00:00+07'
               + ((row_number() OVER (ORDER BY md5(sale.sale_id::text)) - 1) * INTERVAL '13 hours 30 minutes')
               + ((get_byte(decode(md5(sale.sale_id::text), 'hex'), 0) % 133) * INTERVAL '5 minutes')
           ) AS occurred_at,
           row_number() OVER (ORDER BY md5(sale.sale_id::text))::integer AS ordinal
    FROM marketplace_sales sale
    WHERE sale.idempotency_key LIKE 'v28-sale-%';

    UPDATE marketplace_sales sale
    SET created_at = schedule.occurred_at,
        updated_at = schedule.occurred_at
    FROM v33_sale_schedule schedule
    WHERE sale.sale_id = schedule.sale_id;

    UPDATE marketplace_sale_settlements settlement
    SET created_at = schedule.occurred_at,
        updated_at = schedule.occurred_at
    FROM v33_sale_schedule schedule
    WHERE settlement.sale_id = schedule.sale_id;

    UPDATE creator_earning_entries earning
    SET created_at = schedule.occurred_at,
        updated_at = schedule.occurred_at
    FROM marketplace_sale_settlements settlement
    JOIN v33_sale_schedule schedule ON schedule.sale_id = settlement.sale_id
    WHERE earning.settlement_id = settlement.settlement_id;

    UPDATE platform_revenue_entries revenue
    SET created_at = schedule.occurred_at,
        updated_at = schedule.occurred_at
    FROM v33_sale_schedule schedule
    WHERE revenue.sale_id = schedule.sale_id;

    UPDATE marketplace_entitlements entitlement
    SET granted_at = schedule.occurred_at,
        created_at = schedule.occurred_at,
        updated_at = schedule.occurred_at
    FROM v33_sale_schedule schedule
    WHERE entitlement.source_sale_id = schedule.sale_id;

    UPDATE marketplace_ranked_attempts attempt
    SET attempt_date = (schedule.occurred_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
        started_at = schedule.occurred_at + INTERVAL '18 minutes',
        expires_at = schedule.occurred_at + INTERVAL '1 hour 18 minutes',
        completed_at = schedule.occurred_at + INTERVAL '26 minutes' + ((schedule.ordinal % 17) * INTERVAL '1 minute'),
        score = (13 + (schedule.ordinal % 8)) * 5,
        correct_count = 13 + (schedule.ordinal % 8),
        duration_seconds = 480 + ((schedule.ordinal * 37) % 710),
        created_at = schedule.occurred_at + INTERVAL '18 minutes',
        updated_at = schedule.occurred_at + INTERVAL '28 minutes'
    FROM v33_sale_schedule schedule
    WHERE attempt.attempt_id IN (
        SELECT v33_v28_uuid(format('attempt:%s:%s', product_no, buyer_no))
        FROM generate_series(1, 5) product_no CROSS JOIN generate_series(1, 30) buyer_no
    )
      AND attempt.buyer_id = (SELECT sale.buyer_id FROM marketplace_sales sale WHERE sale.sale_id = schedule.sale_id)
      AND attempt.pack_version_id = (SELECT sale.pack_version_id FROM marketplace_sales sale WHERE sale.sale_id = schedule.sale_id);

    UPDATE marketplace_reviews review
    SET rating = CASE WHEN schedule.ordinal % 5 = 0 THEN 4 ELSE 5 END,
        comment = (ARRAY[
            'Lộ trình rõ ràng, phần quiz vừa sức và dễ theo dõi.',
            'Nội dung có ví dụ thực tế, học xong áp dụng được ngay.',
            'Bộ câu hỏi giúp mình rà lại các phần còn yếu.',
            'Trình bày gọn, chất lượng ổn so với mức Coin.',
            'Mình thích phần tổng kết sau mỗi chủ đề.'
        ])[((schedule.ordinal - 1) % 5) + 1],
        created_at = LEAST(schedule.occurred_at + INTERVAL '2 days' + ((schedule.ordinal % 11) * INTERVAL '1 hour'), TIMESTAMPTZ '2026-07-25 20:00:00+07'),
        updated_at = LEAST(schedule.occurred_at + INTERVAL '2 days' + ((schedule.ordinal % 11) * INTERVAL '1 hour'), TIMESTAMPTZ '2026-07-25 20:00:00+07')
    FROM marketplace_sales sale
    JOIN v33_sale_schedule schedule ON schedule.sale_id = sale.sale_id
    WHERE review.user_id = sale.buyer_id
      AND review.pack_version_id = sale.pack_version_id
      AND review.review_id IN (
          SELECT v33_v28_uuid(format('review:%s:%s', product_no, buyer_no))
          FROM generate_series(1, 5) product_no CROSS JOIN generate_series(1, 3) buyer_no
      );

    -- Community activity is spread through the same historical window. Counts
    -- are recalculated from the immutable row set afterwards.
    CREATE TEMP TABLE v33_post_schedule ON COMMIT DROP AS
    SELECT n::integer AS ordinal,
           (
               TIMESTAMPTZ '2026-05-03 08:30:00+07'
               + ((n - 1) * INTERVAL '2 days 12 hours')
               + ((n * 43 % 181) * INTERVAL '5 minutes')
           ) AS occurred_at
    FROM generate_series(1, 30) AS n;

    UPDATE community_posts post
    SET content = (ARRAY[
            'Vừa hoàn thành mục tiêu Pomodoro tuần này, chia nhỏ việc giúp mình đỡ ngợp hơn nhiều.',
            'Mọi người thường ghi chú lỗi Java theo cách nào để lần sau tránh lặp lại vậy?',
            'Hôm nay mình ôn lại SQL JOIN; làm bài nhỏ sau khi học giúp nhớ lâu hơn.',
            'React hooks có đoạn nào mọi người thấy khó nhớ nhất không?',
            'Chia sẻ chút động lực: duy trì 30 phút mỗi ngày vẫn tốt hơn bỏ hẳn một tuần.',
            'Mình vừa refactor lại bài OOP, tách class nhỏ hơn nên code dễ đọc hơn hẳn.',
            'Có bạn nào đang luyện TOEIC muốn trao đổi cách học từ vựng theo chủ đề không?',
            'Bài thuật toán hôm nay chưa ra ngay nhưng debug từng bước cũng học được nhiều.',
            'Mục tiêu tuần tới là hoàn thành một mini project thay vì học lan man.',
            'Cảm ơn mọi người đã gợi ý tài liệu, mình đã sắp lại roadmap cá nhân rồi.'
        ])[((schedule.ordinal - 1) % 10) + 1],
        hashtags = (ARRAY['#Java #HocTap','#SQL #Database','#ReactJS #Frontend','#Pomodoro #KyLuat','#Algorithm #TuDuy'])[((schedule.ordinal - 1) % 5) + 1],
        created_at = schedule.occurred_at,
        updated_at = schedule.occurred_at + INTERVAL '18 minutes'
    FROM v33_post_schedule schedule
    WHERE post.post_id = v33_v28_uuid('post:' || schedule.ordinal);

    UPDATE post_comments comment
    SET content = (ARRAY[
            'Mình cũng từng gặp chỗ này, chia nhỏ bài ra làm dễ hơn nhiều.',
            'Cách bạn ghi lại tiến độ khá hay, mình sẽ thử áp dụng.',
            'Cảm ơn bạn đã chia sẻ, phần ví dụ này rất dễ hiểu.',
            'Đúng rồi, giữ nhịp đều quan trọng hơn học dồn một lúc.',
            'Mình bổ sung thêm: làm lại bài sau một ngày giúp nhớ tốt hơn.',
            'Chúc bạn sớm hoàn thành mục tiêu tuần này nhé.'
        ])[((seed.ordinal - 1) % 6) + 1],
        created_at = LEAST(schedule.occurred_at + INTERVAL '3 hours' + ((seed.ordinal * 47 % 127) * INTERVAL '1 hour'), TIMESTAMPTZ '2026-07-25 21:00:00+07'),
        updated_at = LEAST(schedule.occurred_at + INTERVAL '3 hours' + ((seed.ordinal * 47 % 127) * INTERVAL '1 hour'), TIMESTAMPTZ '2026-07-25 21:00:00+07')
    FROM generate_series(1, 100) AS seed(ordinal)
    JOIN v33_post_schedule schedule ON schedule.ordinal = ((seed.ordinal - 1) % 30) + 1
    WHERE comment.comment_id = v33_v28_uuid('comment:' || seed.ordinal);

    UPDATE post_likes like_row
    SET created_at = LEAST(schedule.occurred_at + INTERVAL '45 minutes' + ((seed.ordinal * 19 % 163) * INTERVAL '1 hour'), TIMESTAMPTZ '2026-07-25 22:00:00+07'),
        updated_at = LEAST(schedule.occurred_at + INTERVAL '45 minutes' + ((seed.ordinal * 19 % 163) * INTERVAL '1 hour'), TIMESTAMPTZ '2026-07-25 22:00:00+07')
    FROM generate_series(1, 323) AS seed(ordinal)
    JOIN v33_post_schedule schedule ON schedule.ordinal = ((seed.ordinal - 1) % 30) + 1
    WHERE like_row.like_id = v33_v28_uuid('like:' || seed.ordinal);

    UPDATE community_room_members member
    SET created_at = TIMESTAMPTZ '2026-05-01 09:00:00+07' + ((seed.ordinal * 29 % 1180) * INTERVAL '1 hour'),
        updated_at = TIMESTAMPTZ '2026-07-25 18:00:00+07'
    FROM generate_series(1, 6) AS room_no
    CROSS JOIN LATERAL generate_series(1, CASE WHEN room_no = 6 THEN 25 ELSE 26 END) AS seed(ordinal)
    WHERE member.member_id = v33_v28_uuid(format('room-member:%s:%s', room_no, seed.ordinal));

    CREATE TEMP TABLE v33_message_schedule ON COMMIT DROP AS
    SELECT n::integer AS ordinal,
           (
               TIMESTAMPTZ '2026-05-12 08:15:00+07'
               + ((n - 1) * INTERVAL '11 hours 30 minutes')
               + ((n * 31 % 97) * INTERVAL '5 minutes')
           ) AS occurred_at
    FROM generate_series(1, 150) AS n;

    UPDATE community_chat_messages message
    SET raw_content = (ARRAY[
            'Mọi người đã thử chia mục tiêu hôm nay thành các phiên ngắn chưa?',
            'Mình vừa hoàn thành phần bài tập, có đoạn nào cần trao đổi thêm không?',
            'Tài liệu này giải thích khá dễ hiểu, mình để lại cho mọi người tham khảo.',
            'Ai đang ôn cùng chủ đề thì vào trao đổi nhé.',
            'Mình sẽ quay lại làm bài sau bữa tối, chúc mọi người học tốt.',
            'Phần này cần đọc chậm một chút là nắm được ý chính.'
        ])[((schedule.ordinal - 1) % 6) + 1],
        masked_content = (ARRAY[
            'Mọi người đã thử chia mục tiêu hôm nay thành các phiên ngắn chưa?',
            'Mình vừa hoàn thành phần bài tập, có đoạn nào cần trao đổi thêm không?',
            'Tài liệu này giải thích khá dễ hiểu, mình để lại cho mọi người tham khảo.',
            'Ai đang ôn cùng chủ đề thì vào trao đổi nhé.',
            'Mình sẽ quay lại làm bài sau bữa tối, chúc mọi người học tốt.',
            'Phần này cần đọc chậm một chút là nắm được ý chính.'
        ])[((schedule.ordinal - 1) % 6) + 1],
        sent_at = schedule.occurred_at
    FROM v33_message_schedule schedule
    WHERE message.message_id = v33_v28_uuid('message:' || schedule.ordinal);

    UPDATE feedbacks feedback
    SET content = (ARRAY[
            'Mình mong giao diện có thêm chế độ tối để học buổi tối dễ chịu hơn.',
            'AI Tutor giải thích lại phần mình sai khá rõ, cảm ơn đội ngũ.',
            'Trên màn hình điện thoại nút bắt đầu Pomodoro đôi lúc khó bấm.',
            'Mình cần thêm hướng dẫn nhận diện nội dung chuyển khoản SePay.'
        ])[seed.ordinal],
        admin_reply = (ARRAY[
            'Cảm ơn bạn đã góp ý. Dark Mode đã được đưa vào danh sách cải tiến giao diện.',
            'Rất vui vì AI Tutor hỗ trợ được quá trình học của bạn. Đội ngũ sẽ tiếp tục cải thiện chất lượng phản hồi.',
            'Đội ngũ đã kiểm tra và điều chỉnh vùng thao tác trên giao diện mobile. Cảm ơn bạn đã báo sớm.',
            'Chúng tôi đã bổ sung hướng dẫn thanh toán SePay trong khu vực hỗ trợ. Bạn có thể thử lại với nội dung chuyển khoản mới.'
        ])[seed.ordinal],
        created_at = (ARRAY[TIMESTAMPTZ '2026-06-08 10:15:00+07', TIMESTAMPTZ '2026-06-19 14:40:00+07', TIMESTAMPTZ '2026-07-03 09:25:00+07', TIMESTAMPTZ '2026-07-15 16:10:00+07'])[seed.ordinal],
        replied_at = (ARRAY[TIMESTAMPTZ '2026-06-10 15:20:00+07', TIMESTAMPTZ '2026-06-20 11:10:00+07', TIMESTAMPTZ '2026-07-05 13:30:00+07', TIMESTAMPTZ '2026-07-16 10:05:00+07'])[seed.ordinal],
        updated_at = (ARRAY[TIMESTAMPTZ '2026-06-10 15:20:00+07', TIMESTAMPTZ '2026-06-20 11:10:00+07', TIMESTAMPTZ '2026-07-05 13:30:00+07', TIMESTAMPTZ '2026-07-16 10:05:00+07'])[seed.ordinal]
    FROM generate_series(1, 4) AS seed(ordinal)
    WHERE feedback.feedback_id = v33_v28_uuid('feedback:' || seed.ordinal);

    UPDATE community_posts post
    SET comment_count = (SELECT count(*) FROM post_comments comment WHERE comment.post_id = post.post_id),
        like_count = (SELECT count(*) FROM post_likes like_row WHERE like_row.post_id = post.post_id)
    WHERE post.post_id IN (SELECT v33_v28_uuid('post:' || n) FROM generate_series(1, 30) AS n);

    IF (SELECT count(*) FROM payment_transactions WHERE payment_id IN (SELECT payment_id FROM v33_subscription_schedule)) <> 105
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE payment_id IN (SELECT payment_id FROM v33_subscription_schedule)) <> 10995000
       OR (SELECT count(*) FROM payment_transactions WHERE payment_id IN (SELECT payment_id FROM v33_coin_schedule)) <> 56
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE payment_id IN (SELECT payment_id FROM v33_coin_schedule)) <> 1500000
       OR (SELECT COALESCE(sum(revenue.amount), 0) FROM platform_revenue_entries revenue
           JOIN marketplace_sales sale ON sale.sale_id = revenue.sale_id
           WHERE sale.idempotency_key LIKE 'v28-sale-%') <> 720
       OR (SELECT count(*) FROM marketplace_ranked_attempts attempt
           WHERE attempt.attempt_id IN (
               SELECT v33_v28_uuid(format('attempt:%s:%s', product_no, buyer_no))
               FROM generate_series(1, 5) product_no CROSS JOIN generate_series(1, 30) buyer_no
           ) AND attempt.score < 100) <> 132
       OR (SELECT count(*) FROM community_posts WHERE post_id IN (SELECT v33_v28_uuid('post:' || n) FROM generate_series(1, 30) AS n)) <> 30
       OR (SELECT count(*) FROM post_comments WHERE comment_id IN (SELECT v33_v28_uuid('comment:' || n) FROM generate_series(1, 100) AS n)) <> 100
       OR (SELECT count(*) FROM post_likes WHERE like_id IN (SELECT v33_v28_uuid('like:' || n) FROM generate_series(1, 323) AS n)) <> 323
       OR (SELECT count(*) FROM community_chat_messages WHERE message_id IN (SELECT v33_v28_uuid('message:' || n) FROM generate_series(1, 150) AS n)) <> 150
       OR (SELECT count(*) FROM feedbacks WHERE feedback_id IN (SELECT v33_v28_uuid('feedback:' || n) FROM generate_series(1, 4) AS n) AND status = 'CLOSED') <> 4 THEN
        RAISE EXCEPTION 'V33 postcondition failed; V28 demo history normalization is rolled back';
    END IF;
END $$;

DROP FUNCTION v33_v28_uuid(TEXT);
