-- Marketplace Plan 6D: buyer-submitted content and violation reports.
--
-- Reports are scoped to an immutable pack version snapshot. They never mutate the
-- reported content; moderation is an explicit admin action recorded on the row.

CREATE TABLE marketplace_content_reports (
    report_id UUID PRIMARY KEY,
    reporter_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    pack_version_id UUID NOT NULL REFERENCES marketplace_pack_versions(version_id),
    target_type VARCHAR(20) NOT NULL,
    target_ref VARCHAR(200),
    category VARCHAR(30) NOT NULL,
    description TEXT,
    evidence_object_key VARCHAR(512),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reviewed_by VARCHAR(100) REFERENCES users(user_id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    resolution_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_marketplace_content_reports_target_type
        CHECK (target_type IN ('VERSION', 'CHAPTER', 'QUESTION', 'CREATOR')),
    CONSTRAINT ck_marketplace_content_reports_category
        CHECK (category IN (
            'INCORRECT_ANSWER', 'AMBIGUOUS', 'BROKEN', 'DUPLICATE',
            'MISLEADING', 'COPYRIGHT', 'INAPPROPRIATE', 'OTHER'
        )),
    CONSTRAINT ck_marketplace_content_reports_status
        CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_marketplace_content_reports_review
        CHECK (
            (status IN ('OPEN', 'IN_REVIEW') AND reviewed_at IS NULL AND reviewed_by IS NULL)
            OR (status IN ('RESOLVED', 'DISMISSED') AND reviewed_at IS NOT NULL)
        )
);

-- A reporter may hold at most one OPEN report for the same version/target/category.
-- COALESCE keeps VERSION/CREATOR rows (null target_ref) distinct and de-duplicated.
CREATE UNIQUE INDEX uq_marketplace_content_reports_open
    ON marketplace_content_reports (reporter_id, pack_version_id, target_type, COALESCE(target_ref, ''), category)
    WHERE status = 'OPEN';

CREATE INDEX idx_marketplace_content_reports_queue
    ON marketplace_content_reports (status, created_at DESC);

CREATE INDEX idx_marketplace_content_reports_version
    ON marketplace_content_reports (pack_version_id);

CREATE INDEX idx_marketplace_content_reports_reporter
    ON marketplace_content_reports (reporter_id, created_at DESC);
