-- The workspace create API accepts descriptions up to 1,000 characters. Older
-- production databases may still have Hibernate's legacy VARCHAR(255) column.
-- Keep the persisted column aligned with the API contract.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'study_workspaces'
          AND column_name = 'description'
    ) THEN
        ALTER TABLE study_workspaces
            ALTER COLUMN description TYPE TEXT;
    END IF;
END $$;
