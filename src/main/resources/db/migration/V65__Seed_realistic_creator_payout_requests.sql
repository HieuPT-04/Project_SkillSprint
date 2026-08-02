-- Seeds realistic Creator Payout (Withdrawal) Requests across all statuses (REQUESTED, APPROVED, PROCESSING, COMPLETED, REJECTED, FAILED)
-- with natural Vietnamese creator names, bank destinations, and realistic amounts (500,000đ - 3,200,000đ).

CREATE FUNCTION v65_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v65:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v65:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v65:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v65:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v65:' || seed), 21, 12))::uuid;
$$;

CREATE FUNCTION v65_v36_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v36:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v36:' || seed), 21, 12))::uuid;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM creator_payouts WHERE external_transfer_reference LIKE 'SP65%') THEN
        RAISE EXCEPTION 'V65 creator payout seed already applied';
    END IF;
END $$;

-- 1. Create or update Creator Payout Destinations
CREATE TEMP TABLE v65_creators ON COMMIT DROP AS
SELECT row_no,
       v65_v36_uuid('user:' || user_no)::text AS user_id,
       bank_name, bank_code, account_holder, account_number, qr_key
FROM (VALUES
    (1,  5, 'Vietcombank', 'VCB', 'HOANG PHAN',      '99283716254', 'payout-qr/vcb-hoang.png'),
    (2, 12, 'Techcombank', 'TCB', 'NGUYEN THI THU HA', '19038472910', 'payout-qr/tcb-ha.png'),
    (3, 22, 'MBBank',      'MB',  'TRAN HOANG LONG',   '0987654321',  'payout-qr/mb-long.png'),
    (4, 33, 'ACB',         'ACB', 'LE KHANH LINH',     '883716254',   'payout-qr/acb-linh.png'),
    (5, 48, 'VPBank',      'VPB', 'DANG BAO NAM',      '158372619',   'payout-qr/vpb-nam.png')
) AS c(row_no, user_no, bank_name, bank_code, account_holder, account_number, qr_key);

INSERT INTO creator_payout_destinations (
    destination_id, creator_id, bank_name, bank_code, account_holder, account_number_encrypted,
    qr_object_key, active, created_at, updated_at
)
SELECT v65_uuid('dest:' || row_no), user_id, bank_name, bank_code, account_holder, account_number,
       qr_key, TRUE, TIMESTAMPTZ '2026-07-20 09:00:00+07', TIMESTAMPTZ '2026-07-20 09:00:00+07'
FROM v65_creators
ON CONFLICT (creator_id) WHERE active DO UPDATE
SET bank_name = EXCLUDED.bank_name,
    bank_code = EXCLUDED.bank_code,
    account_holder = EXCLUDED.account_holder,
    account_number_encrypted = EXCLUDED.account_number_encrypted,
    qr_object_key = EXCLUDED.qr_object_key,
    updated_at = EXCLUDED.updated_at;

-- 2. Clean up or fix any unreal 500đ test request if exists
UPDATE creator_payouts
SET requested_amount = 500000,
    paid_vnd_amount = 500000.00,
    status = 'COMPLETED',
    external_transfer_reference = 'SP65REF-FIX-500K'
WHERE requested_amount = 500 AND status = 'PROCESSING';

-- 3. Seed Realistic Payout Requests across all statuses
CREATE TEMP TABLE v65_payout_requests ON COMMIT DROP AS
SELECT row_no,
       v65_v36_uuid('user:' || user_no)::text AS user_id,
       v65_uuid('dest:' || dest_no) AS destination_id,
       requested_amount,
       status,
       bank_name, bank_code, account_holder, account_number, qr_key,
       transfer_ref, paid_vnd, rejection_reason, notes,
       created_at, updated_at
FROM (VALUES
    -- REQUESTED (Chờ duyệt)
    (1,  12, 2, 500000,  'REQUESTED', 'Techcombank', 'TCB', 'NGUYEN THI THU HA', '19038472910', 'payout-qr/tcb-ha.png', NULL, NULL, NULL, 'Chờ Admin duyệt yêu cầu rút tiền', TIMESTAMPTZ '2026-08-03 09:15:00+07', TIMESTAMPTZ '2026-08-03 09:15:00+07'),
    (2,  22, 3, 1200000, 'REQUESTED', 'MBBank',      'MB',  'TRAN HOANG LONG',   '0987654321',  'payout-qr/mb-long.png', NULL, NULL, NULL, 'Chờ Admin kiểm tra thông tin', TIMESTAMPTZ '2026-08-02 16:30:00+07', TIMESTAMPTZ '2026-08-02 16:30:00+07'),
    (3,  33, 4, 850000,  'REQUESTED', 'ACB',         'ACB', 'LE KHANH LINH',     '883716254',   'payout-qr/acb-linh.png', NULL, NULL, NULL, 'Yêu cầu rút tiền mới', TIMESTAMPTZ '2026-08-02 11:45:00+07', TIMESTAMPTZ '2026-08-02 11:45:00+07'),

    -- APPROVED (Đã duyệt)
    (4,  48, 5, 650000,  'APPROVED',  'VPBank',      'VPB', 'DANG BAO NAM',      '158372619',   'payout-qr/vpb-nam.png', NULL, NULL, NULL, 'Đã phê duyệt, chờ bộ phận kế toán thực hiện lệnh chuyển khoản', TIMESTAMPTZ '2026-08-02 08:20:00+07', TIMESTAMPTZ '2026-08-02 10:10:00+07'),

    -- PROCESSING (Đang chuyển)
    (5,   5, 1, 2500000, 'PROCESSING', 'Vietcombank', 'VCB', 'HOANG PHAN',      '99283716254', 'payout-qr/vcb-hoang.png', NULL, NULL, NULL, 'Đang thực hiện chuyển khoản qua VietQR ngân hàng Vietcombank', TIMESTAMPTZ '2026-08-01 14:00:00+07', TIMESTAMPTZ '2026-08-01 14:45:00+07'),
    (6,  12, 2, 1000000, 'PROCESSING', 'Techcombank', 'TCB', 'NGUYEN THI THU HA', '19038472910', 'payout-qr/tcb-ha.png', NULL, NULL, NULL, 'Lệnh chuyển khoản đang xử lý', TIMESTAMPTZ '2026-08-01 09:30:00+07', TIMESTAMPTZ '2026-08-01 10:15:00+07'),

    -- COMPLETED (Hoàn tất)
    (7,   5, 1, 3200000, 'COMPLETED',  'Vietcombank', 'VCB', 'HOANG PHAN',      '99283716254', 'payout-qr/vcb-hoang.png', 'SP65REF-VCB-3200K', 3200000.00, NULL, 'Đã chuyển khoản thành công qua Internet Banking Vietcombank', TIMESTAMPTZ '2026-07-28 10:00:00+07', TIMESTAMPTZ '2026-07-28 10:30:00+07'),
    (8,  22, 3, 1800000, 'COMPLETED',  'MBBank',      'MB',  'TRAN HOANG LONG',   '0987654321',  'payout-qr/mb-long.png',  'SP65REF-MB-1800K',  1800000.00, NULL, 'Đã chuyển khoản thành công qua ứng dụng MBBank', TIMESTAMPTZ '2026-07-29 15:20:00+07', TIMESTAMPTZ '2026-07-29 15:50:00+07'),
    (9,  33, 4, 1500000, 'COMPLETED',  'ACB',         'ACB', 'LE KHANH LINH',     '883716254',   'payout-qr/acb-linh.png', 'SP65REF-ACB-1500K', 1500000.00, NULL, 'Chuyển khoản thành công', TIMESTAMPTZ '2026-07-30 11:10:00+07', TIMESTAMPTZ '2026-07-30 11:40:00+07'),
    (10, 48, 5, 2100000, 'COMPLETED',  'VPBank',      'VPB', 'DANG BAO NAM',      '158372619',   'payout-qr/vpb-nam.png',  'SP65REF-VPB-2100K', 2100000.00, NULL, 'Chuyển khoản thành công', TIMESTAMPTZ '2026-07-31 17:40:00+07', TIMESTAMPTZ '2026-07-31 18:15:00+07'),

    -- REJECTED (Từ chối)
    (11, 22, 3, 300000,  'REJECTED',   'MBBank',      'MB',  'TRAN HOANG LONG',   '0987654321',  'payout-qr/mb-long.png',  NULL, NULL, 'Số dư chưa đủ hạn mức tối thiểu rút tiền (500,000đ)', 'Từ chối do chưa đạt hạn mức rút tiền tối thiểu', TIMESTAMPTZ '2026-07-25 14:15:00+07', TIMESTAMPTZ '2026-07-25 15:00:00+07'),

    -- FAILED (Lỗi)
    (12, 12, 2, 450000,  'FAILED',     'Techcombank', 'TCB', 'NGUYEN THI THU HA', '19038472910', 'payout-qr/tcb-ha.png',  NULL, NULL, 'Tên chủ tài khoản ngân hàng không trùng khớp với hồ sơ Creator', 'Giao dịch chuyển khoản thất bại từ phía ngân hàng', TIMESTAMPTZ '2026-07-26 09:50:00+07', TIMESTAMPTZ '2026-07-26 10:20:00+07')
) AS p(row_no, user_no, dest_no, requested_amount, status, bank_name, bank_code, account_holder, account_number, qr_key, transfer_ref, paid_vnd, rejection_reason, notes, created_at, updated_at);

INSERT INTO creator_payouts (
    payout_id, creator_id, destination_id, requested_amount, status,
    destination_bank_name, destination_bank_code, destination_account_holder,
    destination_account_number_encrypted, destination_qr_object_key,
    external_transfer_reference, paid_vnd_amount, rejection_reason, notes,
    created_at, updated_at
)
SELECT v65_uuid('payout:' || req.row_no), req.user_id, req.destination_id, req.requested_amount, req.status,
       req.bank_name, req.bank_code, req.account_holder, req.account_number, req.qr_key,
       req.transfer_ref, req.paid_vnd, req.rejection_reason, req.notes,
       req.created_at, req.updated_at
FROM v65_payout_requests req;

-- 4. Record Treasury Entries for COMPLETED payouts
INSERT INTO platform_treasury_entries (
    treasury_entry_id, asset, direction, entry_type, reference_type, reference_id, amount,
    actor_name_snapshot, counterparty_user_id, counterparty_name_snapshot, external_reference,
    note, metadata, occurred_at, idempotency_key, created_at, updated_at
)
SELECT v65_uuid('treasury-payout:' || req.row_no), 'VND', 'DEBIT', 'CREATOR_PAYOUT_COMPLETED',
       'CREATOR_PAYOUT', v65_uuid('payout:' || req.row_no), req.paid_vnd, 'SYSTEM', req.user_id,
       account.full_name, req.transfer_ref, 'Creator payout completed',
       jsonb_build_object('seed', 'V65', 'requestedCoinAmount', req.requested_amount), req.updated_at,
       'CREATOR_PAYOUT_COMPLETED:' || v65_uuid('payout:' || req.row_no), req.updated_at, req.updated_at
FROM v65_payout_requests req
JOIN users account ON account.user_id = req.user_id
WHERE req.status = 'COMPLETED' AND req.paid_vnd IS NOT NULL;

-- Postcondition Assertion to guarantee consistency
DO $$
BEGIN
    IF (SELECT count(*) FROM creator_payouts WHERE destination_bank_name IN ('Vietcombank', 'Techcombank', 'MBBank', 'ACB', 'VPBank')) < 12 THEN
        RAISE EXCEPTION 'V65 postcondition failed; creator payout seeding is incomplete';
    END IF;
END $$;

DROP FUNCTION v65_v36_uuid(TEXT);
DROP FUNCTION v65_uuid(TEXT);
