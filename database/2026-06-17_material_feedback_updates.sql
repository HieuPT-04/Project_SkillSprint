ALTER TABLE feedbacks
    ADD COLUMN IF NOT EXISTS admin_reply TEXT,
    ADD COLUMN IF NOT EXISTS replied_by_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS replied_at TIMESTAMP WITH TIME ZONE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_feedbacks_replied_by_user'
    ) THEN
        ALTER TABLE feedbacks
            ADD CONSTRAINT fk_feedbacks_replied_by_user
            FOREIGN KEY (replied_by_user_id)
            REFERENCES users(user_id);
    END IF;
END $$;

UPDATE feedbacks
SET status = 'CLOSED'
WHERE status IN ('DONE', 'RESOLVED');

ALTER TABLE feedbacks ADD COLUMN image_object_key VARCHAR(512);
