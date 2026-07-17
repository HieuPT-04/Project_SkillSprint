CREATE TABLE marketplace_ranked_attempts (
    attempt_id UUID PRIMARY KEY,
    buyer_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    pack_version_id UUID NOT NULL REFERENCES marketplace_pack_versions(version_id),
    definition_id UUID NOT NULL REFERENCES marketplace_ranked_quiz_definitions(definition_id),
    attempt_date DATE NOT NULL,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    question_snapshot_json JSONB NOT NULL,
    answer_snapshot_json JSONB NOT NULL,
    score INTEGER CHECK (score BETWEEN 0 AND 100),
    correct_count INTEGER CHECK (correct_count >= 0),
    duration_seconds BIGINT CHECK (duration_seconds >= 0),
    suspicious BOOLEAN NOT NULL DEFAULT FALSE,
    leaderboard_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_marketplace_ranked_attempt_sequence
        UNIQUE (buyer_id, pack_version_id, attempt_date, attempt_number)
);

CREATE INDEX idx_marketplace_ranked_attempt_buyer_version_date
    ON marketplace_ranked_attempts (buyer_id, pack_version_id, attempt_date);

CREATE INDEX idx_marketplace_ranked_attempt_buyer_version_status
    ON marketplace_ranked_attempts (buyer_id, pack_version_id, status);

CREATE INDEX idx_marketplace_ranked_attempt_leaderboard
    ON marketplace_ranked_attempts (pack_version_id, leaderboard_eligible, score DESC, duration_seconds ASC);
