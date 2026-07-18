CREATE TABLE marketplace_practice_attempts (
    attempt_id UUID PRIMARY KEY,
    buyer_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    pack_version_id UUID NOT NULL REFERENCES marketplace_pack_versions(version_id),
    chapter_sequence_no INTEGER NOT NULL CHECK (chapter_sequence_no > 0),
    status VARCHAR(20) NOT NULL,
    question_snapshot_json JSONB NOT NULL,
    answer_snapshot_json JSONB NOT NULL,
    submitted_answers_json JSONB,
    score INTEGER CHECK (score BETWEEN 0 AND 100),
    correct_count INTEGER CHECK (correct_count >= 0),
    question_count INTEGER NOT NULL CHECK (question_count > 0),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    idempotency_key UUID,
    request_fingerprint VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_marketplace_practice_active_chapter
    ON marketplace_practice_attempts (buyer_id, pack_version_id, chapter_sequence_no)
    WHERE status = 'IN_PROGRESS';

CREATE UNIQUE INDEX uq_marketplace_practice_submit_idempotency
    ON marketplace_practice_attempts (buyer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_marketplace_practice_buyer_version_started
    ON marketplace_practice_attempts (buyer_id, pack_version_id, started_at DESC);

CREATE INDEX idx_marketplace_practice_buyer_version_status
    ON marketplace_practice_attempts (buyer_id, pack_version_id, status);

CREATE TABLE marketplace_version_progress (
    progress_id UUID PRIMARY KEY,
    buyer_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    pack_version_id UUID NOT NULL REFERENCES marketplace_pack_versions(version_id),
    completed_quiz_count INTEGER NOT NULL DEFAULT 0 CHECK (completed_quiz_count >= 0),
    completed_chapter_count INTEGER NOT NULL DEFAULT 0 CHECK (completed_chapter_count >= 0),
    completion_percent NUMERIC(5, 2) NOT NULL DEFAULT 0
        CHECK (completion_percent BETWEEN 0 AND 100),
    first_activity_at TIMESTAMP WITH TIME ZONE,
    last_activity_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_marketplace_version_progress_buyer_version
        UNIQUE (buyer_id, pack_version_id)
);

CREATE INDEX idx_marketplace_version_progress_buyer_activity
    ON marketplace_version_progress (buyer_id, last_activity_at DESC);
