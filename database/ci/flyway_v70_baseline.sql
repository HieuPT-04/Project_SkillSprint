-- Minimal production-shaped baseline for exercising pending migrations in CI.
-- Versions V2-V69 were introduced onto a pre-existing database outside Flyway,
-- so this fixture starts Flyway at version 69 and validates pending migrations
-- against the same payment-purpose constraint used in production.

CREATE TABLE users (
    user_id VARCHAR(100) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE service_plans (
    plan_id UUID PRIMARY KEY,
    plan_type VARCHAR(20) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE payment_transactions (
    payment_id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    plan_id UUID REFERENCES service_plans(plan_id),
    purpose VARCHAR(30) NOT NULL,
    coin_amount INTEGER,
    coin_package_key VARCHAR(50),
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_transactions_purpose_shape CHECK (
        (purpose = 'SUBSCRIPTION' AND plan_id IS NOT NULL AND coin_amount IS NULL AND coin_package_key IS NULL)
        OR
        (purpose = 'COIN_TOP_UP' AND plan_id IS NULL AND coin_amount IS NOT NULL
            AND coin_amount > 0 AND coin_package_key IS NOT NULL)
    )
);

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

CREATE TABLE community_posts (
    post_id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    content TEXT NOT NULL,
    hashtags TEXT,
    status VARCHAR(30) NOT NULL,
    like_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    report_count INTEGER NOT NULL DEFAULT 0,
    admin_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE post_comments (
    comment_id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES community_posts(post_id),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    report_count INTEGER NOT NULL DEFAULT 0,
    admin_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE post_likes (
    like_id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES community_posts(post_id),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_like_post_user UNIQUE (post_id, user_id)
);

CREATE TABLE content_reports (
    report_id UUID PRIMARY KEY,
    target_type VARCHAR(30) NOT NULL,
    target_id UUID NOT NULL,
    reporter_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    reason TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    reviewed_by VARCHAR(100) REFERENCES users(user_id),
    reviewed_at TIMESTAMPTZ,
    admin_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_content_report_target_reporter UNIQUE (target_type, target_id, reporter_id)
);

INSERT INTO users (user_id, email, created_at)
VALUES ('ci-reconciliation-user', 'reconciliation@gmail.com', TIMESTAMPTZ '2026-05-01 08:00:00+07');

INSERT INTO service_plans (plan_id, plan_type, is_active)
VALUES ('33333333-3333-3333-3333-333333333333', 'PREMIUM', TRUE);
