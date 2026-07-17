ALTER TABLE marketplace_ranked_attempts
    ADD COLUMN submitted_answers_json JSONB,
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN request_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX uq_marketplace_ranked_first_eligible_attempt
    ON marketplace_ranked_attempts (buyer_id, pack_version_id)
    WHERE leaderboard_eligible = TRUE;
