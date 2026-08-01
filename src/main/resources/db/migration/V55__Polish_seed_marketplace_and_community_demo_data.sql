-- Keep the demo catalog coherent without touching learner-created content or
-- real-user financial records. Older V28 packs were still priced in single
-- digit Coins, which leaked into the treasury as implausibly small commission.

CREATE FUNCTION v55_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v55:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v55:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v55:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v55:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v55:' || seed), 21, 12)
    )::uuid;
$$;

CREATE FUNCTION v55_v28_uuid(seed TEXT)
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

CREATE FUNCTION v55_v52_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v52:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v52:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v52:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v52:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v52:' || seed), 21, 12)
    )::uuid;
$$;

CREATE TEMP TABLE v55_pack_shapes (
    item_id UUID PRIMARY KEY,
    chapter_count INTEGER NOT NULL,
    questions_per_chapter INTEGER NOT NULL
) ON COMMIT DROP;

-- Five V28 packs and six expanded V52 packs. These are the seeded packs with
-- immutable quiz snapshots, i.e. the set rendered by the public catalog.
-- Every visible pack has a distinct chapter/question total in the catalog.
INSERT INTO v55_pack_shapes (item_id, chapter_count, questions_per_chapter) VALUES
    (v55_v28_uuid('item:1'), 5, 7),
    (v55_v28_uuid('item:2'), 4, 9),
    (v55_v28_uuid('item:3'), 6, 7),
    (v55_v28_uuid('item:4'), 5, 9),
    (v55_v28_uuid('item:5'), 7, 7),
    (v55_v52_uuid('item:1'), 3, 11),
    (v55_v52_uuid('item:2'), 4, 8),
    (v55_v52_uuid('item:3'), 5, 8),
    (v55_v52_uuid('item:4'), 6, 8),
    (v55_v52_uuid('item:5'), 7, 8),
    (v55_v52_uuid('item:6'), 5, 11);

CREATE TEMP TABLE v55_snapshot_content ON COMMIT DROP AS
SELECT shape.item_id,
       jsonb_build_object('chapters', jsonb_agg(
           jsonb_build_object(
               'sequenceNo', chapter.chapter_no,
               'title', format('%s — Chương %s', item.subject, chapter.chapter_no),
               'summary', format('Ôn tập %s theo tình huống thực hành, có đáp án và giải thích rõ ràng.', item.subject),
               'quiz', jsonb_build_object(
                   'title', format('Quiz chương %s', chapter.chapter_no),
                   'questions', (
                       SELECT jsonb_agg(jsonb_build_object(
                           'questionId', v55_uuid(format('snapshot-question:%s:%s:%s', shape.item_id, chapter.chapter_no, question.question_no))::text,
                           'type', 'SINGLE_CHOICE',
                           'text', format('Tình huống %s của chương %s: lựa chọn nào phù hợp nhất khi áp dụng %s?', question.question_no, chapter.chapter_no, item.subject),
                           'explanation', 'Đáp án đúng ưu tiên kiểm tra yêu cầu, dữ liệu đầu vào và xác minh kết quả trước khi mở rộng thay đổi.',
                           'sequenceNo', question.question_no,
                           'options', jsonb_build_array(
                               jsonb_build_object('optionId', v55_uuid(format('snapshot-option:%s:%s:%s:1', shape.item_id, chapter.chapter_no, question.question_no))::text, 'label', 'A', 'text', 'Xác định yêu cầu, kiểm tra dữ liệu và xác minh kết quả theo từng bước.', 'correct', TRUE, 'sequenceNo', 1),
                               jsonb_build_object('optionId', v55_uuid(format('snapshot-option:%s:%s:%s:2', shape.item_id, chapter.chapter_no, question.question_no))::text, 'label', 'B', 'text', 'Áp dụng ngay một phương án cố định cho mọi bối cảnh.', 'correct', FALSE, 'sequenceNo', 2),
                               jsonb_build_object('optionId', v55_uuid(format('snapshot-option:%s:%s:%s:3', shape.item_id, chapter.chapter_no, question.question_no))::text, 'label', 'C', 'text', 'Bỏ qua bước đối chiếu vì kết quả ban đầu trông hợp lý.', 'correct', FALSE, 'sequenceNo', 3),
                               jsonb_build_object('optionId', v55_uuid(format('snapshot-option:%s:%s:%s:4', shape.item_id, chapter.chapter_no, question.question_no))::text, 'label', 'D', 'text', 'Thay đổi nhiều yếu tố cùng lúc để hoàn thành nhanh hơn.', 'correct', FALSE, 'sequenceNo', 4)
                           )
                       ) ORDER BY question.question_no)
                       FROM generate_series(1, shape.questions_per_chapter) AS question(question_no)
                   )
               )
           ) ORDER BY chapter.chapter_no
       )) AS content_json
FROM v55_pack_shapes shape
JOIN marketplace_items item ON item.item_id = shape.item_id
CROSS JOIN LATERAL generate_series(1, shape.chapter_count) AS chapter(chapter_no)
GROUP BY shape.item_id, item.subject, shape.chapter_count, shape.questions_per_chapter;

-- Catalog cards and the legacy Full Pack Challenge read the immutable item
-- snapshot, so keep its counters and question content in lockstep.
UPDATE marketplace_quiz_pack_snapshots snapshot
SET chapter_count = shape.chapter_count,
    quiz_count = shape.chapter_count,
    question_count = shape.chapter_count * shape.questions_per_chapter,
    content_json = content.content_json,
    updated_at = CURRENT_TIMESTAMP
FROM v55_pack_shapes shape
JOIN v55_snapshot_content content ON content.item_id = shape.item_id
WHERE snapshot.item_id = shape.item_id;

-- All visible demo packs are now sold at the advertised 50,000 Coin price.
-- Historical V52 purchases remain immutable; only the known V28 history had
-- the old single-digit price and needs its financial rows corrected.
UPDATE marketplace_items item
SET price_coins = 50000,
    updated_at = CURRENT_TIMESTAMP
FROM v55_pack_shapes shape
WHERE item.item_id = shape.item_id;

UPDATE marketplace_pack_versions version
SET price_coins = 50000,
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_packs pack
JOIN v55_pack_shapes shape ON shape.item_id = pack.legacy_item_id
WHERE version.pack_id = pack.pack_id;

UPDATE marketplace_sales sale
SET gross_coin_amount = 50000,
    original_gross_coin_amount = 50000,
    discount_coin_amount = 0,
    gross_vnd_amount = 50000,
    coin_to_vnd_rate = 1.0000,
    updated_at = CURRENT_TIMESTAMP
WHERE sale.idempotency_key LIKE 'v28-sale-%';

UPDATE marketplace_sale_settlements settlement
SET creator_amount = sale.gross_coin_amount * 80 / 100,
    platform_amount = sale.gross_coin_amount * 20 / 100,
    coin_to_vnd_rate = 1.0000,
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sales sale
WHERE settlement.sale_id = sale.sale_id
  AND sale.idempotency_key LIKE 'v28-sale-%';

UPDATE creator_earning_entries earning
SET amount = settlement.creator_amount,
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sale_settlements settlement
JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
WHERE earning.settlement_id = settlement.settlement_id
  AND sale.idempotency_key LIKE 'v28-sale-%';

UPDATE platform_revenue_entries revenue
SET amount = settlement.platform_amount,
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sale_settlements settlement
JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
WHERE revenue.settlement_id = settlement.settlement_id
  AND sale.idempotency_key LIKE 'v28-sale-%';

UPDATE platform_treasury_entries treasury
SET amount = settlement.platform_amount,
    note = 'Marketplace commission from normalized V28 demo sale',
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_sale_settlements settlement
JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
WHERE treasury.entry_type = 'MARKETPLACE_COMMISSION_EARNED'
  AND treasury.reference_type = 'SALE'
  AND treasury.reference_id = sale.sale_id
  AND settlement.sale_id = sale.sale_id
  AND sale.idempotency_key LIKE 'v28-sale-%';

-- V34 correctly skipped the two original free V28 packs because their revenue
-- was zero at the time. They are paid after the V55 price normalization, so
-- create their missing commission credits before reconciling the ledger.
INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v55_uuid('normalized-v28-commission:' || revenue.sale_id),
       'COIN', 'CREDIT', 'MARKETPLACE_COMMISSION_EARNED', 'SALE', revenue.sale_id, revenue.amount,
       'SYSTEM', sale.buyer_id, buyer.full_name,
       'Marketplace commission from normalized V28 demo sale',
       jsonb_build_object('settlementId', revenue.settlement_id, 'source', 'V55 price normalization'),
       revenue.created_at,
       'v55-normalized-v28-commission:' || revenue.sale_id,
       revenue.created_at, CURRENT_TIMESTAMP
FROM platform_revenue_entries revenue
JOIN marketplace_sales sale ON sale.sale_id = revenue.sale_id
JOIN users buyer ON buyer.user_id = sale.buyer_id
WHERE sale.idempotency_key LIKE 'v28-sale-%'
  AND revenue.amount > 0
  AND NOT EXISTS (
      SELECT 1
      FROM platform_treasury_entries treasury
      WHERE treasury.entry_type = 'MARKETPLACE_COMMISSION_EARNED'
        AND treasury.reference_type = 'SALE'
        AND treasury.reference_id = revenue.sale_id
        AND treasury.asset = 'COIN'
        AND treasury.direction = 'CREDIT'
  );

-- The public item page uses marketplace_quiz_attempts, not the newer ranked
-- attempt table. Seed ten credible completed attempts for every visible demo
-- item so its public leaderboard is never empty.
INSERT INTO marketplace_quiz_attempts (
    attempt_id, item_id, pack_version_id, user_id, attempt_type, score,
    correct_count, question_count, duration_seconds, suspicious, completed_at,
    created_at, updated_at
)
SELECT v55_uuid(format('public-leaderboard:%s:%s', shape.item_id, rank.rank_no)),
       shape.item_id,
       version.version_id,
       v55_v28_uuid('user:' || rank.rank_no)::text,
       'RANKED',
       100 - ((rank.rank_no - 1) * 3),
       GREATEST(shape.chapter_count * shape.questions_per_chapter - rank.rank_no + 1, 1),
       shape.chapter_count * shape.questions_per_chapter,
       310 + (rank.rank_no * 47) + ((get_byte(decode(md5(shape.item_id::text), 'hex'), 0) % 29)),
       FALSE,
       TIMESTAMPTZ '2026-07-30 20:00:00+07' - ((rank.rank_no - 1) * INTERVAL '37 minutes'),
       TIMESTAMPTZ '2026-07-30 20:00:00+07' - ((rank.rank_no - 1) * INTERVAL '37 minutes'),
       TIMESTAMPTZ '2026-07-30 20:00:00+07' - ((rank.rank_no - 1) * INTERVAL '37 minutes')
FROM v55_pack_shapes shape
JOIN marketplace_packs pack ON pack.legacy_item_id = shape.item_id
JOIN LATERAL (
    SELECT version_id
    FROM marketplace_pack_versions
    WHERE pack_id = pack.pack_id
    ORDER BY version_no DESC, created_at DESC
    LIMIT 1
) version ON TRUE
CROSS JOIN generate_series(1, 10) AS rank(rank_no)
ON CONFLICT (attempt_id) DO NOTHING;

-- V33 intentionally improved the old V28 history, but its short arrays still
-- produced visibly repeated posts, replies and room messages. Make each seed
-- row distinct while retaining the same users, rooms, counts and timestamps.
UPDATE community_posts post
SET content = format(
        '%s %s Mình đang ở mốc học tập %s trong tuần và muốn nghe thêm cách mọi người xử lý.',
        (ARRAY[
            'Vừa hoàn thành một phiên Pomodoro tập trung nên mình ghi lại cách chia nhỏ đầu việc.',
            'Mọi người thường ghi chú lỗi Java theo cấu trúc nào để lần sau tra lại nhanh hơn?',
            'Sau khi ôn SQL JOIN mình thử tự viết lại truy vấn bằng dữ liệu mẫu để kiểm tra.',
            'React hooks có phần dependency nào mọi người hay nhầm khi refactor không?',
            'Mình đặt mục tiêu học 30 phút mỗi ngày để không bị dồn bài vào cuối tuần.',
            'Bài OOP hôm nay dễ hiểu hơn khi mình tách trách nhiệm của từng class.',
            'Bạn nào luyện TOEIC có cách gom từ vựng theo chủ đề để ôn lại hiệu quả không?',
            'Bài thuật toán chưa ra ngay nhưng ghi lại từng giả thuyết giúp mình debug tốt hơn.',
            'Tuần tới mình muốn hoàn thành mini project nhỏ thay vì học quá nhiều chủ đề.',
            'Cảm ơn các bạn đã gợi ý tài liệu, mình vừa sắp lại roadmap cá nhân.'
        ])[((seed.ordinal - 1) % 10) + 1],
        (ARRAY['Điểm vướng là phần kiểm tra đầu vào.', 'Mình đã thử lại bằng ví dụ nhỏ.', 'Kết quả tốt hơn sau khi có checklist.', 'Hy vọng nhận được góp ý thực tế.', 'Mình sẽ cập nhật kết quả vào cuối ngày.'])[((seed.ordinal - 1) % 5) + 1],
        seed.ordinal
    ),
    hashtags = (ARRAY['#Java #HocTap','#SQL #Database','#ReactJS #Frontend','#Pomodoro #KyLuat','#Algorithm #TuDuy'])[((seed.ordinal - 1) % 5) + 1],
    updated_at = CURRENT_TIMESTAMP
FROM generate_series(1, 30) AS seed(ordinal)
WHERE post.post_id = v55_v28_uuid('post:' || seed.ordinal);

UPDATE post_comments comment
SET content = format(
        '%s %s Mình ghi lại ý này vào checklist ôn tập số %s để thử trong buổi sau.',
        (ARRAY[
            'Mình cũng từng gặp tình huống này và thấy chia nhỏ bài ra làm hiệu quả hơn.',
            'Cách bạn theo dõi tiến độ khá rõ, mình sẽ thử áp dụng cho kế hoạch tuần này.',
            'Cảm ơn bạn đã chia sẻ ví dụ; phần minh họa giúp mình hiểu vấn đề nhanh hơn.',
            'Đúng rồi, giữ nhịp đều quan trọng hơn việc học dồn trong một buổi.',
            'Mình bổ sung thêm: làm lại bài vào hôm sau giúp nhớ kiến thức lâu hơn.',
            'Chúc bạn sớm hoàn thành mục tiêu; mình cũng đang ở phần tương tự.'
        ])[((seed.ordinal - 1) % 6) + 1],
        (ARRAY['Có thể bắt đầu từ một case nhỏ.', 'Nên lưu lại lỗi và nguyên nhân.', 'Thử so sánh trước và sau khi sửa.', 'Nếu có thời gian hãy viết thêm test.', 'Đừng quên kiểm tra lại giả định ban đầu.'])[((seed.ordinal - 1) % 5) + 1],
        seed.ordinal
    ),
    updated_at = CURRENT_TIMESTAMP
FROM generate_series(1, 100) AS seed(ordinal)
WHERE comment.comment_id = v55_v28_uuid('comment:' || seed.ordinal);

UPDATE community_chat_messages message
SET raw_content = format(
        '%s — %s Mình đang xử lý phần %s trong kế hoạch hôm nay, ai cùng chủ đề chia sẻ thêm nhé. %s',
        (ARRAY['Java & Spring Boot', 'TOEIC & IELTS', 'ReactJS & Next.js', 'IT & Computer Science', 'Pomodoro 500 giờ', 'Học viên SkillSprint/FPT'])[((seed.ordinal - 1) % 6) + 1],
        (ARRAY[
            'Mình vừa chia bài thành các phiên ngắn để dễ bắt đầu.',
            'Có đoạn nào cần mọi người góp ý về cách tiếp cận không?',
            'Mình để lại một ví dụ nhỏ để cả phòng cùng đối chiếu.',
            'Sau khi sửa lỗi mình đã ghi rõ nguyên nhân để tránh lặp lại.',
            'Ai có tài liệu nhập môn phù hợp thì gửi mình xin với.',
            'Mình đang thử làm lại bài không nhìn đáp án trước.',
            'Buổi tối nay mình dự định hoàn thành nốt phần thực hành.',
            'Mình vừa tổng hợp vài ghi chú ngắn từ buổi học hôm qua.',
            'Nếu ai rảnh mình muốn trao đổi nhanh một case thực tế.',
            'Mình thấy cách đặt checklist trước khi làm bài khá hữu ích.',
            'Hôm nay mình ưu tiên hiểu rõ một phần thay vì học lan man.',
            'Mình sẽ cập nhật kết quả sau khi chạy lại ví dụ.'
        ])[((seed.ordinal - 1) % 12) + 1],
        ((seed.ordinal - 1) % 9) + 1,
        (ARRAY[
            'Mình sẽ so sánh lại kết quả với mục tiêu đầu ngày.',
            'Sau đó mình thử lại bằng một ví dụ nhỏ trước.',
            'Mình sẽ ghi phần này vào checklist ôn tập.',
            'Tối nay mình dành thêm một phiên để thực hành.',
            'Nếu có hướng khác mình rất muốn đối chiếu thêm.'
        ])[((seed.ordinal - 1) % 5) + 1]
    ),
    masked_content = format(
        '%s — %s Mình đang xử lý phần %s trong kế hoạch hôm nay, ai cùng chủ đề chia sẻ thêm nhé. %s',
        (ARRAY['Java & Spring Boot', 'TOEIC & IELTS', 'ReactJS & Next.js', 'IT & Computer Science', 'Pomodoro 500 giờ', 'Học viên SkillSprint/FPT'])[((seed.ordinal - 1) % 6) + 1],
        (ARRAY[
            'Mình vừa chia bài thành các phiên ngắn để dễ bắt đầu.',
            'Có đoạn nào cần mọi người góp ý về cách tiếp cận không?',
            'Mình để lại một ví dụ nhỏ để cả phòng cùng đối chiếu.',
            'Sau khi sửa lỗi mình đã ghi rõ nguyên nhân để tránh lặp lại.',
            'Ai có tài liệu nhập môn phù hợp thì gửi mình xin với.',
            'Mình đang thử làm lại bài không nhìn đáp án trước.',
            'Buổi tối nay mình dự định hoàn thành nốt phần thực hành.',
            'Mình vừa tổng hợp vài ghi chú ngắn từ buổi học hôm qua.',
            'Nếu ai rảnh mình muốn trao đổi nhanh một case thực tế.',
            'Mình thấy cách đặt checklist trước khi làm bài khá hữu ích.',
            'Hôm nay mình ưu tiên hiểu rõ một phần thay vì học lan man.',
            'Mình sẽ cập nhật kết quả sau khi chạy lại ví dụ.'
        ])[((seed.ordinal - 1) % 12) + 1],
        ((seed.ordinal - 1) % 9) + 1,
        (ARRAY[
            'Mình sẽ so sánh lại kết quả với mục tiêu đầu ngày.',
            'Sau đó mình thử lại bằng một ví dụ nhỏ trước.',
            'Mình sẽ ghi phần này vào checklist ôn tập.',
            'Tối nay mình dành thêm một phiên để thực hành.',
            'Nếu có hướng khác mình rất muốn đối chiếu thêm.'
        ])[((seed.ordinal - 1) % 5) + 1]
    )
FROM generate_series(1, 150) AS seed(ordinal)
WHERE message.message_id = v55_v28_uuid('message:' || seed.ordinal);

UPDATE community_posts post
SET comment_count = (
        SELECT count(*) FROM post_comments comment WHERE comment.post_id = post.post_id
    ),
    like_count = (
        SELECT count(*) FROM post_likes like_row WHERE like_row.post_id = post.post_id
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE post.post_id IN (SELECT v55_v28_uuid('post:' || n) FROM generate_series(1, 30) AS n);

DO $$
BEGIN
    IF (SELECT count(*) FROM v55_pack_shapes) <> 11
       OR (SELECT count(*) FROM marketplace_quiz_pack_snapshots snapshot
           JOIN v55_pack_shapes shape ON shape.item_id = snapshot.item_id
           WHERE snapshot.question_count = shape.chapter_count * shape.questions_per_chapter
             AND snapshot.chapter_count = shape.chapter_count
             AND snapshot.quiz_count = shape.chapter_count) <> 11
       OR (SELECT count(DISTINCT snapshot.question_count) FROM marketplace_quiz_pack_snapshots snapshot
           JOIN v55_pack_shapes shape ON shape.item_id = snapshot.item_id) <> 11
       OR (SELECT count(*) FROM marketplace_items item
           JOIN v55_pack_shapes shape ON shape.item_id = item.item_id
           WHERE item.price_coins = 50000) <> 11
       OR (SELECT count(*) FROM marketplace_sales WHERE idempotency_key LIKE 'v28-sale-%'
           AND gross_coin_amount = 50000 AND original_gross_coin_amount = 50000) <> 150
       OR (SELECT COALESCE(sum(platform_amount), 0) FROM marketplace_sale_settlements settlement
           JOIN marketplace_sales sale ON sale.sale_id = settlement.sale_id
           WHERE sale.idempotency_key LIKE 'v28-sale-%') <> 1500000
       OR (SELECT COALESCE(sum(amount), 0) FROM platform_treasury_entries
           WHERE entry_type = 'MARKETPLACE_COMMISSION_EARNED'
             AND reference_type = 'SALE'
             AND reference_id IN (SELECT sale_id FROM marketplace_sales WHERE idempotency_key LIKE 'v28-sale-%')) <> 1500000
       OR (SELECT count(*) FROM marketplace_quiz_attempts
           WHERE attempt_id IN (
               SELECT v55_uuid(format('public-leaderboard:%s:%s', shape.item_id, rank.rank_no))
               FROM v55_pack_shapes shape CROSS JOIN generate_series(1, 10) AS rank(rank_no)
           )) <> 110
       OR (SELECT count(DISTINCT content) FROM (
               SELECT raw_content AS content FROM community_chat_messages
               WHERE message_id IN (SELECT v55_v28_uuid('message:' || n) FROM generate_series(1, 150) AS n)
           ) distinct_messages) <> 150 THEN
        RAISE EXCEPTION 'V55 postcondition failed; demo marketplace or community data is incomplete';
    END IF;
END $$;

DROP FUNCTION v55_v52_uuid(TEXT);
DROP FUNCTION v55_v28_uuid(TEXT);
DROP FUNCTION v55_uuid(TEXT);
