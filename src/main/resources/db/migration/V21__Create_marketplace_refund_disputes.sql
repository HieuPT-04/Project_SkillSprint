-- Marketplace Plan 6E: buyer refund disputes with an auditable, idempotent refund.
--
-- APPROVED records only the admin decision. A separate refund-completion step moves
-- money and transitions the dispute to REFUNDED, storing the compensating wallet
-- ledger transaction id. Reversal is achieved with compensating records only:
-- the sale becomes REFUNDED, the settlement REVERSED, the creator earning REVERSED,
-- the entitlement REVOKED, and the buyer wallet receives a MARKETPLACE_REFUND credit.

CREATE TABLE marketplace_refund_disputes (
    dispute_id UUID PRIMARY KEY,
    sale_id UUID NOT NULL REFERENCES marketplace_sales(sale_id),
    buyer_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    pack_version_id UUID NOT NULL REFERENCES marketplace_pack_versions(version_id),
    reason VARCHAR(30) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    admin_actor_id VARCHAR(100) REFERENCES users(user_id),
    decision_note TEXT,
    decided_at TIMESTAMP WITH TIME ZONE,
    refunded_at TIMESTAMP WITH TIME ZONE,
    refund_coin_amount INTEGER CHECK (refund_coin_amount IS NULL OR refund_coin_amount >= 0),
    refund_wallet_transaction_id UUID REFERENCES wallet_transactions(transaction_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_marketplace_refund_disputes_status
        CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'REFUNDED')),
    CONSTRAINT ck_marketplace_refund_disputes_reason
        CHECK (reason IN (
            'NOT_AS_DESCRIBED', 'POOR_QUALITY', 'TECHNICAL_ISSUE', 'ACCIDENTAL_PURCHASE', 'OTHER'
        )),
    -- A decided dispute must record who decided, when, and why.
    CONSTRAINT ck_marketplace_refund_disputes_decision
        CHECK (
            (status IN ('OPEN', 'UNDER_REVIEW')
                AND decided_at IS NULL AND admin_actor_id IS NULL AND decision_note IS NULL)
            OR (status IN ('APPROVED', 'REJECTED', 'REFUNDED')
                AND decided_at IS NOT NULL AND admin_actor_id IS NOT NULL AND decision_note IS NOT NULL)
        ),
    -- Only a REFUNDED dispute may carry refund execution details. A zero-coin sale (free pack)
    -- refunds with no wallet transaction, so the ledger link is required only when coins moved.
    CONSTRAINT ck_marketplace_refund_disputes_refund
        CHECK (
            (status = 'REFUNDED'
                AND refunded_at IS NOT NULL AND refund_coin_amount IS NOT NULL
                AND (refund_coin_amount = 0 OR refund_wallet_transaction_id IS NOT NULL))
            OR (status <> 'REFUNDED'
                AND refunded_at IS NULL AND refund_coin_amount IS NULL
                AND refund_wallet_transaction_id IS NULL)
        )
);

-- At most one active (non-terminal) dispute per sale. Browser retries and concurrent
-- creates collide here rather than opening duplicate disputes.
CREATE UNIQUE INDEX uq_marketplace_refund_disputes_active_sale
    ON marketplace_refund_disputes (sale_id)
    WHERE status IN ('OPEN', 'UNDER_REVIEW', 'APPROVED');

CREATE INDEX idx_marketplace_refund_disputes_queue
    ON marketplace_refund_disputes (status, created_at DESC);

CREATE INDEX idx_marketplace_refund_disputes_buyer
    ON marketplace_refund_disputes (buyer_id, created_at DESC);

CREATE INDEX idx_marketplace_refund_disputes_version
    ON marketplace_refund_disputes (pack_version_id);
