-- Marketplace Plan 1: versioned pack foundation.
--
-- Adds the stable Pack identity and the independently reviewable/purchasable
-- Pack Version aggregate, then migrates every legacy marketplace_items row into
-- one Pack + Version 1. No legacy table, column, or row is dropped or rewritten:
-- marketplace_items and marketplace_quiz_pack_snapshots remain the write path for
-- this phase, and legacy_item_id keeps the compatibility mapping in both
-- directions.

CREATE TABLE marketplace_packs (
    pack_id UUID PRIMARY KEY,
    creator_id VARCHAR(100) NOT NULL REFERENCES users(user_id),
    source_workspace_id UUID NOT NULL REFERENCES study_workspaces(workspace_id),
    -- Provenance of a migrated pack. NULL for packs created after the migration.
    legacy_item_id UUID UNIQUE REFERENCES marketplace_items(item_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_marketplace_packs_creator ON marketplace_packs (creator_id);

CREATE TABLE marketplace_pack_versions (
    version_id UUID PRIMARY KEY,
    pack_id UUID NOT NULL REFERENCES marketplace_packs(pack_id),
    version_no INTEGER NOT NULL CHECK (version_no >= 1),
    status VARCHAR(30) NOT NULL,
    update_type VARCHAR(10) NOT NULL,
    -- Compatibility mapping: the legacy item this version was migrated from.
    legacy_item_id UUID UNIQUE REFERENCES marketplace_items(item_id),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    subject VARCHAR(100) NOT NULL,
    price_coins INTEGER NOT NULL CHECK (price_coins >= 0),
    creator_validation_score INTEGER,
    reviewed_by VARCHAR(100) REFERENCES users(user_id),
    review_note TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    chapter_count INTEGER NOT NULL CHECK (chapter_count >= 1),
    quiz_count INTEGER NOT NULL CHECK (quiz_count >= 1),
    question_count INTEGER NOT NULL CHECK (question_count >= 1),
    content_json JSONB NOT NULL,
    -- Saleability marker enforcing "at most one current selling version per pack".
    saleable BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    superseded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_marketplace_pack_version_no UNIQUE (pack_id, version_no)
);

-- At most one saleable version per pack. The service layer validates the same
-- rule inside the publish transaction to return a typed domain error; this index
-- is the authoritative guard against a concurrent publish.
CREATE UNIQUE INDEX uq_marketplace_pack_single_saleable_version
    ON marketplace_pack_versions (pack_id)
    WHERE saleable;

CREATE INDEX idx_marketplace_pack_versions_status_published_at
    ON marketplace_pack_versions (status, published_at DESC);

-- Nullable version foreign keys on the historical tables.
--
-- They stay nullable in this phase on purpose. NOT NULL here would fail every insert
-- from an instance still running the previous release during a rolling deploy, and a
-- legacy item without a snapshot yields no Version 1 for its history to point at. A
-- follow-up migration may SET NOT NULL once every deployed instance populates the
-- column and each of these returns 0:
--   SELECT count(*) FROM marketplace_purchases          WHERE pack_version_id IS NULL;
--   SELECT count(*) FROM marketplace_quiz_attempts      WHERE pack_version_id IS NULL;
--   SELECT count(*) FROM marketplace_reviews            WHERE pack_version_id IS NULL;
--   SELECT count(*) FROM marketplace_challenge_sessions WHERE pack_version_id IS NULL;
ALTER TABLE marketplace_purchases
    ADD COLUMN pack_version_id UUID REFERENCES marketplace_pack_versions(version_id);
ALTER TABLE marketplace_quiz_attempts
    ADD COLUMN pack_version_id UUID REFERENCES marketplace_pack_versions(version_id);
ALTER TABLE marketplace_reviews
    ADD COLUMN pack_version_id UUID REFERENCES marketplace_pack_versions(version_id);
ALTER TABLE marketplace_challenge_sessions
    ADD COLUMN pack_version_id UUID REFERENCES marketplace_pack_versions(version_id);

CREATE INDEX idx_marketplace_purchases_pack_version ON marketplace_purchases (pack_version_id);
CREATE INDEX idx_marketplace_quiz_attempts_pack_version ON marketplace_quiz_attempts (pack_version_id);
CREATE INDEX idx_marketplace_reviews_pack_version ON marketplace_reviews (pack_version_id);
CREATE INDEX idx_marketplace_challenge_sessions_pack_version ON marketplace_challenge_sessions (pack_version_id);

-- One Pack per legacy item. ON CONFLICT keeps the statement re-runnable.
INSERT INTO marketplace_packs (pack_id, creator_id, source_workspace_id, legacy_item_id, created_at, updated_at)
SELECT gen_random_uuid(), i.creator_id, i.source_workspace_id, i.item_id, i.created_at, i.updated_at
FROM marketplace_items i
ON CONFLICT (legacy_item_id) DO NOTHING;

-- Version 1 per legacy item, copying the marketplace metadata and the immutable
-- snapshot content. Only a legacy PUBLISHED item yields a saleable version.
-- The inner join on the snapshot means an item without a snapshot produces no
-- version; its historical rows keep a NULL pack_version_id rather than being
-- reassigned to another version.
INSERT INTO marketplace_pack_versions (
    version_id, pack_id, version_no, status, update_type, legacy_item_id,
    title, description, subject, price_coins,
    creator_validation_score, reviewed_by, review_note, reviewed_at,
    chapter_count, quiz_count, question_count, content_json,
    saleable, published_at, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    p.pack_id,
    1,
    i.status,
    'MAJOR',
    i.item_id,
    i.title,
    i.description,
    i.subject,
    i.price_coins,
    i.creator_validation_score,
    i.reviewed_by,
    i.review_note,
    i.reviewed_at,
    s.chapter_count,
    s.quiz_count,
    s.question_count,
    s.content_json,
    i.status = 'PUBLISHED',
    i.published_at,
    i.created_at,
    i.updated_at
FROM marketplace_items i
JOIN marketplace_packs p ON p.legacy_item_id = i.item_id
JOIN marketplace_quiz_pack_snapshots s ON s.item_id = i.item_id
ON CONFLICT (legacy_item_id) DO NOTHING;

-- Backfill the historical tables to the Version 1 of their own legacy item.
UPDATE marketplace_purchases h
SET pack_version_id = v.version_id
FROM marketplace_pack_versions v
WHERE v.legacy_item_id = h.item_id AND h.pack_version_id IS NULL;

UPDATE marketplace_quiz_attempts h
SET pack_version_id = v.version_id
FROM marketplace_pack_versions v
WHERE v.legacy_item_id = h.item_id AND h.pack_version_id IS NULL;

UPDATE marketplace_reviews h
SET pack_version_id = v.version_id
FROM marketplace_pack_versions v
WHERE v.legacy_item_id = h.item_id AND h.pack_version_id IS NULL;

UPDATE marketplace_challenge_sessions h
SET pack_version_id = v.version_id
FROM marketplace_pack_versions v
WHERE v.legacy_item_id = h.item_id AND h.pack_version_id IS NULL;
