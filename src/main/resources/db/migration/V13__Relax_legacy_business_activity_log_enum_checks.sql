-- Marketplace activity types are validated by the Java enums. Some existing
-- production databases also carry legacy CHECK constraints for the old enum
-- lists, which reject newly introduced Marketplace audit events.
DO $$
DECLARE
    legacy_constraint RECORD;
BEGIN
    FOR legacy_constraint IN
        SELECT constraint_row.conname
        FROM pg_constraint constraint_row
        JOIN pg_class table_row ON table_row.oid = constraint_row.conrelid
        JOIN pg_namespace schema_row ON schema_row.oid = table_row.relnamespace
        WHERE schema_row.nspname = current_schema()
          AND table_row.relname = 'business_activity_logs'
          AND constraint_row.contype = 'c'
          AND (
              pg_get_constraintdef(constraint_row.oid) ILIKE '%action_type%'
              OR pg_get_constraintdef(constraint_row.oid) ILIKE '%entity_type%'
          )
    LOOP
        EXECUTE format(
            'ALTER TABLE business_activity_logs DROP CONSTRAINT %I',
            legacy_constraint.conname
        );
    END LOOP;
END $$;
