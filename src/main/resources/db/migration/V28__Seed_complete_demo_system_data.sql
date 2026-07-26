-- V28 is intentionally independent from the snapshot-bound V27 seed.
-- It creates its own inactive-login demo cohort and fails atomically if the
-- deployment is missing the real admin or subscription plans it needs.

CREATE FUNCTION v28_seed_uuid(seed TEXT)
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
    v_admin_id VARCHAR(100);
    v_premium_plan UUID;
    v_builder_plan UUID;
    v_now TIMESTAMPTZ := TIMESTAMPTZ '2026-07-26 10:00:00+07';
    v_product INTEGER;
    v_step INTEGER;
    v_question INTEGER;
    v_option INTEGER;
    v_row INTEGER;
    v_buyer INTEGER;
    v_user_id VARCHAR(100);
    v_creator_id VARCHAR(100);
    v_workspace_id UUID;
    v_structure_id UUID;
    v_roadmap_id UUID;
    v_step_id UUID;
    v_quiz_id UUID;
    v_item_id UUID;
    v_pack_id UUID;
    v_version_id UUID;
    v_definition_id UUID;
    v_payment_id UUID;
    v_sale_id UUID;
    v_settlement_id UUID;
    v_content JSONB;
    v_questions JSONB;
    v_answers JSONB;
    v_submitted_answers JSONB;
    v_title TEXT;
    v_subject TEXT;
    v_price INTEGER;
    v_paid_at TIMESTAMPTZ;
BEGIN
    SELECT ur.user_id
    INTO v_admin_id
    FROM user_roles ur
    JOIN roles r ON r.role_id = ur.role_id
    JOIN users u ON u.user_id = ur.user_id
    WHERE r.role_name = 'ADMIN' AND u.status = 'ACTIVE'
    ORDER BY ur.granted_at NULLS LAST, ur.user_id
    LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'V28 requires one existing ACTIVE ADMIN user; no demo admin is created';
    END IF;

    SELECT plan_id INTO v_premium_plan FROM service_plans WHERE plan_type = 'PREMIUM' LIMIT 1;
    SELECT plan_id INTO v_builder_plan FROM service_plans WHERE plan_type = 'SKILL_BUILDER' LIMIT 1;
    IF v_premium_plan IS NULL OR v_builder_plan IS NULL THEN
        RAISE EXCEPTION 'V28 requires PREMIUM and SKILL_BUILDER rows in service_plans';
    END IF;

    IF EXISTS (SELECT 1 FROM users WHERE email LIKE 'v28-demo-%@example.invalid') THEN
        RAISE EXCEPTION 'V28 demo users already exist; do not apply this migration to a partially seeded database';
    END IF;

    -- 184 isolated users: 179 learners/buyers plus 5 marketplace creators.
    FOR v_row IN 1..184 LOOP
        v_user_id := v28_seed_uuid('user:' || v_row)::text;
        INSERT INTO users (user_id, email, email_verified, full_name, timezone, status, created_at, updated_at)
        VALUES (
            v_user_id,
            'v28-demo-' || lpad(v_row::text, 3, '0') || '@example.invalid',
            TRUE,
            CASE WHEN v_row > 179 THEN 'Demo Creator ' || (v_row - 179) ELSE 'Demo Learner ' || v_row END,
            'Asia/Ho_Chi_Minh', 'ACTIVE', v_now - ((185 - v_row) * INTERVAL '8 hours'), v_now
        );
    END LOOP;

    -- Subscription revenue: 140 x 89,000 + 165 x 199,000 = 45,295,000 VND.
    FOR v_row IN 1..305 LOOP
        v_user_id := v28_seed_uuid('user:' || (((v_row - 1) % 184) + 1))::text;
        v_payment_id := v28_seed_uuid('payment:' || v_row);
        v_paid_at := TIMESTAMPTZ '2026-05-01 09:00:00+07' + ((v_row - 1) * INTERVAL '6 hours 45 minutes');
        INSERT INTO payment_transactions (
            payment_id, user_id, plan_id, purpose, provider, status, txn_ref, amount, currency,
            subscription_months, transfer_content, expire_at, paid_at, provider_transaction_id,
            provider_reference_code, raw_callback_data, created_at, updated_at
        ) VALUES (
            v_payment_id, v_user_id,
            CASE WHEN v_row <= 140 THEN v_builder_plan ELSE v_premium_plan END,
            'SUBSCRIPTION', 'SEPAY', 'PAID', 'V28SUB' || lpad(v_row::text, 4, '0'),
            CASE WHEN v_row <= 140 THEN 89000 ELSE 199000 END, 'VND', 1,
            'V28SUB' || lpad(v_row::text, 4, '0'), v_paid_at - INTERVAL '15 minutes', v_paid_at,
            'V28-SEPAY-' || lpad(v_row::text, 4, '0'), 'V28REF' || lpad(v_row::text, 4, '0'),
            jsonb_build_object('seed', 'V28', 'payment', v_row)::text, v_paid_at, v_paid_at
        );
        INSERT INTO platform_treasury_entries (
            treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
            actor_user_id, actor_name_snapshot, external_reference, note, metadata, occurred_at,
            idempotency_key, created_at, updated_at
        ) VALUES (
            v28_seed_uuid('treasury:' || v_row), 'VND', 'IN', 'SUBSCRIPTION_PAYMENT', 'PAYMENT', v_payment_id,
            CASE WHEN v_row <= 140 THEN 89000 ELSE 199000 END, v_user_id,
            (SELECT full_name FROM users WHERE user_id = v_user_id), 'V28SUB' || lpad(v_row::text, 4, '0'),
            'V28 realistic subscription payment', jsonb_build_object('seed', 'V28'), v_paid_at,
            'v28-subscription-payment-' || v_payment_id, v_paid_at, v_paid_at
        );
    END LOOP;

    -- Only V28 users receive a subscription. Existing production subscriptions are never touched.
    FOR v_row IN 1..184 LOOP
        v_user_id := v28_seed_uuid('user:' || v_row)::text;
        SELECT p.plan_id INTO v_premium_plan
        FROM payment_transactions p
        WHERE p.user_id = v_user_id AND p.txn_ref LIKE 'V28SUB%'
        ORDER BY p.paid_at DESC LIMIT 1;
        INSERT INTO subscriptions (subscription_id, user_id, plan_id, start_date, end_date, start_at, end_at, status, created_at)
        VALUES (v28_seed_uuid('subscription:' || v_row), v_user_id, v_premium_plan,
            DATE '2026-07-01', DATE '2026-08-01', TIMESTAMPTZ '2026-07-01 00:00:00+07',
            TIMESTAMPTZ '2026-08-01 00:00:00+07', 'ACTIVE', v_now);
    END LOOP;

    -- Five genuine source workspaces. Each has a confirmed structure, roadmap,
    -- four steps, and an ACTIVE five-question quiz per step.
    FOR v_product IN 1..5 LOOP
        v_creator_id := v28_seed_uuid('user:' || (179 + v_product))::text;
        v_workspace_id := v28_seed_uuid('workspace:' || v_product);
        v_structure_id := v28_seed_uuid('structure:' || v_product);
        v_roadmap_id := v28_seed_uuid('roadmap:' || v_product);
        v_title := (ARRAY[
            'Bộ Đề Luyện Thi Java Core & OOP 2026',
            'Chinh Phục ReactJS & TypeScript Thực Chiến',
            '100+ Câu Hỏi Phỏng Vấn SQL & Database Tuning',
            'Bộ Đề Tiếng Anh B2 Vstep / TOEIC 750+',
            'Cấu Trúc Dữ Liệu & Giải Thuật Thuần Thục'
        ])[v_product];
        v_subject := (ARRAY['Java', 'ReactJS', 'SQL', 'English', 'Algorithms'])[v_product];
        v_price := (ARRAY[50, 0, 30, 40, 0])[v_product];

        INSERT INTO study_workspaces (workspace_id, user_id, name, description, status, created_at, updated_at)
        VALUES (v_workspace_id, v_creator_id, 'V28 | ' || v_subject,
            'Workspace nguồn cho marketplace demo V28', 'ACTIVE', v_now - INTERVAL '45 days', v_now);
        INSERT INTO learning_structure_versions (
            structure_version_id, workspace_id, version_no, status, generated_by, ai_model,
            confidence_score, input_summary, warnings, created_at, confirmed_at
        ) VALUES (v_structure_id, v_workspace_id, 1, 'CONFIRMED', 'SYSTEM', 'seed-v28', 100.00,
            'Cấu trúc học tập đã xác nhận cho ' || v_subject, '[]'::jsonb, v_now - INTERVAL '44 days', v_now - INTERVAL '43 days');
        INSERT INTO roadmaps (
            roadmap_id, workspace_id, structure_version_id, user_id, title, description,
            total_steps, completed_steps, progress_percent, version_no, status, generated_at, updated_at
        ) VALUES (v_roadmap_id, v_workspace_id, v_structure_id, v_creator_id, 'Roadmap ' || v_subject,
            'Roadmap nguồn đã hoàn chỉnh cho marketplace', 4, 4, 100.00, 1, 'COMPLETED',
            v_now - INTERVAL '42 days', v_now - INTERVAL '20 days');

        FOR v_step IN 1..4 LOOP
            v_step_id := v28_seed_uuid(format('step:%s:%s', v_product, v_step));
            v_quiz_id := v28_seed_uuid(format('quiz:%s:%s', v_product, v_step));
            INSERT INTO roadmap_steps (
                step_id, roadmap_id, workspace_id, title, subtitle, summary, what_to_learn,
                key_concepts, learning_outcomes, recommended_focus, difficulty, estimated_study_time,
                estimated_minutes, sequence_no, status, completed_at, created_at, updated_at
            ) VALUES (
                v_step_id, v_roadmap_id, v_workspace_id, v_subject || ' - Chương ' || v_step,
                'Nền tảng và thực hành', 'Nội dung thực hành có quiz kiểm tra.', '["Nắm chắc lý thuyết"]'::jsonb,
                '["Khái niệm chính"]'::jsonb, '["Hoàn thành quiz"]'::jsonb, '["Làm đủ 5 câu"]'::jsonb,
                'INTERMEDIATE', '45 phút', 45, v_step, 'COMPLETED', v_now - ((5 - v_step) * INTERVAL '7 days'),
                v_now - INTERVAL '42 days', v_now - INTERVAL '20 days'
            );
            INSERT INTO quizzes (quiz_id, user_id, workspace_id, roadmap_step_id, title, passing_score, question_count, status, created_at, updated_at)
            VALUES (v_quiz_id, v_creator_id, v_workspace_id, v_step_id, 'Quiz ' || v_subject || ' chương ' || v_step,
                70, 5, 'ACTIVE', v_now - INTERVAL '35 days', v_now - INTERVAL '20 days');
            FOR v_question IN 1..5 LOOP
                INSERT INTO quiz_questions (question_id, quiz_id, type, question_text, explanation, sequence_no, created_at, updated_at)
                VALUES (v28_seed_uuid(format('question:%s:%s:%s', v_product, v_step, v_question)), v_quiz_id,
                    'SINGLE_CHOICE', v_subject || ' câu hỏi chương ' || v_step || ' số ' || v_question,
                    'Đáp án A là đáp án đúng cho dữ liệu demo.', v_question, v_now, v_now);
                FOR v_option IN 1..4 LOOP
                    INSERT INTO quiz_options (option_id, question_id, label, option_text, is_correct, sequence_no, created_at, updated_at)
                    VALUES (v28_seed_uuid(format('option:%s:%s:%s:%s', v_product, v_step, v_question, v_option)),
                        v28_seed_uuid(format('question:%s:%s:%s', v_product, v_step, v_question)),
                        chr(64 + v_option), 'Lựa chọn ' || chr(64 + v_option), v_option = 1, v_option, v_now, v_now);
                END LOOP;
            END LOOP;
        END LOOP;

        -- Build precisely the JSON shape that the quality validator reads.
        SELECT jsonb_build_object('chapters', jsonb_agg(chapter ORDER BY step_no))
        INTO v_content
        FROM (
            SELECT s.step_no, jsonb_build_object(
                'sequenceNo', s.step_no, 'title', v_subject || ' - Chương ' || s.step_no,
                'summary', 'Chương có đủ 5 câu xếp hạng.', 'quiz', jsonb_build_object(
                    'title', 'Quiz chương ' || s.step_no,
                    'questions', (
                        SELECT jsonb_agg(jsonb_build_object(
                            'questionId', v28_seed_uuid(format('question:%s:%s:%s', v_product, s.step_no, q.no))::text,
                            'type', 'SINGLE_CHOICE', 'text', v_subject || ' câu hỏi chương ' || s.step_no || ' số ' || q.no,
                            'explanation', 'Đáp án A là đúng.', 'sequenceNo', q.no,
                            'options', (
                                SELECT jsonb_agg(jsonb_build_object(
                                    'optionId', v28_seed_uuid(format('option:%s:%s:%s:%s', v_product, s.step_no, q.no, o.no))::text,
                                    'label', chr(64 + o.no), 'text', 'Lựa chọn ' || chr(64 + o.no),
                                    'correct', o.no = 1, 'sequenceNo', o.no) ORDER BY o.no)
                                FROM generate_series(1, 4) AS o(no)
                            )) ORDER BY q.no)
                        FROM generate_series(1, 5) AS q(no)
                    )
                )
            ) AS chapter
            FROM generate_series(1, 4) AS s(step_no)
        ) chapters;
        v_item_id := v28_seed_uuid('item:' || v_product);
        v_pack_id := v28_seed_uuid('pack:' || v_product);
        v_version_id := v28_seed_uuid('version:' || v_product);
        v_definition_id := v28_seed_uuid('definition:' || v_product);
        INSERT INTO marketplace_items (
            item_id, creator_id, source_workspace_id, title, description, subject, price_coins, status,
            creator_validation_score, reviewed_by, review_note, reviewed_at, published_at, created_at, updated_at
        ) VALUES (v_item_id, v_creator_id, v_workspace_id, v_title,
            'Nội dung demo V28 đã qua đủ luồng workspace, roadmap, quiz và xét duyệt.', v_subject, v_price,
            'PUBLISHED', 100, v_admin_id, 'Đã xét duyệt nội dung demo V28.', v_now - INTERVAL '10 days',
            v_now - INTERVAL '10 days', v_now - INTERVAL '40 days', v_now - INTERVAL '10 days');
        INSERT INTO marketplace_quiz_pack_snapshots (snapshot_id, item_id, chapter_count, quiz_count, question_count, content_json, created_at, updated_at)
        VALUES (v28_seed_uuid('snapshot:' || v_product), v_item_id, 4, 4, 20, v_content, v_now - INTERVAL '10 days', v_now - INTERVAL '10 days');
        INSERT INTO marketplace_packs (pack_id, creator_id, source_workspace_id, legacy_item_id, created_at, updated_at)
        VALUES (v_pack_id, v_creator_id, v_workspace_id, v_item_id, v_now - INTERVAL '10 days', v_now - INTERVAL '10 days');
        INSERT INTO marketplace_pack_versions (
            version_id, pack_id, version_no, status, update_type, legacy_item_id, title, description, subject,
            price_coins, creator_validation_score, reviewed_by, review_note, reviewed_at, chapter_count, quiz_count,
            question_count, content_json, saleable, published_at, created_at, updated_at
        ) VALUES (v_version_id, v_pack_id, 1, 'PUBLISHED', 'MAJOR', v_item_id, v_title,
            'Phiên bản V1 đã được kiểm định.', v_subject, v_price, 100, v_admin_id,
            'Chất lượng đạt 100/100.', v_now - INTERVAL '10 days', 4, 4, 20, v_content, TRUE,
            v_now - INTERVAL '10 days', v_now - INTERVAL '10 days', v_now - INTERVAL '10 days');
        INSERT INTO marketplace_ranked_quiz_definitions (definition_id, pack_version_id, questions_per_step, total_question_count, daily_attempt_limit, created_at, updated_at)
        VALUES (v_definition_id, v_version_id, 5, 20, 3, v_now - INTERVAL '10 days', v_now - INTERVAL '10 days');
        FOR v_step IN 1..4 LOOP
            FOR v_question IN 1..5 LOOP
                INSERT INTO marketplace_ranked_question_selections (
                    selection_id, definition_id, source_step_key, step_order, question_id, selection_order, created_at, updated_at
                ) VALUES (v28_seed_uuid(format('selection:%s:%s:%s', v_product, v_step, v_question)), v_definition_id,
                    'step-' || v_step, v_step, v28_seed_uuid(format('question:%s:%s:%s', v_product, v_step, v_question)),
                    v_question, v_now - INTERVAL '10 days', v_now - INTERVAL '10 days');
            END LOOP;
        END LOOP;
    END LOOP;

    -- 150 completed sales / active entitlements / ranked attempts: 30 per pack.
    FOR v_product IN 1..5 LOOP
        v_item_id := v28_seed_uuid('item:' || v_product);
        v_pack_id := v28_seed_uuid('pack:' || v_product);
        v_version_id := v28_seed_uuid('version:' || v_product);
        v_definition_id := v28_seed_uuid('definition:' || v_product);
        v_creator_id := v28_seed_uuid('user:' || (179 + v_product))::text;
        v_price := (ARRAY[50, 0, 30, 40, 0])[v_product];
        SELECT content_json INTO v_content FROM marketplace_pack_versions WHERE version_id = v_version_id;
        -- Persist the same snapshot contract produced by MarketplaceRankedAttemptService:
        -- learners can open historical attempts instead of seeing malformed JSON.
        SELECT jsonb_build_object('questions', jsonb_agg(jsonb_build_object(
            'questionId', question.value ->> 'questionId',
            'type', question.value ->> 'type',
            'text', question.value ->> 'text',
            'options', (
                SELECT jsonb_agg(jsonb_build_object(
                    'optionId', option.value ->> 'optionId',
                    'label', option.value ->> 'label',
                    'text', option.value ->> 'text'
                ) ORDER BY option.ordinality)
                FROM jsonb_array_elements(question.value -> 'options') WITH ORDINALITY option(value, ordinality)
            )
        ) ORDER BY chapter.ordinality, question.ordinality))
        INTO v_questions
        FROM jsonb_array_elements(v_content -> 'chapters') WITH ORDINALITY chapter(value, ordinality)
        CROSS JOIN LATERAL jsonb_array_elements(chapter.value -> 'quiz' -> 'questions') WITH ORDINALITY question(value, ordinality);
        SELECT jsonb_build_object('answers', jsonb_agg(jsonb_build_object(
            'questionId', question.value ->> 'questionId',
            'correctOptionId', option.value ->> 'optionId'
        ) ORDER BY chapter.ordinality, question.ordinality))
        INTO v_answers
        FROM jsonb_array_elements(v_content -> 'chapters') WITH ORDINALITY chapter(value, ordinality)
        CROSS JOIN LATERAL jsonb_array_elements(chapter.value -> 'quiz' -> 'questions') WITH ORDINALITY question(value, ordinality)
        CROSS JOIN LATERAL jsonb_array_elements(question.value -> 'options') option(value)
        WHERE (option.value ->> 'correct')::boolean;
        SELECT jsonb_build_object('answers', jsonb_agg(jsonb_build_object(
            'questionId', question.value ->> 'questionId',
            'optionId', option.value ->> 'optionId'
        ) ORDER BY chapter.ordinality, question.ordinality))
        INTO v_submitted_answers
        FROM jsonb_array_elements(v_content -> 'chapters') WITH ORDINALITY chapter(value, ordinality)
        CROSS JOIN LATERAL jsonb_array_elements(chapter.value -> 'quiz' -> 'questions') WITH ORDINALITY question(value, ordinality)
        CROSS JOIN LATERAL jsonb_array_elements(question.value -> 'options') option(value)
        WHERE (option.value ->> 'correct')::boolean;
        FOR v_buyer IN 1..30 LOOP
            v_user_id := v28_seed_uuid('user:' || ((v_product - 1) * 30 + v_buyer))::text;
            v_sale_id := v28_seed_uuid(format('sale:%s:%s', v_product, v_buyer));
            v_settlement_id := v28_seed_uuid(format('settlement:%s:%s', v_product, v_buyer));
            INSERT INTO marketplace_sales (
                sale_id, buyer_id, pack_id, pack_version_id, gross_coin_amount, original_gross_coin_amount,
                discount_coin_amount, gross_vnd_amount, coin_to_vnd_rate, status, idempotency_key, created_at, updated_at
            ) VALUES (v_sale_id, v_user_id, v_pack_id, v_version_id, v_price, v_price, 0, v_price * 1000,
                1000.0000, 'COMPLETED', 'v28-sale-' || v_product || '-' || v_buyer,
                v_now - ((31 - v_buyer) * INTERVAL '6 hours'), v_now);
            INSERT INTO marketplace_entitlements (entitlement_id, buyer_id, pack_version_id, source_sale_id, status, granted_at, created_at, updated_at)
            VALUES (v28_seed_uuid(format('entitlement:%s:%s', v_product, v_buyer)), v_user_id, v_version_id, v_sale_id,
                'ACTIVE', v_now - ((31 - v_buyer) * INTERVAL '6 hours'), v_now, v_now);
            INSERT INTO marketplace_sale_settlements (
                settlement_id, sale_id, creator_id, creator_share_bps, creator_amount, platform_share_bps,
                platform_amount, coin_to_vnd_rate, status, created_at, updated_at
            ) VALUES (v_settlement_id, v_sale_id, v_creator_id, 8000, v_price * 800, 2000, v_price * 200,
                1000.0000, 'RECORDED', v_now, v_now);
            INSERT INTO creator_earning_entries (earning_entry_id, creator_id, settlement_id, amount, state, created_at, updated_at)
            VALUES (v28_seed_uuid(format('earning:%s:%s', v_product, v_buyer)), v_creator_id, v_settlement_id,
                v_price * 800, 'PENDING', v_now, v_now);
            INSERT INTO platform_revenue_entries (revenue_entry_id, settlement_id, sale_id, amount, created_at, updated_at)
            VALUES (v28_seed_uuid(format('revenue:%s:%s', v_product, v_buyer)), v_settlement_id, v_sale_id,
                v_price * 200, v_now, v_now);
            INSERT INTO marketplace_ranked_attempts (
                attempt_id, buyer_id, pack_version_id, definition_id, attempt_date, attempt_number, status,
                started_at, expires_at, completed_at, question_snapshot_json, answer_snapshot_json,
                submitted_answers_json, idempotency_key, request_fingerprint, score, correct_count,
                duration_seconds, suspicious, leaderboard_eligible, created_at, updated_at
            ) VALUES (
                v28_seed_uuid(format('attempt:%s:%s', v_product, v_buyer)), v_user_id, v_version_id, v_definition_id,
                DATE '2026-07-01' + v_buyer, 1, 'COMPLETED', v_now - ((31 - v_buyer) * INTERVAL '6 hours'),
                v_now + INTERVAL '1 day', v_now - ((31 - v_buyer) * INTERVAL '6 hours') + INTERVAL '8 minutes',
                v_questions, v_answers, v_submitted_answers, v28_seed_uuid(format('attempt-key:%s:%s', v_product, v_buyer)),
                md5('v28-ranked-' || v_product || '-' || v_buyer), 100, 20,
                300 + (v_buyer * 5), FALSE, TRUE, v_now, v_now
            );
            IF v_buyer <= 3 THEN
                INSERT INTO marketplace_reviews (review_id, item_id, pack_version_id, user_id, rating, comment, created_at, updated_at)
                VALUES (v28_seed_uuid(format('review:%s:%s', v_product, v_buyer)), v_item_id, v_version_id, v_user_id,
                    5, 'Nội dung rõ ràng và quiz hữu ích.', v_now, v_now);
            END IF;
        END LOOP;
    END LOOP;

    -- Community feed: 30 approved posts, 100 visible comments and 323 unique likes.
    FOR v_row IN 1..30 LOOP
        INSERT INTO community_posts (post_id, user_id, content, hashtags, status, like_count, comment_count, report_count, created_at, updated_at)
        VALUES (v28_seed_uuid('post:' || v_row), v28_seed_uuid('user:' || ((v_row % 179) + 1))::text,
            'Chia sẻ tiến độ học tập demo V28 số ' || v_row || '. Cùng duy trì thói quen mỗi ngày!',
            '#SkillSprint #HocTap', 'APPROVED', 0, 0, 0, v_now - (v_row * INTERVAL '1 day'), v_now);
    END LOOP;
    FOR v_row IN 1..100 LOOP
        INSERT INTO post_comments (comment_id, post_id, user_id, content, status, report_count, created_at, updated_at)
        VALUES (v28_seed_uuid('comment:' || v_row), v28_seed_uuid('post:' || (((v_row - 1) % 30) + 1)),
            v28_seed_uuid('user:' || (((v_row * 7) % 179) + 1))::text,
            'Bình luận demo V28 số ' || v_row || ': cùng cố gắng nhé!', 'VISIBLE', 0, v_now, v_now);
    END LOOP;
    FOR v_row IN 1..323 LOOP
        INSERT INTO post_likes (like_id, post_id, user_id, created_at, updated_at)
        VALUES (v28_seed_uuid('like:' || v_row), v28_seed_uuid('post:' || (((v_row - 1) % 30) + 1)),
            v28_seed_uuid('user:' || (((v_row * 11) % 179) + 1))::text, v_now, v_now);
    END LOOP;
    UPDATE community_posts p SET
        comment_count = (SELECT count(*) FROM post_comments c WHERE c.post_id = p.post_id),
        like_count = (SELECT count(*) FROM post_likes l WHERE l.post_id = p.post_id)
    WHERE p.post_id IN (SELECT v28_seed_uuid('post:' || n) FROM generate_series(1, 30) n);

    -- Six rooms with exactly 155 active members and 150 messages.
    FOR v_row IN 1..6 LOOP
        INSERT INTO community_rooms (room_id, name, description, mode, status, owner_id, max_members, member_count, report_count, created_at, updated_at)
        VALUES (v28_seed_uuid('room:' || v_row), (ARRAY['Java & Spring Boot', 'TOEIC & IELTS', 'ReactJS & Next.js', 'IT & Computer Science', 'Pomodoro 500 giờ', 'Học viên SkillSprint/FPT'])[v_row],
            'Phòng thảo luận demo V28', 'PUBLIC', 'ACTIVE', v28_seed_uuid('user:' || (179 + ((v_row - 1) % 5) + 1))::text,
            50, 0, 0, v_now - INTERVAL '20 days', v_now);
        FOR v_buyer IN 1..CASE WHEN v_row = 6 THEN 25 ELSE 26 END LOOP
            v_user_id := CASE WHEN v_buyer = 1
                THEN v28_seed_uuid('user:' || (179 + ((v_row - 1) % 5) + 1))::text
                ELSE v28_seed_uuid('user:' || (((v_row * 31 + v_buyer) % 179) + 1))::text END;
            INSERT INTO community_room_members (member_id, room_id, user_id, role, banned, status, created_at, updated_at)
            VALUES (v28_seed_uuid(format('room-member:%s:%s', v_row, v_buyer)), v28_seed_uuid('room:' || v_row), v_user_id,
                CASE WHEN v_buyer = 1 THEN 'OWNER' ELSE 'MEMBER' END, FALSE, 'ACTIVE', v_now - INTERVAL '20 days', v_now);
        END LOOP;
    END LOOP;
    FOR v_row IN 1..150 LOOP
        INSERT INTO community_chat_messages (message_id, room_id, sender_id, raw_content, masked_content, hidden, report_count, sent_at)
        VALUES (v28_seed_uuid('message:' || v_row), v28_seed_uuid('room:' || (((v_row - 1) % 6) + 1)),
            CASE WHEN ((v_row - 1) % 25) = 0
                THEN v28_seed_uuid('user:' || (179 + (((v_row - 1) % 6) % 5) + 1))::text
                ELSE v28_seed_uuid('user:' || (((((v_row - 1) % 6) + 1) * 31 + (((v_row - 1) % 25) + 1)) % 179 + 1))::text END,
            'Tin nhắn thảo luận demo V28 số ' || v_row, 'Tin nhắn thảo luận demo V28 số ' || v_row,
            FALSE, 0, v_now - ((151 - v_row) * INTERVAL '45 minutes'));
    END LOOP;
    UPDATE community_rooms r SET member_count = (
        SELECT count(*) FROM community_room_members m WHERE m.room_id = r.room_id AND m.status = 'ACTIVE'
    ) WHERE r.room_id IN (SELECT v28_seed_uuid('room:' || n) FROM generate_series(1, 6) n);

    -- All feedback is visibly resolved by a pre-existing real admin; no admin account is seeded.
    FOR v_row IN 1..4 LOOP
        INSERT INTO feedbacks (feedback_id, user_id, type, title, content, status, admin_reply, replied_at, replied_by_user_id, created_at, updated_at)
        VALUES (v28_seed_uuid('feedback:' || v_row), v28_seed_uuid('user:' || v_row)::text,
            (ARRAY['IMPROVEMENT', 'OTHER', 'BUG', 'IMPROVEMENT'])[v_row],
            (ARRAY['Đề xuất Dark Mode', 'Khen AI Tutor', 'Báo lỗi Pomodoro mobile', 'Phản hồi về thanh toán SePay'])[v_row],
            'Phản hồi demo V28 để kiểm tra màn quản trị.', 'RESOLVED',
            'Cảm ơn bạn. Đội ngũ đã ghi nhận và xử lý phản hồi này.', v_now - INTERVAL '2 days', v_admin_id,
            v_now - INTERVAL '5 days', v_now - INTERVAL '2 days');
    END LOOP;

    -- Deterministic postconditions make a broken source schema fail at migration time.
    IF (SELECT count(*) FROM payment_transactions WHERE txn_ref LIKE 'V28SUB%') <> 305
       OR (SELECT count(*) FROM platform_treasury_entries WHERE idempotency_key LIKE 'v28-subscription-payment-%') <> 305
       OR (SELECT count(*) FROM marketplace_items WHERE title IN (
           'Bộ Đề Luyện Thi Java Core & OOP 2026', 'Chinh Phục ReactJS & TypeScript Thực Chiến',
           '100+ Câu Hỏi Phỏng Vấn SQL & Database Tuning', 'Bộ Đề Tiếng Anh B2 Vstep / TOEIC 750+',
           'Cấu Trúc Dữ Liệu & Giải Thuật Thuần Thục')) <> 5
       OR (SELECT count(*) FROM marketplace_ranked_attempts WHERE attempt_id IN (
            SELECT v28_seed_uuid(format('attempt:%s:%s', product_no, buyer_no))
            FROM generate_series(1, 5) product_no CROSS JOIN generate_series(1, 30) buyer_no
       )) <> 150
       OR (SELECT count(*) FROM community_posts WHERE post_id IN (SELECT v28_seed_uuid('post:' || n) FROM generate_series(1, 30) n)) <> 30
       OR (SELECT count(*) FROM post_comments WHERE comment_id IN (SELECT v28_seed_uuid('comment:' || n) FROM generate_series(1, 100) n)) <> 100
       OR (SELECT count(*) FROM post_likes WHERE like_id IN (SELECT v28_seed_uuid('like:' || n) FROM generate_series(1, 323) n)) <> 323
       OR (SELECT count(*) FROM community_chat_messages WHERE message_id IN (SELECT v28_seed_uuid('message:' || n) FROM generate_series(1, 150) n)) <> 150
       OR (SELECT count(*) FROM feedbacks WHERE feedback_id IN (SELECT v28_seed_uuid('feedback:' || n) FROM generate_series(1, 4) n)) <> 4 THEN
        RAISE EXCEPTION 'V28 postcondition failed; seed transaction is rolled back';
    END IF;
END $$;

DROP FUNCTION v28_seed_uuid(TEXT);
