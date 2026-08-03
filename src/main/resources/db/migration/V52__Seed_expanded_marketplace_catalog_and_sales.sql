-- Expand the demo marketplace with complete, saleable Quiz Packs. Prices stay
-- below 50,000 Coin and every visible sale is backed by wallet funding,
-- entitlement, 80/20 settlement, creator earning and a valid ranked attempt.

CREATE FUNCTION v52_uuid(seed TEXT)
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

CREATE FUNCTION v52_v28_uuid(seed TEXT)
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

CREATE FUNCTION v52_v36_uuid(seed TEXT)
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

CREATE TEMP TABLE v52_catalog (
    pack_no INTEGER PRIMARY KEY,
    creator_product_no INTEGER NOT NULL,
    buyer_start INTEGER NOT NULL,
    sale_count INTEGER NOT NULL,
    review_count INTEGER NOT NULL,
    title TEXT NOT NULL,
    subject TEXT NOT NULL,
    description TEXT NOT NULL,
    price_coins INTEGER NOT NULL CHECK (price_coins > 0 AND price_coins <= 50000),
    chapter_titles TEXT[] NOT NULL,
    chapter_summaries TEXT[] NOT NULL
) ON COMMIT DROP;

INSERT INTO v52_catalog VALUES
(
    1, 1, 1, 23, 12,
    'Python Backend Thực Chiến: FastAPI, SQLAlchemy & Testing',
    'Python',
    'Lộ trình ôn tập dành cho người đã biết Python cơ bản và muốn xây API có cấu trúc rõ ràng. Pack đi từ quản lý môi trường, thiết kế request/response, làm việc với cơ sở dữ liệu đến kiểm thử và quan sát lỗi khi triển khai.',
    32000,
    ARRAY['Môi trường và cấu trúc dự án', 'Thiết kế API với FastAPI', 'Dữ liệu, ORM và transaction', 'Kiểm thử và vận hành'],
    ARRAY[
        'Thiết lập virtual environment, dependency lock và cấu trúc module để dự án có thể mở rộng mà không rối import.',
        'Thiết kế endpoint, schema Pydantic, validation và mã lỗi nhất quán cho client.',
        'Dùng SQLAlchemy theo session scope, xử lý transaction và tránh lỗi N+1 khi đọc dữ liệu quan hệ.',
        'Viết pytest cho luồng chính, ghi log có ngữ cảnh và xác định điểm cần theo dõi sau khi deploy.'
    ]
),
(
    2, 2, 16, 19, 10,
    'Node.js & Express: Xây REST API Production-ready',
    'Node.js',
    'Bộ câu hỏi theo tình huống thực tế khi xây REST API với Node.js: tổ chức router/service, xác thực, truy vấn dữ liệu, xử lý lỗi và kiểm soát chất lượng trước khi phát hành.',
    42000,
    ARRAY['Kiến trúc ứng dụng Express', 'Xác thực và phân quyền', 'Dữ liệu và hiệu năng', 'Bảo mật, logging và release'],
    ARRAY[
        'Tách router, controller, service và repository để thay đổi nghiệp vụ không làm vỡ HTTP contract.',
        'Áp dụng access token, kiểm tra quyền theo resource và xử lý vòng đời refresh token an toàn.',
        'Thiết kế pagination, index và transaction cho các luồng ghi dữ liệu có nhiều bước.',
        'Chuẩn hóa error response, che dữ liệu nhạy cảm trong log và kiểm tra health trước khi release.'
    ]
),
(
    3, 3, 31, 16, 9,
    'AWS Cloud Practitioner: Nền Tảng Cloud & Case Study',
    'AWS',
    'Pack hệ thống hóa kiến thức AWS theo các quyết định thường gặp: chọn compute, lưu trữ, mạng, IAM, chi phí và khả năng phục hồi. Nội dung phù hợp cho người chuẩn bị chứng chỉ Cloud Practitioner hoặc làm việc với sản phẩm cloud đầu tiên.',
    48000,
    ARRAY['Compute và mô hình triển khai', 'Storage, database và backup', 'Mạng, IAM và bảo mật', 'Chi phí, quan sát và độ tin cậy'],
    ARRAY[
        'Phân biệt EC2, Lambda, container và các tiêu chí chọn mô hình chạy ứng dụng.',
        'Chọn S3, EBS, RDS hoặc DynamoDB theo kiểu truy cập, độ bền dữ liệu và chiến lược backup.',
        'Làm rõ VPC, security group, IAM policy và nguyên tắc cấp quyền tối thiểu.',
        'Đọc tín hiệu chi phí, đặt budget alert và thiết kế cơ chế giảm ảnh hưởng khi một thành phần gặp lỗi.'
    ]
),
(
    4, 4, 43, 14, 8,
    'Business Analysis: BPMN, User Story & SQL Căn Bản',
    'Business Analysis',
    'Bộ đề dành cho BA mới vào nghề, tập trung vào cách bóc tách yêu cầu, mô tả quy trình, viết user story có tiêu chí chấp nhận và dùng SQL để kiểm tra dữ liệu nghiệp vụ.',
    36000,
    ARRAY['Khám phá vấn đề và stakeholder', 'BPMN và quy trình nghiệp vụ', 'User story và acceptance criteria', 'SQL kiểm tra dữ liệu nghiệp vụ'],
    ARRAY[
        'Xác định người liên quan, mục tiêu đo lường được và các giả định cần xác minh trước khi đề xuất giải pháp.',
        'Phân tích happy path, ngoại lệ và điểm bàn giao giữa các vai trò trong sơ đồ BPMN.',
        'Viết user story theo giá trị người dùng, điều kiện biên và tiêu chí chấp nhận có thể kiểm thử.',
        'Dùng SELECT, JOIN, GROUP BY và điều kiện lọc để đối soát dữ liệu sau khi thay đổi quy trình.'
    ]
),
(
    5, 5, 54, 12, 7,
    'Excel Phân Tích Dữ Liệu Cho Công Việc Văn Phòng',
    'Excel',
    'Nội dung thực hành phân tích bảng dữ liệu bán hàng và vận hành: làm sạch dữ liệu, công thức tra cứu, tổng hợp bằng PivotTable và trình bày dashboard gọn, dễ đọc cho người ra quyết định.',
    22000,
    ARRAY['Chuẩn hóa và làm sạch dữ liệu', 'Công thức tra cứu và điều kiện', 'PivotTable và phân tích xu hướng', 'Dashboard và kiểm tra số liệu'],
    ARRAY[
        'Nhận diện dữ liệu trùng, định dạng không nhất quán và cách chuẩn bị bảng nguồn có thể tái sử dụng.',
        'Kết hợp XLOOKUP, SUMIFS, IFERROR và tham chiếu có cấu trúc để trả lời câu hỏi nghiệp vụ.',
        'Tổng hợp doanh thu, tỷ trọng và xu hướng theo thời gian bằng PivotTable mà không làm thay đổi dữ liệu gốc.',
        'Chọn biểu đồ phù hợp, kiểm tra số tổng và ghi chú rõ nguồn số liệu trước khi chia sẻ dashboard.'
    ]
),
(
    6, 1, 64, 10, 6,
    'Git & CI/CD Cho Developer: Quy Trình Làm Việc Theo Team',
    'DevOps',
    'Pack mô phỏng quy trình phát triển theo nhóm: nhánh Git, pull request, xử lý conflict, pipeline CI, triển khai có kiểm soát và rollback khi bản phát hành có sự cố.',
    28000,
    ARRAY['Git workflow và quy ước nhánh', 'Pull request và code review', 'CI kiểm thử tự động', 'CD, quan sát và rollback'],
    ARRAY[
        'Chọn chiến lược nhánh, commit nhỏ có ý nghĩa và cách giữ lịch sử thay đổi dễ truy vết.',
        'Tổ chức pull request có mô tả, checklist, review có ngữ cảnh và xử lý conflict an toàn.',
        'Thiết kế pipeline chạy lint, test và build để chặn lỗi trước khi artifact được phát hành.',
        'Triển khai theo môi trường, theo dõi chỉ số sau release và rollback bằng artifact đã được xác thực.'
    ]
);

CREATE TEMP TABLE v52_questions ON COMMIT DROP AS
SELECT catalog.pack_no,
       chapter.chapter_no,
       question.question_no,
       catalog.chapter_titles[chapter.chapter_no] AS chapter_title,
       catalog.chapter_summaries[chapter.chapter_no] AS chapter_summary,
       CASE question.question_no
           WHEN 1 THEN format('Mục tiêu cốt lõi của chương "%s" là gì?', catalog.chapter_titles[chapter.chapter_no])
           WHEN 2 THEN format('Khi áp dụng %s trong thực tế, lựa chọn nào giúp giảm rủi ro triển khai?', lower(catalog.chapter_titles[chapter.chapter_no]))
           WHEN 3 THEN format('Dấu hiệu nào cho thấy phần "%s" đã được thực hiện đúng?', catalog.chapter_titles[chapter.chapter_no])
           WHEN 4 THEN format('Nếu kết quả ở chương "%s" chưa đạt kỳ vọng, bước tiếp theo hợp lý nhất là gì?', catalog.chapter_titles[chapter.chapter_no])
           ELSE format('Thực hành nào giúp duy trì chất lượng của "%s" khi dự án mở rộng?', catalog.chapter_titles[chapter.chapter_no])
       END AS question_text,
       CASE question.question_no
           WHEN 1 THEN format('Nắm được %s và áp dụng đúng vào đầu ra của chương.', lower(catalog.chapter_titles[chapter.chapter_no]))
           WHEN 2 THEN 'Xác định điều kiện đầu vào, kiểm tra thay đổi trên phạm vi nhỏ rồi mới mở rộng.'
           WHEN 3 THEN 'Có tiêu chí kiểm tra rõ ràng và kết quả có thể đối chiếu với yêu cầu ban đầu.'
           WHEN 4 THEN 'Khoanh vùng nguyên nhân bằng dữ liệu, điều chỉnh một giả định và kiểm tra lại.'
           ELSE 'Ghi nhận quy ước, tự động hóa bước lặp lại và rà soát định kỳ.'
       END AS correct_answer,
       CASE question.question_no
           WHEN 1 THEN 'Chỉ ghi nhớ định nghĩa mà không cần liên hệ với tình huống sử dụng.'
           WHEN 2 THEN 'Đưa toàn bộ thay đổi vào môi trường thật ngay khi code chạy được trên máy cá nhân.'
           WHEN 3 THEN 'Không cần kiểm tra miễn là giao diện hoặc câu trả lời trông hợp lý.'
           WHEN 4 THEN 'Đổi nhiều thành phần cùng lúc để có cơ hội thấy kết quả nhanh hơn.'
           ELSE 'Dựa vào trí nhớ cá nhân thay vì thống nhất cách làm trong nhóm.'
       END AS wrong_answer_1,
       'Bỏ qua bước xác minh vì đây là chi tiết không ảnh hưởng đến chất lượng chung.' AS wrong_answer_2,
       'Áp dụng một cấu hình cố định cho mọi tình huống mà không xem xét bối cảnh.' AS wrong_answer_3
FROM v52_catalog catalog
CROSS JOIN generate_series(1, 4) AS chapter(chapter_no)
CROSS JOIN generate_series(1, 5) AS question(question_no);

CREATE TEMP TABLE v52_chapter_content ON COMMIT DROP AS
SELECT question.pack_no,
       question.chapter_no,
       max(question.chapter_title) AS chapter_title,
       max(question.chapter_summary) AS chapter_summary,
       jsonb_agg(jsonb_build_object(
           'questionId', v52_uuid(format('question:%s:%s:%s', question.pack_no, question.chapter_no, question.question_no))::text,
           'type', 'SINGLE_CHOICE',
           'text', question.question_text,
           'explanation', question.chapter_summary,
           'sequenceNo', question.question_no,
           'options', jsonb_build_array(
               jsonb_build_object('optionId', v52_uuid(format('option:%s:%s:%s:1', question.pack_no, question.chapter_no, question.question_no))::text, 'label', 'A', 'text', question.correct_answer, 'correct', TRUE, 'sequenceNo', 1),
               jsonb_build_object('optionId', v52_uuid(format('option:%s:%s:%s:2', question.pack_no, question.chapter_no, question.question_no))::text, 'label', 'B', 'text', question.wrong_answer_1, 'correct', FALSE, 'sequenceNo', 2),
               jsonb_build_object('optionId', v52_uuid(format('option:%s:%s:%s:3', question.pack_no, question.chapter_no, question.question_no))::text, 'label', 'C', 'text', question.wrong_answer_2, 'correct', FALSE, 'sequenceNo', 3),
               jsonb_build_object('optionId', v52_uuid(format('option:%s:%s:%s:4', question.pack_no, question.chapter_no, question.question_no))::text, 'label', 'D', 'text', question.wrong_answer_3, 'correct', FALSE, 'sequenceNo', 4)
           )
       ) ORDER BY question.question_no) AS questions
FROM v52_questions question
GROUP BY question.pack_no, question.chapter_no;

CREATE TEMP TABLE v52_content ON COMMIT DROP AS
SELECT chapter.pack_no,
       jsonb_build_object('chapters', jsonb_agg(jsonb_build_object(
           'sequenceNo', chapter.chapter_no,
           'title', chapter.chapter_title,
           'summary', chapter.chapter_summary,
           'quiz', jsonb_build_object('title', 'Quiz ' || chapter.chapter_title, 'questions', chapter.questions)
       ) ORDER BY chapter.chapter_no)) AS content_json
FROM v52_chapter_content chapter
GROUP BY chapter.pack_no;

INSERT INTO marketplace_items (
    item_id, creator_id, source_workspace_id, title, description, subject, price_coins, status,
    creator_validation_score, reviewed_by, review_note, reviewed_at, published_at, created_at, updated_at
)
SELECT v52_uuid('item:' || catalog.pack_no),
       v52_v28_uuid('user:' || (179 + catalog.creator_product_no))::text,
       v52_v28_uuid('workspace:' || catalog.creator_product_no),
       catalog.title, catalog.description, catalog.subject, catalog.price_coins, 'PUBLISHED',
       96 + (catalog.pack_no % 5),
       (SELECT user_id FROM user_roles role_assignment JOIN roles role ON role.role_id = role_assignment.role_id
        WHERE role.role_name = 'ADMIN' AND role_assignment.workspace_id IS NULL ORDER BY role_assignment.granted_at NULLS LAST LIMIT 1),
       'Đã kiểm tra cấu trúc nội dung, đáp án và luồng Full Pack Challenge.',
       TIMESTAMPTZ '2026-06-05 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days'),
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days'),
       TIMESTAMPTZ '2026-05-22 09:00:00+07' + (catalog.pack_no * INTERVAL '2 days'),
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days')
FROM v52_catalog catalog;

INSERT INTO marketplace_quiz_pack_snapshots (
    snapshot_id, item_id, chapter_count, quiz_count, question_count, content_json, created_at, updated_at
)
SELECT v52_uuid('snapshot:' || catalog.pack_no), v52_uuid('item:' || catalog.pack_no),
       4, 4, 20, content.content_json,
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days'),
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days')
FROM v52_catalog catalog
JOIN v52_content content ON content.pack_no = catalog.pack_no;

INSERT INTO marketplace_packs (pack_id, creator_id, source_workspace_id, legacy_item_id, created_at, updated_at)
SELECT v52_uuid('pack:' || catalog.pack_no),
       v52_v28_uuid('user:' || (179 + catalog.creator_product_no))::text,
       v52_v28_uuid('workspace:' || catalog.creator_product_no),
       v52_uuid('item:' || catalog.pack_no),
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days'),
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days')
FROM v52_catalog catalog;

INSERT INTO marketplace_pack_versions (
    version_id, pack_id, version_no, status, update_type, legacy_item_id, title, description, subject,
    price_coins, creator_validation_score, reviewed_by, review_note, reviewed_at, chapter_count, quiz_count,
    question_count, content_json, saleable, published_at, created_at, updated_at
)
SELECT v52_uuid('version:' || catalog.pack_no), v52_uuid('pack:' || catalog.pack_no), 1,
       'PUBLISHED', 'MAJOR', v52_uuid('item:' || catalog.pack_no), catalog.title, catalog.description,
       catalog.subject, catalog.price_coins, 96 + (catalog.pack_no % 5),
       item.reviewed_by, item.review_note, item.reviewed_at, 4, 4, 20, content.content_json, TRUE,
       item.published_at, item.created_at, item.updated_at
FROM v52_catalog catalog
JOIN marketplace_items item ON item.item_id = v52_uuid('item:' || catalog.pack_no)
JOIN v52_content content ON content.pack_no = catalog.pack_no;

INSERT INTO marketplace_ranked_quiz_definitions (
    definition_id, pack_version_id, questions_per_step, total_question_count, daily_attempt_limit, created_at, updated_at
)
SELECT v52_uuid('definition:' || catalog.pack_no), v52_uuid('version:' || catalog.pack_no), 5, 20, 3,
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days'),
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (catalog.pack_no * INTERVAL '2 days')
FROM v52_catalog catalog;

INSERT INTO marketplace_ranked_question_selections (
    selection_id, definition_id, source_step_key, step_order, question_id, selection_order, created_at, updated_at
)
SELECT v52_uuid(format('selection:%s:%s:%s', question.pack_no, question.chapter_no, question.question_no)),
       v52_uuid('definition:' || question.pack_no), 'step-' || question.chapter_no,
       question.chapter_no,
       v52_uuid(format('question:%s:%s:%s', question.pack_no, question.chapter_no, question.question_no)),
       question.question_no,
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (question.pack_no * INTERVAL '2 days'),
       TIMESTAMPTZ '2026-06-06 10:00:00+07' + (question.pack_no * INTERVAL '2 days')
FROM v52_questions question;

CREATE TEMP TABLE v52_sales ON COMMIT DROP AS
SELECT catalog.pack_no,
       catalog.sale_count,
       catalog.review_count,
       catalog.price_coins,
       sale.sale_no,
       v52_v36_uuid('user:' || (catalog.buyer_start + sale.sale_no - 1))::text AS buyer_id,
       TIMESTAMPTZ '2026-06-08 09:00:00+07'
           + ((catalog.pack_no - 1) * INTERVAL '4 days')
           + (sale.sale_no * INTERVAL '13 hours 20 minutes') AS sold_at
FROM v52_catalog catalog
CROSS JOIN LATERAL generate_series(1, catalog.sale_count) AS sale(sale_no);

CREATE TEMP TABLE v52_buyer_funding ON COMMIT DROP AS
SELECT buyer_id,
       sum(price_coins)::integer AS required_coin,
       CASE WHEN sum(price_coins) <= 50000 THEN 50000 ELSE 100000 END AS funded_coin,
       min(sold_at) - INTERVAL '1 day' - ((get_byte(decode(md5(buyer_id), 'hex'), 0) % 4) * INTERVAL '1 hour') AS funded_at,
       max(sold_at) AS last_sale_at
FROM v52_sales
GROUP BY buyer_id;

INSERT INTO payment_transactions (
    payment_id, user_id, plan_id, purpose, coin_amount, coin_package_key, provider, status,
    txn_ref, amount, currency, subscription_months, transfer_content, expire_at, paid_at,
    provider_transaction_id, provider_reference_code, raw_callback_data, created_at, updated_at
)
SELECT v52_uuid('payment:' || funding.buyer_id), funding.buyer_id, NULL, 'COIN_TOP_UP', funding.funded_coin,
       CASE WHEN funding.funded_coin = 50000 THEN 'COIN_50000' ELSE 'COIN_100000' END,
       'SEPAY', 'PAID', 'SP52C' || lpad(row_number() OVER (ORDER BY funding.buyer_id)::text, 4, '0'),
       funding.funded_coin, 'VND', 0,
       'SP52C' || lpad(row_number() OVER (ORDER BY funding.buyer_id)::text, 4, '0'),
       funding.funded_at + INTERVAL '15 minutes', funding.funded_at + INTERVAL '3 minutes',
       'SP52-SEPAY-' || lpad(row_number() OVER (ORDER BY funding.buyer_id)::text, 4, '0'),
       'SP52REF-' || lpad(row_number() OVER (ORDER BY funding.buyer_id)::text, 4, '0'),
       jsonb_build_object('seed', 'V52', 'purpose', 'COIN_TOP_UP', 'coinAmount', funding.funded_coin),
       funding.funded_at, funding.funded_at + INTERVAL '3 minutes'
FROM v52_buyer_funding funding;

INSERT INTO user_wallets (wallet_id, user_id, balance, created_at, updated_at)
SELECT v52_uuid('wallet:' || funding.buyer_id), funding.buyer_id, funding.funded_coin,
       funding.funded_at, funding.last_sale_at
FROM v52_buyer_funding funding;

INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v52_uuid('wallet-credit:' || funding.buyer_id), v52_uuid('wallet:' || funding.buyer_id),
       'CREDIT', funding.funded_coin, 0, funding.funded_coin, 'COIN_TOP_UP',
       v52_uuid('payment:' || funding.buyer_id), funding.funded_at + INTERVAL '3 minutes', funding.funded_at + INTERVAL '3 minutes'
FROM v52_buyer_funding funding;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v52_uuid('top-up-treasury:' || funding.buyer_id), 'VND', 'CREDIT', 'COIN_TOP_UP_RECEIVED', 'PAYMENT',
       v52_uuid('payment:' || funding.buyer_id), funding.funded_coin, 'SYSTEM', funding.buyer_id, user_account.full_name,
       'SP52C-' || substr(md5(funding.buyer_id), 1, 8), 'Coin top-up received before marketplace purchase',
       jsonb_build_object('seed', 'V52', 'packageKey', CASE WHEN funding.funded_coin = 50000 THEN 'COIN_50000' ELSE 'COIN_100000' END),
       funding.funded_at + INTERVAL '3 minutes', 'COIN_TOP_UP_RECEIVED:' || v52_uuid('payment:' || funding.buyer_id),
       funding.funded_at + INTERVAL '3 minutes', funding.funded_at + INTERVAL '3 minutes'
FROM v52_buyer_funding funding
JOIN users user_account ON user_account.user_id = funding.buyer_id;

INSERT INTO marketplace_sales (
    sale_id, buyer_id, pack_id, pack_version_id, source_entitlement_id, gross_coin_amount,
    original_gross_coin_amount, discount_coin_amount, gross_vnd_amount, coin_to_vnd_rate,
    status, idempotency_key, created_at, updated_at
)
SELECT v52_uuid(format('sale:%s:%s', sale.pack_no, sale.sale_no)), sale.buyer_id,
       v52_uuid('pack:' || sale.pack_no), v52_uuid('version:' || sale.pack_no), NULL,
       sale.price_coins, sale.price_coins, 0, sale.price_coins, 1.0000, 'COMPLETED',
       'v52-sale-' || sale.pack_no || '-' || sale.sale_no, sale.sold_at, sale.sold_at
FROM v52_sales sale;

INSERT INTO marketplace_entitlements (
    entitlement_id, buyer_id, pack_version_id, source_sale_id, status, granted_at, created_at, updated_at
)
SELECT v52_uuid(format('entitlement:%s:%s', sale.pack_no, sale.sale_no)), sale.buyer_id,
       v52_uuid('version:' || sale.pack_no), v52_uuid(format('sale:%s:%s', sale.pack_no, sale.sale_no)),
       'ACTIVE', sale.sold_at, sale.sold_at, sale.sold_at
FROM v52_sales sale;

INSERT INTO marketplace_sale_settlements (
    settlement_id, sale_id, creator_id, creator_share_bps, creator_amount, platform_share_bps,
    platform_amount, coin_to_vnd_rate, status, created_at, updated_at
)
SELECT v52_uuid(format('settlement:%s:%s', sale.pack_no, sale.sale_no)),
       v52_uuid(format('sale:%s:%s', sale.pack_no, sale.sale_no)),
       pack.creator_id, 8000, sale.price_coins * 80 / 100, 2000, sale.price_coins * 20 / 100,
       1.0000, 'RECORDED', sale.sold_at, sale.sold_at
FROM v52_sales sale
JOIN marketplace_packs pack ON pack.pack_id = v52_uuid('pack:' || sale.pack_no);

INSERT INTO creator_earning_entries (
    earning_entry_id, creator_id, settlement_id, amount, state, created_at, updated_at
)
SELECT v52_uuid(format('earning:%s:%s', sale.pack_no, sale.sale_no)), pack.creator_id,
       v52_uuid(format('settlement:%s:%s', sale.pack_no, sale.sale_no)), sale.price_coins * 80 / 100,
       'PENDING', sale.sold_at, sale.sold_at
FROM v52_sales sale
JOIN marketplace_packs pack ON pack.pack_id = v52_uuid('pack:' || sale.pack_no);

INSERT INTO platform_revenue_entries (revenue_entry_id, settlement_id, sale_id, amount, created_at, updated_at)
SELECT v52_uuid(format('revenue:%s:%s', sale.pack_no, sale.sale_no)),
       v52_uuid(format('settlement:%s:%s', sale.pack_no, sale.sale_no)),
       v52_uuid(format('sale:%s:%s', sale.pack_no, sale.sale_no)), sale.price_coins * 20 / 100,
       sale.sold_at, sale.sold_at
FROM v52_sales sale;

INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v52_uuid(format('commission-treasury:%s:%s', sale.pack_no, sale.sale_no)),
       'COIN', 'CREDIT', 'MARKETPLACE_COMMISSION_EARNED', 'SALE',
       v52_uuid(format('sale:%s:%s', sale.pack_no, sale.sale_no)), sale.price_coins * 20 / 100,
       'SYSTEM', sale.buyer_id, buyer.full_name, 'v52-sale-' || sale.pack_no || '-' || sale.sale_no,
       'Marketplace commission', jsonb_build_object('settlementId', v52_uuid(format('settlement:%s:%s', sale.pack_no, sale.sale_no)), 'platformShareBps', 2000),
       sale.sold_at, 'MARKETPLACE_COMMISSION_EARNED:' || v52_uuid(format('sale:%s:%s', sale.pack_no, sale.sale_no)),
       sale.sold_at, sale.sold_at
FROM v52_sales sale
JOIN users buyer ON buyer.user_id = sale.buyer_id;

WITH ordered_sales AS (
    SELECT sale.*,
           funding.funded_coin,
           sum(sale.price_coins) OVER (
               PARTITION BY sale.buyer_id ORDER BY sale.sold_at, sale.pack_no, sale.sale_no
           ) AS running_spend
    FROM v52_sales sale
    JOIN v52_buyer_funding funding ON funding.buyer_id = sale.buyer_id
)
INSERT INTO wallet_transactions (
    transaction_id, wallet_id, direction, amount, balance_before, balance_after,
    reference_type, reference_id, created_at, updated_at
)
SELECT v52_uuid(format('wallet-debit:%s:%s', sale.pack_no, sale.sale_no)),
       v52_uuid('wallet:' || sale.buyer_id), 'DEBIT', sale.price_coins,
       sale.funded_coin - sale.running_spend + sale.price_coins,
       sale.funded_coin - sale.running_spend,
       'MARKETPLACE_SALE', v52_uuid(format('sale:%s:%s', sale.pack_no, sale.sale_no)), sale.sold_at, sale.sold_at
FROM ordered_sales sale;

UPDATE user_wallets wallet
SET balance = funding.funded_coin - funding.required_coin,
    updated_at = funding.last_sale_at
FROM v52_buyer_funding funding
WHERE wallet.wallet_id = v52_uuid('wallet:' || funding.buyer_id);

INSERT INTO marketplace_reviews (
    review_id, item_id, pack_version_id, user_id, rating, comment, created_at, updated_at
)
SELECT v52_uuid(format('review:%s:%s', sale.pack_no, sale.sale_no)),
       v52_uuid('item:' || sale.pack_no), v52_uuid('version:' || sale.pack_no), sale.buyer_id,
       (ARRAY[5, 5, 4, 5, 4, 5, 5, 4, 5, 5, 4, 5])[sale.sale_no],
       CASE sale.sale_no % 4
           WHEN 1 THEN 'Bố cục theo chương giúp mình biết rõ phần cần ôn lại. Câu hỏi có giải thích nên không chỉ dừng ở việc chọn đáp án.'
           WHEN 2 THEN 'Nội dung bám sát tình huống thực tế và mức độ câu hỏi tăng dần. Mình dùng tốt cho một buổi ôn ngắn trước khi làm bài.'
           WHEN 3 THEN 'Phần Full Pack Challenge tạo động lực học lại toàn bộ kiến thức. Mình mong sẽ có thêm bản cập nhật theo chủ đề chuyên sâu.'
           ELSE 'Giá trị nhất là phần tổng hợp và các câu kiểm tra điều kiện biên. Làm xong dễ nhận ra lỗ hổng kiến thức của mình.'
       END,
       sale.sold_at + INTERVAL '2 days' + ((sale.sale_no % 5) * INTERVAL '3 hours'),
       sale.sold_at + INTERVAL '2 days' + ((sale.sale_no % 5) * INTERVAL '3 hours')
FROM v52_sales sale
WHERE sale.sale_no <= sale.review_count;

CREATE TEMP TABLE v52_ranked_seed ON COMMIT DROP AS
SELECT sale.*,
       row_number() OVER (PARTITION BY sale.pack_no ORDER BY sale.sale_no) AS rank_no,
       (ARRAY[20, 19, 19, 18, 18, 17, 17, 16, 16, 15])[row_number() OVER (PARTITION BY sale.pack_no ORDER BY sale.sale_no)] AS correct_count
FROM v52_sales sale
WHERE sale.sale_no <= 10;

INSERT INTO marketplace_ranked_attempts (
    attempt_id, buyer_id, pack_version_id, definition_id, attempt_date, attempt_number, status,
    started_at, expires_at, completed_at, question_snapshot_json, answer_snapshot_json,
    submitted_answers_json, idempotency_key, request_fingerprint, score, correct_count,
    duration_seconds, suspicious, leaderboard_eligible, created_at, updated_at
)
SELECT v52_uuid(format('ranked-attempt:%s:%s', ranked.pack_no, ranked.rank_no)), ranked.buyer_id,
       v52_uuid('version:' || ranked.pack_no), v52_uuid('definition:' || ranked.pack_no),
       DATE '2026-07-22' - (((ranked.rank_no - 1) / 2)::integer), 100 + ranked.rank_no, 'COMPLETED',
       TIMESTAMPTZ '2026-07-22 20:00:00+07' - ((ranked.pack_no - 1) * INTERVAL '7 hours') - ((ranked.rank_no - 1) * INTERVAL '5 hours 20 minutes'),
       TIMESTAMPTZ '2026-07-22 21:00:00+07' - ((ranked.pack_no - 1) * INTERVAL '7 hours') - ((ranked.rank_no - 1) * INTERVAL '5 hours 20 minutes'),
       TIMESTAMPTZ '2026-07-22 20:00:00+07' - ((ranked.pack_no - 1) * INTERVAL '7 hours') - ((ranked.rank_no - 1) * INTERVAL '5 hours 20 minutes')
           + (((ARRAY[310, 354, 397, 442, 488, 535, 581, 639, 703, 781])[ranked.rank_no] + (get_byte(decode(md5(ranked.buyer_id), 'hex'), 0) % 31)) * INTERVAL '1 second'),
       snapshot.question_snapshot, snapshot.answer_snapshot, submitted.submitted_answers,
       v52_uuid(format('ranked-key:%s:%s', ranked.pack_no, ranked.rank_no)),
       md5('v52-ranked:' || ranked.pack_no || ':' || ranked.rank_no) || md5('v52-ranked-fingerprint:' || ranked.pack_no || ':' || ranked.rank_no),
       ranked.correct_count * 5, ranked.correct_count,
       (ARRAY[310, 354, 397, 442, 488, 535, 581, 639, 703, 781])[ranked.rank_no] + (get_byte(decode(md5(ranked.buyer_id), 'hex'), 0) % 31),
       FALSE, TRUE,
       TIMESTAMPTZ '2026-07-22 20:00:00+07' - ((ranked.pack_no - 1) * INTERVAL '7 hours') - ((ranked.rank_no - 1) * INTERVAL '5 hours 20 minutes'),
       TIMESTAMPTZ '2026-07-22 20:00:00+07' - ((ranked.pack_no - 1) * INTERVAL '7 hours') - ((ranked.rank_no - 1) * INTERVAL '5 hours 20 minutes')
           + (((ARRAY[310, 354, 397, 442, 488, 535, 581, 639, 703, 781])[ranked.rank_no] + (get_byte(decode(md5(ranked.buyer_id), 'hex'), 0) % 31)) * INTERVAL '1 second')
FROM v52_ranked_seed ranked
CROSS JOIN LATERAL (
    SELECT jsonb_build_object('questions', jsonb_agg(jsonb_build_object(
               'questionId', question.value ->> 'questionId',
               'type', question.value ->> 'type',
               'text', question.value ->> 'text',
               'options', (
                   SELECT jsonb_agg(jsonb_build_object('optionId', option.value ->> 'optionId', 'label', option.value ->> 'label', 'text', option.value ->> 'text') ORDER BY option.ordinality)
                   FROM jsonb_array_elements(question.value -> 'options') WITH ORDINALITY option(value, ordinality)
               )
           ) ORDER BY selection.step_order, selection.selection_order)) AS question_snapshot,
           jsonb_build_object('answers', jsonb_agg(jsonb_build_object(
               'questionId', question.value ->> 'questionId',
               'correctOptionId', correct_option.value ->> 'optionId'
           ) ORDER BY selection.step_order, selection.selection_order)) AS answer_snapshot
    FROM marketplace_ranked_question_selections selection
    JOIN marketplace_pack_versions version ON version.version_id = v52_uuid('version:' || ranked.pack_no)
    CROSS JOIN LATERAL jsonb_array_elements(version.content_json -> 'chapters') chapter(value)
    CROSS JOIN LATERAL jsonb_array_elements(chapter.value -> 'quiz' -> 'questions') question(value)
    CROSS JOIN LATERAL jsonb_array_elements(question.value -> 'options') correct_option(value)
    WHERE selection.definition_id = v52_uuid('definition:' || ranked.pack_no)
      AND selection.question_id::text = question.value ->> 'questionId'
      AND (correct_option.value ->> 'correct')::boolean
) snapshot
CROSS JOIN LATERAL (
    SELECT jsonb_build_object('answers', jsonb_agg(jsonb_build_object(
        'questionId', selected.question_json ->> 'questionId',
        'optionId', (
            SELECT option.value ->> 'optionId'
            FROM jsonb_array_elements(selected.question_json -> 'options') option(value)
            WHERE CASE WHEN selected.ordinal <= ranked.correct_count
                THEN (option.value ->> 'correct')::boolean
                ELSE NOT (option.value ->> 'correct')::boolean
            END
            ORDER BY option.value ->> 'optionId'
            LIMIT 1
        )
    ) ORDER BY selected.ordinal)) AS submitted_answers
    FROM (
        SELECT question.value AS question_json,
               row_number() OVER (ORDER BY selection.step_order, selection.selection_order) AS ordinal
        FROM marketplace_ranked_question_selections selection
        JOIN marketplace_pack_versions version ON version.version_id = v52_uuid('version:' || ranked.pack_no)
        CROSS JOIN LATERAL jsonb_array_elements(version.content_json -> 'chapters') chapter(value)
        CROSS JOIN LATERAL jsonb_array_elements(chapter.value -> 'quiz' -> 'questions') question(value)
        WHERE selection.definition_id = v52_uuid('definition:' || ranked.pack_no)
          AND selection.question_id::text = question.value ->> 'questionId'
    ) selected
) submitted;

DO $$
BEGIN
    IF (SELECT count(*) FROM marketplace_items WHERE item_id IN (SELECT v52_uuid('item:' || pack_no) FROM v52_catalog)) <> 6
       OR EXISTS (SELECT 1 FROM marketplace_items WHERE item_id IN (SELECT v52_uuid('item:' || pack_no) FROM v52_catalog) AND price_coins NOT BETWEEN 1 AND 50000)
       OR (SELECT count(*) FROM v52_sales) <> 94
       OR (SELECT COALESCE(sum(gross_coin_amount), 0) FROM marketplace_sales WHERE idempotency_key LIKE 'v52-sale-%') <> 3350000
       OR (SELECT count(*) FROM marketplace_entitlements WHERE source_sale_id IN (SELECT v52_uuid(format('sale:%s:%s', pack_no, sale_no)) FROM v52_sales)) <> 94
       OR (SELECT COALESCE(sum(creator_amount), 0) FROM marketplace_sale_settlements WHERE sale_id IN (SELECT v52_uuid(format('sale:%s:%s', pack_no, sale_no)) FROM v52_sales)) <> 2680000
       OR (SELECT COALESCE(sum(platform_amount), 0) FROM marketplace_sale_settlements WHERE sale_id IN (SELECT v52_uuid(format('sale:%s:%s', pack_no, sale_no)) FROM v52_sales)) <> 670000
       OR (SELECT count(*) FROM creator_earning_entries WHERE settlement_id IN (SELECT v52_uuid(format('settlement:%s:%s', pack_no, sale_no)) FROM v52_sales)) <> 94
       OR (SELECT COALESCE(sum(amount), 0) FROM platform_revenue_entries WHERE sale_id IN (SELECT v52_uuid(format('sale:%s:%s', pack_no, sale_no)) FROM v52_sales)) <> 670000
       OR (SELECT count(*) FROM v52_buyer_funding) <> 73
       OR (SELECT COALESCE(sum(amount), 0) FROM payment_transactions WHERE txn_ref LIKE 'SP52C%') <> 4600000
       OR (SELECT COALESCE(sum(amount), 0) FROM wallet_transactions WHERE reference_type = 'MARKETPLACE_SALE' AND reference_id IN (SELECT v52_uuid(format('sale:%s:%s', pack_no, sale_no)) FROM v52_sales)) <> 3350000
       OR (SELECT count(*) FROM marketplace_ranked_attempts WHERE attempt_id IN (SELECT v52_uuid(format('ranked-attempt:%s:%s', pack_no, rank_no)) FROM v52_ranked_seed)) <> 60
       OR EXISTS (
           SELECT 1 FROM v52_catalog catalog
           WHERE (SELECT count(*) FROM marketplace_ranked_attempts attempt
                  WHERE attempt.pack_version_id = v52_uuid('version:' || catalog.pack_no)
                    AND attempt.status = 'COMPLETED'
                    AND attempt.suspicious = FALSE
                    AND attempt.leaderboard_eligible = TRUE) < 10
       )
       OR EXISTS (
           SELECT 1
           FROM marketplace_ranked_attempts attempt
           LEFT JOIN marketplace_entitlements entitlement
             ON entitlement.buyer_id = attempt.buyer_id
            AND entitlement.pack_version_id = attempt.pack_version_id
            AND entitlement.status = 'ACTIVE'
           WHERE attempt.attempt_id IN (SELECT v52_uuid(format('ranked-attempt:%s:%s', pack_no, rank_no)) FROM v52_ranked_seed)
             AND entitlement.entitlement_id IS NULL
       ) THEN
        RAISE EXCEPTION 'V52 postcondition failed; marketplace catalog, buyer activity and commission ledger are inconsistent';
    END IF;
END $$;

DROP FUNCTION v52_v36_uuid(TEXT);
DROP FUNCTION v52_v28_uuid(TEXT);
DROP FUNCTION v52_uuid(TEXT);
