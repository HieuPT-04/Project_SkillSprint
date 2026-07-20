-- Marketplace Plan 6C: make Pack Version the canonical review scope.
--
-- V7 backfilled the rows that existed at deployment time. Repeat the same safe
-- update for rows written through compatibility routes since V7, then preserve
-- any remaining unmapped row as a legacy item review.

UPDATE marketplace_reviews review
SET pack_version_id = version.version_id
FROM marketplace_pack_versions version
WHERE review.pack_version_id IS NULL
  AND review.item_id = version.legacy_item_id;

ALTER TABLE marketplace_reviews
    DROP CONSTRAINT IF EXISTS uq_marketplace_review_user_item;

-- Native versions do not need a synthetic marketplace_items row. Legacy rows
-- may still use item_id, but every review must retain at least one valid anchor.
ALTER TABLE marketplace_reviews
    ALTER COLUMN item_id DROP NOT NULL;

ALTER TABLE marketplace_reviews
    ADD CONSTRAINT ck_marketplace_reviews_scope
    CHECK (pack_version_id IS NOT NULL OR item_id IS NOT NULL);

CREATE UNIQUE INDEX uq_marketplace_reviews_user_version
    ON marketplace_reviews (user_id, pack_version_id)
    WHERE pack_version_id IS NOT NULL;

CREATE UNIQUE INDEX uq_marketplace_reviews_user_legacy_item
    ON marketplace_reviews (user_id, item_id)
    WHERE pack_version_id IS NULL;

CREATE INDEX idx_marketplace_reviews_version_updated
    ON marketplace_reviews (pack_version_id, updated_at DESC)
    WHERE pack_version_id IS NOT NULL;
