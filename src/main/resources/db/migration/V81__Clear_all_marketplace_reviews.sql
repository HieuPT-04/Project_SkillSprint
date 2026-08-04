-- V81: Clear all seeded marketplace reviews as requested by product team feedback.
-- Removes dummy reviews that were disconnected from leaderboard users,
-- ensuring marketplace packages display a clean state ("Chưa có đánh giá").

DELETE FROM marketplace_reviews;
