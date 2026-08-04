-- Replace the V65 payout QR placeholder keys with verified private S3 objects.
-- Only rows that still contain the original placeholder are changed, so a QR
-- subsequently uploaded by the creator is never overwritten.

CREATE TEMP TABLE v75_creator_payout_qr_assets (
    destination_id UUID PRIMARY KEY,
    creator_id VARCHAR(100) NOT NULL,
    old_object_key VARCHAR(512) NOT NULL,
    new_object_key VARCHAR(512) NOT NULL
) ON COMMIT DROP;

INSERT INTO v75_creator_payout_qr_assets (
    destination_id,
    creator_id,
    old_object_key,
    new_object_key
) VALUES
    (
        '6dd138f1-3ff1-0f40-a564-01f346d5b07b',
        'c0cf93ef-dfaa-4ae8-c9ab-a4c436749f00',
        'payout-qr/vcb-hoang.png',
        'creator-payouts/c0cf93ef-dfaa-4ae8-c9ab-a4c436749f00/qr/144ccf7a-8d32-4e09-848f-44252702c890.png'
    ),
    (
        'acc3f5c6-1179-4832-3ae3-7eda1beb2390',
        '9a13be75-c63a-f9f4-e0a6-3477b3bfda4a',
        'payout-qr/tcb-ha.png',
        'creator-payouts/9a13be75-c63a-f9f4-e0a6-3477b3bfda4a/qr/53af4205-bff0-4ce8-967e-e76387b48be1.png'
    ),
    (
        '3e207c1e-dc2b-c0e6-6e5b-7fc7c60ec8c0',
        '35c31185-6dcb-dd32-7057-f9603c2a7e2c',
        'payout-qr/mb-long.png',
        'creator-payouts/35c31185-6dcb-dd32-7057-f9603c2a7e2c/qr/34647d46-f7ce-4730-b8f4-97ec9406e908.png'
    ),
    (
        'cb66e7da-4d26-beea-1e6a-378664fcd810',
        'df1ff9cd-b28b-1515-5735-78e949da9275',
        'payout-qr/acb-linh.png',
        'creator-payouts/df1ff9cd-b28b-1515-5735-78e949da9275/qr/9257e5fe-a3d7-4e89-974f-20152c75c488.png'
    ),
    (
        'a9189a79-e62e-75ed-3967-3aa1139759d5',
        'a2bfc927-8678-eae1-0d53-4e5fc7e105e4',
        'payout-qr/vpb-nam.png',
        'creator-payouts/a2bfc927-8678-eae1-0d53-4e5fc7e105e4/qr/1ea23b2d-d42c-43bb-ba2c-6062a502a6ee.png'
    );

UPDATE creator_payout_destinations AS destination
SET qr_object_key = asset.new_object_key,
    updated_at = CURRENT_TIMESTAMP
FROM v75_creator_payout_qr_assets AS asset
WHERE destination.destination_id = asset.destination_id
  AND destination.creator_id = asset.creator_id
  AND destination.qr_object_key = asset.old_object_key;

-- Payouts retain a destination snapshot, so their placeholder key must be
-- repaired separately from the creator's current destination.
UPDATE creator_payouts AS payout
SET destination_qr_object_key = asset.new_object_key
FROM v75_creator_payout_qr_assets AS asset
WHERE payout.destination_id = asset.destination_id
  AND payout.creator_id = asset.creator_id
  AND payout.destination_qr_object_key = asset.old_object_key;
