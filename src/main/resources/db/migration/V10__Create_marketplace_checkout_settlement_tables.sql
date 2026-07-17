-- Marketplace Plan 3A: version-aware checkout and immutable settlement foundation.
--
-- These tables are additive. Historical marketplace_purchases remain untouched:
-- they do not contain the immutable settlement data needed to recreate Creator
-- earnings safely, so this migration deliberately does not invent a backfill.

CREATE TABLE marketplace_sales (
    sale_id UUID PRIMARY KEY,
    buyer_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    pack_id UUID NOT NULL REFERENCES marketplace_packs(pack_id),
    pack_version_id UUID NOT NULL REFERENCES marketplace_pack_versions(version_id),
    source_entitlement_id UUID,
    gross_coin_amount INTEGER NOT NULL CHECK (gross_coin_amount >= 0),
    gross_vnd_amount BIGINT NOT NULL CHECK (gross_vnd_amount >= 0),
    coin_to_vnd_rate NUMERIC(12, 4) NOT NULL CHECK (coin_to_vnd_rate > 0),
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_marketplace_sales_buyer_idempotency UNIQUE (buyer_id, idempotency_key),
    CONSTRAINT ck_marketplace_sales_status CHECK (status IN ('COMPLETED', 'CANCELED', 'REFUNDED'))
);

CREATE INDEX idx_marketplace_sales_buyer_created_at
    ON marketplace_sales (buyer_id, created_at DESC);
CREATE INDEX idx_marketplace_sales_pack_version
    ON marketplace_sales (pack_version_id);

CREATE TABLE marketplace_entitlements (
    entitlement_id UUID PRIMARY KEY,
    buyer_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    pack_version_id UUID NOT NULL REFERENCES marketplace_pack_versions(version_id),
    source_sale_id UUID NOT NULL REFERENCES marketplace_sales(sale_id),
    status VARCHAR(30) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_marketplace_entitlements_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_marketplace_entitlements_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL) OR
        (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

ALTER TABLE marketplace_sales
    ADD CONSTRAINT fk_marketplace_sales_source_entitlement
    FOREIGN KEY (source_entitlement_id) REFERENCES marketplace_entitlements(entitlement_id);

CREATE UNIQUE INDEX uq_marketplace_entitlements_active_buyer_version
    ON marketplace_entitlements (buyer_id, pack_version_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_marketplace_entitlements_buyer_status
    ON marketplace_entitlements (buyer_id, status);

CREATE TABLE marketplace_sale_settlements (
    settlement_id UUID PRIMARY KEY,
    sale_id UUID NOT NULL UNIQUE REFERENCES marketplace_sales(sale_id),
    creator_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    creator_share_bps INTEGER NOT NULL CHECK (creator_share_bps BETWEEN 0 AND 10000),
    creator_amount INTEGER NOT NULL CHECK (creator_amount >= 0),
    platform_share_bps INTEGER NOT NULL CHECK (platform_share_bps BETWEEN 0 AND 10000),
    platform_amount INTEGER NOT NULL CHECK (platform_amount >= 0),
    coin_to_vnd_rate NUMERIC(12, 4) NOT NULL CHECK (coin_to_vnd_rate > 0),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_marketplace_sale_settlements_share_total CHECK (
        creator_share_bps + platform_share_bps = 10000
    ),
    CONSTRAINT ck_marketplace_sale_settlements_status CHECK (status IN ('RECORDED', 'REVERSED'))
);

CREATE INDEX idx_marketplace_sale_settlements_creator
    ON marketplace_sale_settlements (creator_id, created_at DESC);

CREATE TABLE creator_payout_destinations (
    destination_id UUID PRIMARY KEY,
    creator_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    bank_name VARCHAR(150) NOT NULL,
    bank_code VARCHAR(50),
    account_holder VARCHAR(200) NOT NULL,
    account_number_encrypted TEXT,
    qr_object_key VARCHAR(512) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_creator_payout_destinations_active_creator
    ON creator_payout_destinations (creator_id)
    WHERE active;

CREATE TABLE creator_payouts (
    payout_id UUID PRIMARY KEY,
    creator_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    destination_id UUID NOT NULL REFERENCES creator_payout_destinations(destination_id),
    requested_amount INTEGER NOT NULL CHECK (requested_amount > 0),
    status VARCHAR(30) NOT NULL,
    destination_bank_name VARCHAR(150) NOT NULL,
    destination_bank_code VARCHAR(50),
    destination_account_holder VARCHAR(200) NOT NULL,
    destination_account_number_encrypted TEXT,
    destination_qr_object_key VARCHAR(512) NOT NULL,
    admin_actor_id VARCHAR(100) REFERENCES users(user_id),
    external_transfer_reference VARCHAR(200),
    rejection_reason TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_creator_payouts_status CHECK (
        status IN ('REQUESTED', 'APPROVED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'FAILED')
    )
);

CREATE INDEX idx_creator_payouts_creator_status
    ON creator_payouts (creator_id, status, created_at DESC);
CREATE INDEX idx_creator_payouts_status_created_at
    ON creator_payouts (status, created_at ASC);

CREATE TABLE creator_earning_entries (
    earning_entry_id UUID PRIMARY KEY,
    creator_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    settlement_id UUID NOT NULL UNIQUE REFERENCES marketplace_sale_settlements(settlement_id),
    payout_id UUID REFERENCES creator_payouts(payout_id),
    amount INTEGER NOT NULL CHECK (amount >= 0),
    state VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_creator_earning_entries_state CHECK (
        state IN ('PENDING', 'RESERVED', 'PAID', 'REVERSED')
    )
);

CREATE INDEX idx_creator_earning_entries_creator_state
    ON creator_earning_entries (creator_id, state, created_at DESC);
CREATE INDEX idx_creator_earning_entries_payout
    ON creator_earning_entries (payout_id)
    WHERE payout_id IS NOT NULL;

CREATE TABLE platform_revenue_entries (
    revenue_entry_id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL UNIQUE REFERENCES marketplace_sale_settlements(settlement_id),
    sale_id UUID NOT NULL UNIQUE REFERENCES marketplace_sales(sale_id),
    amount INTEGER NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_platform_revenue_entries_created_at
    ON platform_revenue_entries (created_at DESC);
