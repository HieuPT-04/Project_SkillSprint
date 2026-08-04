-- Keep the persisted counters aligned with the immutable content displayed to
-- moderators. This also repairs the V69 pending-review seed payloads.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'marketplace_quiz_pack_snapshots')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'marketplace_pack_versions') THEN
        WITH snapshot_counts AS (
            SELECT snapshot.snapshot_id,
                   jsonb_array_length(snapshot.content_json -> 'chapters') AS chapter_count,
                   quiz_counts.quiz_count,
                   quiz_counts.question_count
            FROM marketplace_quiz_pack_snapshots snapshot
            CROSS JOIN LATERAL (
                SELECT COUNT(*)::INTEGER AS quiz_count,
                       COALESCE(SUM(
                           CASE WHEN jsonb_typeof(quiz.value -> 'questions') = 'array'
                               THEN jsonb_array_length(quiz.value -> 'questions')
                               ELSE 0
                           END
                       ), 0)::INTEGER AS question_count
                FROM jsonb_array_elements(
                    CASE WHEN jsonb_typeof(snapshot.content_json -> 'chapters') = 'array'
                        THEN snapshot.content_json -> 'chapters'
                        ELSE '[]'::jsonb
                    END
                ) chapter
                CROSS JOIN LATERAL jsonb_array_elements(
                    CASE
                        WHEN jsonb_typeof(chapter.value -> 'quizzes') = 'array' THEN chapter.value -> 'quizzes'
                        WHEN jsonb_typeof(chapter.value -> 'quiz') = 'object' THEN jsonb_build_array(chapter.value -> 'quiz')
                        ELSE '[]'::jsonb
                    END
                ) quiz
            ) quiz_counts
            WHERE jsonb_typeof(snapshot.content_json -> 'chapters') = 'array'
        )
        UPDATE marketplace_quiz_pack_snapshots snapshot
        SET chapter_count = counts.chapter_count,
            quiz_count = counts.quiz_count,
            question_count = counts.question_count,
            updated_at = CURRENT_TIMESTAMP
        FROM snapshot_counts counts
        WHERE snapshot.snapshot_id = counts.snapshot_id
          AND (snapshot.chapter_count, snapshot.quiz_count, snapshot.question_count)
              IS DISTINCT FROM (counts.chapter_count, counts.quiz_count, counts.question_count);

        UPDATE marketplace_pack_versions version
        SET chapter_count = snapshot.chapter_count,
            quiz_count = snapshot.quiz_count,
            question_count = snapshot.question_count,
            updated_at = CURRENT_TIMESTAMP
        FROM marketplace_quiz_pack_snapshots snapshot
        WHERE version.legacy_item_id = snapshot.item_id
          AND (version.chapter_count, version.quiz_count, version.question_count)
              IS DISTINCT FROM (snapshot.chapter_count, snapshot.quiz_count, snapshot.question_count);
    END IF;
END $$;
