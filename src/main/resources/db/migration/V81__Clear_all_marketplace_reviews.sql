-- V81: Clear all seeded marketplace reviews as requested by product team feedback.
-- Removes dummy reviews that were disconnected from leaderboard users,
-- ensuring marketplace packages display a clean state ("Chưa có đánh giá").

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'marketplace_reviews'
    ) THEN
        DELETE FROM marketplace_reviews;
    END IF;
END $$;

