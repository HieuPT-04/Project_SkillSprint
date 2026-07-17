CREATE TABLE creator_payout_allocations (
    allocation_id UUID PRIMARY KEY,
    payout_id UUID NOT NULL REFERENCES creator_payouts(payout_id),
    earning_entry_id UUID NOT NULL REFERENCES creator_earning_entries(earning_entry_id),
    amount INTEGER NOT NULL CHECK (amount > 0),
    state VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_creator_payout_allocation_entry UNIQUE (payout_id, earning_entry_id),
    CONSTRAINT ck_creator_payout_allocations_state CHECK (state IN ('RESERVED', 'PAID', 'RELEASED'))
);

CREATE INDEX idx_creator_payout_allocations_earning_state
    ON creator_payout_allocations (earning_entry_id, state);
CREATE INDEX idx_creator_payout_allocations_payout
    ON creator_payout_allocations (payout_id);
