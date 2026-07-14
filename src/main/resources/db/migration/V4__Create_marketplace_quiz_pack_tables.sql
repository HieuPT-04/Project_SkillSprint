CREATE TABLE marketplace_items (
    item_id UUID PRIMARY KEY,
    creator_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    source_workspace_id UUID NOT NULL REFERENCES study_workspaces(workspace_id),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    subject VARCHAR(100) NOT NULL,
    price_coins INTEGER NOT NULL CHECK (price_coins >= 0),
    status VARCHAR(30) NOT NULL,
    creator_validation_score INTEGER,
    reviewed_by VARCHAR(100) REFERENCES users(user_id),
    review_note TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE marketplace_quiz_pack_snapshots (
    snapshot_id UUID PRIMARY KEY,
    item_id UUID NOT NULL UNIQUE REFERENCES marketplace_items(item_id),
    chapter_count INTEGER NOT NULL CHECK (chapter_count >= 1),
    quiz_count INTEGER NOT NULL CHECK (quiz_count >= 1),
    question_count INTEGER NOT NULL CHECK (question_count >= 1),
    content_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE marketplace_purchases (
    purchase_id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    item_id UUID NOT NULL REFERENCES marketplace_items(item_id),
    price_coins INTEGER NOT NULL CHECK (price_coins >= 0),
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    purchased_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_marketplace_purchase_user_item UNIQUE (user_id, item_id)
);

CREATE TABLE marketplace_quiz_attempts (
    attempt_id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES marketplace_items(item_id),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    attempt_type VARCHAR(30) NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    correct_count INTEGER NOT NULL CHECK (correct_count >= 0),
    question_count INTEGER NOT NULL CHECK (question_count >= 1),
    duration_seconds BIGINT NOT NULL CHECK (duration_seconds >= 0),
    suspicious BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_marketplace_ranked_attempts
    ON marketplace_quiz_attempts (item_id, attempt_type, suspicious, score DESC, duration_seconds ASC, completed_at ASC);

CREATE TABLE marketplace_reviews (
    review_id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES marketplace_items(item_id),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_marketplace_review_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_marketplace_items_status_published_at
    ON marketplace_items (status, published_at DESC);
