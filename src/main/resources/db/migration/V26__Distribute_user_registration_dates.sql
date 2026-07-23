-- Migration V26: Distribute user registration dates evenly across May 1, 2026 to July 23, 2026

WITH target_users AS (
    SELECT 
        user_id,
        ROW_NUMBER() OVER (ORDER BY user_id) - 1 AS row_num,
        COUNT(*) OVER () AS total_users
    FROM users
),
distributed_dates AS (
    SELECT 
        user_id,
        row_num,
        TIMESTAMP '2026-05-01 08:00:00' + 
        (row_num * (TIMESTAMP '2026-07-23 20:00:00' - TIMESTAMP '2026-05-01 08:00:00') / GREATEST(total_users - 1, 1)) AS new_created_at
    FROM target_users
)
UPDATE users u
SET 
    created_at = d.new_created_at,
    updated_at = d.new_created_at + INTERVAL '1 hour' * (d.row_num % 18 + 1)
FROM distributed_dates d
WHERE u.user_id = d.user_id;

-- Also update existing subscription created_at to match user registration date
UPDATE subscriptions s
SET 
    created_at = u.created_at
FROM users u
WHERE s.user_id = u.user_id;
