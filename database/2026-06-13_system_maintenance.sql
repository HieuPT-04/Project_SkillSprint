CREATE TABLE IF NOT EXISTS system_maintenance (
    maintenance_id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    message TEXT,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    updated_by VARCHAR(100) REFERENCES users(user_id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_system_maintenance_updated_at
    ON system_maintenance(updated_at DESC);
