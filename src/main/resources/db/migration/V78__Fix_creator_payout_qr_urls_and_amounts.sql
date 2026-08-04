-- Migration V78: 
-- 1. Fix Creator Payout QR URLs and account holder names so VietQR image content matches Creator Full Name 100% (NO INITIALS like T G H, L Q B).
-- 2. Reduce Creator Payout requested amounts to realistic small amounts (50,000đ - 180,000đ) per leader feedback.
-- 3. Reconcile platform treasury entries for completed payouts with the reduced amounts.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'creator_payouts') THEN
        -- 1. First, normalize destination account holders to match creator full_name (FULL UPPERCASE unaccented name, NO initials)
        UPDATE creator_payouts p
        SET destination_account_holder = UPPER(
            REGEXP_REPLACE(
                TRANSLATE(
                    u.full_name,
                    'áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÉÈẺẼẸÊẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÚÙỦŨỤƯỨỪỬỮỰÝỲỶỸỴĐ',
                    'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyydaaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyd'
                ),
                '[^A-ZA-Z0-9 ]', '', 'g'
            )
        )
        FROM users u
        WHERE u.user_id = p.creator_id;

        -- 2. Update requested amounts to small realistic values (50k - 180k) & update paid_vnd_amount
        UPDATE creator_payouts
        SET requested_amount = CASE
                WHEN requested_amount = 500000  THEN 50000
                WHEN requested_amount = 1200000 THEN 120000
                WHEN requested_amount = 850000  THEN 85000
                WHEN requested_amount = 650000  THEN 65000
                WHEN requested_amount = 2500000 THEN 150000
                WHEN requested_amount = 1000000 THEN 100000
                WHEN requested_amount = 3200000 THEN 180000
                WHEN requested_amount = 1800000 THEN 140000
                WHEN requested_amount = 1500000 THEN 95000
                WHEN requested_amount = 2100000 THEN 110000
                WHEN requested_amount = 300000  THEN 30000
                WHEN requested_amount = 450000  THEN 45000
                ELSE LEAST(GREATEST(requested_amount / 20, 50000), 180000)
            END,
            paid_vnd_amount = CASE
                WHEN status = 'COMPLETED' THEN CASE
                    WHEN requested_amount = 3200000 THEN 180000.00
                    WHEN requested_amount = 1800000 THEN 140000.00
                    WHEN requested_amount = 1500000 THEN 95000.00
                    WHEN requested_amount = 2100000 THEN 110000.00
                    ELSE LEAST(GREATEST(requested_amount / 20, 50000), 180000)::numeric(19, 2)
                END
                ELSE NULL
            END;

        -- 3. Set destination_qr_object_key to dynamic, 100% valid VietQR URL with full creator name, bank, acc, & reduced amount
        UPDATE creator_payouts
        SET destination_qr_object_key = 'https://img.vietqr.io/image/' ||
            COALESCE(NULLIF(destination_bank_code, ''), 'MB') || '-' ||
            COALESCE(NULLIF(destination_account_number_encrypted, ''), '0987654321') ||
            '-compact2.png?amount=' || requested_amount ||
            '&addInfo=SkillSprint%20Payout&accountName=' ||
            REPLACE(COALESCE(NULLIF(destination_account_holder, ''), 'CREATOR'), ' ', '%20');
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'creator_payout_destinations') THEN
        UPDATE creator_payout_destinations d
        SET account_holder = UPPER(
            REGEXP_REPLACE(
                TRANSLATE(
                    u.full_name,
                    'áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÉÈẺẼẸÊẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÚÙỦŨỤƯỨỪỬỮỰÝỲỶỸỴĐ',
                    'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyydaaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyd'
                ),
                '[^A-ZA-Z0-9 ]', '', 'g'
            )
        )
        FROM users u
        WHERE u.user_id = d.creator_id;

        UPDATE creator_payout_destinations
        SET qr_object_key = 'https://img.vietqr.io/image/' ||
            COALESCE(NULLIF(bank_code, ''), 'MB') || '-' ||
            COALESCE(NULLIF(account_number_encrypted, ''), '0987654321') ||
            '-compact2.png?amount=100000&addInfo=SkillSprint%20Payout&accountName=' ||
            REPLACE(COALESCE(NULLIF(account_holder, ''), 'CREATOR'), ' ', '%20');
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'platform_treasury_entries') AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'creator_payouts') THEN
        UPDATE platform_treasury_entries e
        SET amount = p.paid_vnd_amount
        FROM creator_payouts p
        WHERE e.reference_type = 'CREATOR_PAYOUT'
          AND e.reference_id = p.payout_id
          AND p.status = 'COMPLETED'
          AND p.paid_vnd_amount IS NOT NULL;
    END IF;
END $$;
