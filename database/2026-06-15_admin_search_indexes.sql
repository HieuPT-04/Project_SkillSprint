CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_users_user_id_lower_trgm
    ON users USING gin (lower(user_id) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_email_lower_trgm
    ON users USING gin (lower(email) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_full_name_lower_trgm
    ON users USING gin (lower(full_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_feedbacks_title_trgm
    ON feedbacks USING gin (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_feedbacks_content_trgm
    ON feedbacks USING gin (content gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_feedbacks_status
    ON feedbacks(status);

CREATE INDEX IF NOT EXISTS idx_feedbacks_type
    ON feedbacks(type);

CREATE INDEX IF NOT EXISTS idx_feedbacks_created_at
    ON feedbacks(created_at DESC);
