-- V28 was already applied on production before the Treasury enum mismatch was
-- noticed. Repair the persisted values so Hibernate can deserialize the rows.
UPDATE platform_treasury_entries
SET direction = 'CREDIT',
    entry_type = 'SUBSCRIPTION_PAYMENT_RECEIVED',
    updated_at = CURRENT_TIMESTAMP
WHERE idempotency_key LIKE 'v28-subscription-payment-%'
  AND (direction <> 'CREDIT' OR entry_type <> 'SUBSCRIPTION_PAYMENT_RECEIVED');
