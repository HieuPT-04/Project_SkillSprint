CREATE TABLE platform_treasury_entries (
    treasury_entry_id UUID PRIMARY KEY,
    asset VARCHAR(10) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    entry_type VARCHAR(60) NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    actor_user_id VARCHAR(255),
    actor_name_snapshot VARCHAR(255),
    counterparty_user_id VARCHAR(255),
    counterparty_name_snapshot VARCHAR(255),
    external_reference VARCHAR(200),
    note TEXT,
    metadata JSONB,
    occurred_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_treasury_asset_occurred_at ON platform_treasury_entries(asset, occurred_at DESC);
CREATE INDEX idx_treasury_type_occurred_at ON platform_treasury_entries(entry_type, occurred_at DESC);
CREATE INDEX idx_treasury_reference ON platform_treasury_entries(reference_type, reference_id);

ALTER TABLE creator_payouts
    ADD COLUMN paid_vnd_amount NUMERIC(19, 2);
