-- Marketplace Plan 6D remediation: block duplicate reports while a moderator is reviewing.
--
-- V20 enforced at most one OPEN report per reporter/version/target/category. A reporter
-- could still file an identical report once the first moved to IN_REVIEW. Before widening
-- the partial unique index, retain the oldest active report and dismiss any later duplicate.
-- This preserves every audit row and makes this migration safe for databases that already
-- contain the now-invalid active duplicates.

WITH ranked_active_reports AS (
    SELECT
        report_id,
        ROW_NUMBER() OVER (
            PARTITION BY reporter_id, pack_version_id, target_type, COALESCE(target_ref, ''), category
            ORDER BY created_at ASC, report_id ASC
        ) AS duplicate_rank
    FROM marketplace_content_reports
    WHERE status IN ('OPEN', 'IN_REVIEW')
)
UPDATE marketplace_content_reports AS report
SET
    status = 'DISMISSED',
    reviewed_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP,
    resolution_note = COALESCE(
        NULLIF(report.resolution_note, ''),
        'Duplicate active report consolidated by migration.'
    )
FROM ranked_active_reports AS ranked
WHERE report.report_id = ranked.report_id
  AND ranked.duplicate_rank > 1;

DROP INDEX IF EXISTS uq_marketplace_content_reports_open;

CREATE UNIQUE INDEX uq_marketplace_content_reports_active
    ON marketplace_content_reports (reporter_id, pack_version_id, target_type, COALESCE(target_ref, ''), category)
    WHERE status IN ('OPEN', 'IN_REVIEW');
