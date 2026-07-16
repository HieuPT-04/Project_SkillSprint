-- Manual Coin adjustments must retain both the actor and their stated reason.
-- Existing non-admin ledger rows intentionally keep these nullable.
ALTER TABLE wallet_transactions
    ADD COLUMN adjusted_by_user_id VARCHAR(100) REFERENCES users(user_id);

ALTER TABLE wallet_transactions
    ADD COLUMN adjustment_reason VARCHAR(500);

CREATE INDEX idx_wallet_transactions_adjusted_by_user_id
    ON wallet_transactions (adjusted_by_user_id)
    WHERE adjusted_by_user_id IS NOT NULL;
