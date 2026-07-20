-- Marketplace Plan 6D remediation: allow reporting a Marketplace Review.
--
-- V20 restricted target_type to VERSION/CHAPTER/QUESTION/CREATOR. Widen the check
-- constraint to also accept REVIEW. Additive and non-destructive; existing rows are
-- unaffected.

ALTER TABLE marketplace_content_reports
    DROP CONSTRAINT ck_marketplace_content_reports_target_type;

ALTER TABLE marketplace_content_reports
    ADD CONSTRAINT ck_marketplace_content_reports_target_type
    CHECK (target_type IN ('VERSION', 'CHAPTER', 'QUESTION', 'CREATOR', 'REVIEW'));
