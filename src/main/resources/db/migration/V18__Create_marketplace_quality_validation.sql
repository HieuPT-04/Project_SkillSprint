CREATE TABLE marketplace_quality_jobs (
    job_id UUID PRIMARY KEY,
    pack_version_id UUID NOT NULL REFERENCES marketplace_pack_versions(version_id),
    requested_by VARCHAR(100) NOT NULL REFERENCES users(user_id),
    status VARCHAR(20) NOT NULL,
    snapshot_fingerprint CHAR(64) NOT NULL,
    score INTEGER CHECK (score BETWEEN 0 AND 100),
    report_json JSONB,
    error_code VARCHAR(100),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    max_retries INTEGER NOT NULL DEFAULT 2 CHECK (max_retries >= 0),
    next_retry_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_marketplace_quality_job_active
    ON marketplace_quality_jobs (pack_version_id)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX idx_marketplace_quality_job_next
    ON marketplace_quality_jobs (status, next_retry_at, created_at);

CREATE INDEX idx_marketplace_quality_job_version_latest
    ON marketplace_quality_jobs (pack_version_id, created_at DESC);

ALTER TABLE marketplace_pack_versions
    ADD COLUMN quality_status VARCHAR(20),
    ADD COLUMN quality_score INTEGER CHECK (quality_score BETWEEN 0 AND 100),
    ADD COLUMN quality_snapshot_fingerprint CHAR(64),
    ADD COLUMN quality_validated_at TIMESTAMP WITH TIME ZONE;
