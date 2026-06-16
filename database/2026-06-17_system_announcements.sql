CREATE TABLE IF NOT EXISTS system_announcements (
    announcement_id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    title VARCHAR(255),
    message TEXT,
    type VARCHAR(30) NOT NULL DEFAULT 'INFO',
    start_at TIMESTAMP WITH TIME ZONE,
    end_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_system_announcements_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_system_announcements_updated_at
    ON system_announcements(updated_at DESC);
