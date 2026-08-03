-- Marketplace Plan 2: Coin top-up payment model and ledger foundation.
--
-- Additive only. Existing subscription payment rows keep their meaning: they are
-- backfilled to purpose = 'SUBSCRIPTION' and none is rewritten or repurposed.

CREATE TABLE IF NOT EXISTS payment_transactions (
    payment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    plan_id UUID REFERENCES service_plans(plan_id) ON DELETE SET NULL,
    provider VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    txn_ref VARCHAR(100) NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    subscription_months INTEGER DEFAULT 0,
    transfer_content VARCHAR(255),
    expire_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    provider_transaction_id VARCHAR(255),
    provider_reference_code VARCHAR(255),
    raw_callback_data JSONB,
    bank_account_name VARCHAR(255),
    bank_account_number VARCHAR(255),
    bank_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(30);

-- Every pre-existing payment row is a subscription payment by definition.
UPDATE payment_transactions SET purpose = 'SUBSCRIPTION' WHERE purpose IS NULL;

ALTER TABLE payment_transactions
    ALTER COLUMN purpose SET DEFAULT 'SUBSCRIPTION';
ALTER TABLE payment_transactions
    ALTER COLUMN purpose SET NOT NULL;

-- 2. Coin top-up fields ------------------------------------------------------

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS coin_amount INTEGER;
ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS coin_package_key VARCHAR(50);

-- A Coin top-up has no service plan, so plan_id can no longer be mandatory.
-- Existing subscription rows keep their plan; the shape check below guarantees
-- a subscription payment still always has one.
ALTER TABLE payment_transactions
    ALTER COLUMN plan_id DROP NOT NULL;

-- The two purposes are structurally disjoint at the database level, so a
-- subscription row can never be read or rewritten as a top-up row and vice versa.
ALTER TABLE payment_transactions
    ADD CONSTRAINT ck_payment_transactions_purpose_shape CHECK (
        (
            purpose = 'SUBSCRIPTION'
            AND plan_id IS NOT NULL
            AND coin_amount IS NULL
            AND coin_package_key IS NULL
        )
        OR (
            purpose = 'COIN_TOP_UP'
            AND plan_id IS NULL
            AND coin_amount IS NOT NULL
            AND coin_amount > 0
            AND coin_package_key IS NOT NULL
        )
    );

CREATE INDEX idx_payment_transactions_purpose_status
    ON payment_transactions (purpose, status);

-- 3. Wallet ledger -----------------------------------------------------------

-- MARKETPLACE_PLATFORM_COMMISSION is 31 characters and does not fit VARCHAR(30).
ALTER TABLE wallet_transactions
    ALTER COLUMN reference_type TYPE VARCHAR(40);

-- Exactly-once Coin credit per top-up payment. The service also checks the
-- payment status under a row lock; this index is the authority under concurrent
-- or repeated webhook delivery.
CREATE UNIQUE INDEX uq_wallet_transactions_coin_top_up_reference
    ON wallet_transactions (reference_id)
    WHERE reference_type = 'COIN_TOP_UP';
