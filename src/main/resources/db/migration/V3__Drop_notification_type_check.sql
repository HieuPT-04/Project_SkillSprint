DO $$ 
DECLARE 
    constraint_name text;
BEGIN 
    SELECT conname INTO constraint_name 
    FROM pg_constraint 
    WHERE conrelid = 'notifications'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%type%';
    
    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE notifications DROP CONSTRAINT ' || constraint_name;
    END IF;
END $$;
