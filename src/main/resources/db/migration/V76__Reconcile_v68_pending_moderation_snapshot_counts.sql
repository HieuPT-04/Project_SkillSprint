-- V68 copied one shared two-chapter payload into every pending moderation
-- snapshot while retaining the source version's aggregate counts. Reconcile
-- only those deterministic snapshots so the summary and review detail agree.

CREATE FUNCTION v76_v68_uuid(seed TEXT) RETURNS UUID LANGUAGE SQL IMMUTABLE AS $$
    SELECT (substr(md5('skillsprint-v68:' || seed), 1, 8) || '-' ||
            substr(md5('skillsprint-v68:' || seed), 9, 4) || '-' ||
            substr(md5('skillsprint-v68:' || seed), 13, 4) || '-' ||
            substr(md5('skillsprint-v68:' || seed), 17, 4) || '-' ||
            substr(md5('skillsprint-v68:' || seed), 21, 12))::uuid;
$$;

WITH v68_snapshots AS (
    SELECT snapshot.snapshot_id,
           jsonb_array_length(snapshot.content_json -> 'chapters') AS chapter_count,
           COALESCE((
               SELECT SUM(jsonb_array_length(chapter.value -> 'quizzes'))
               FROM jsonb_array_elements(snapshot.content_json -> 'chapters') chapter
               WHERE jsonb_typeof(chapter.value -> 'quizzes') = 'array'
           ), 0)::INTEGER AS quiz_count,
           COALESCE((
               SELECT SUM(jsonb_array_length(quiz.value -> 'questions'))
               FROM jsonb_array_elements(snapshot.content_json -> 'chapters') chapter
               CROSS JOIN LATERAL jsonb_array_elements(
                   CASE WHEN jsonb_typeof(chapter.value -> 'quizzes') = 'array'
                       THEN chapter.value -> 'quizzes'
                       ELSE '[]'::jsonb
                   END
               ) quiz
               WHERE jsonb_typeof(quiz.value -> 'questions') = 'array'
           ), 0)::INTEGER AS question_count
    FROM marketplace_quiz_pack_snapshots snapshot
    JOIN marketplace_pack_versions version ON version.legacy_item_id = snapshot.item_id
    WHERE snapshot.snapshot_id = v76_v68_uuid('snapshot:pending:' || version.version_id::text)
      AND jsonb_typeof(snapshot.content_json -> 'chapters') = 'array'
)
UPDATE marketplace_quiz_pack_snapshots snapshot
SET chapter_count = affected.chapter_count,
    quiz_count = affected.quiz_count,
    question_count = affected.question_count,
    updated_at = CURRENT_TIMESTAMP
FROM v68_snapshots affected
WHERE snapshot.snapshot_id = affected.snapshot_id
  AND (snapshot.chapter_count, snapshot.quiz_count, snapshot.question_count)
      IS DISTINCT FROM (affected.chapter_count, affected.quiz_count, affected.question_count);

UPDATE marketplace_pack_versions version
SET chapter_count = snapshot.chapter_count,
    quiz_count = snapshot.quiz_count,
    question_count = snapshot.question_count,
    updated_at = CURRENT_TIMESTAMP
FROM marketplace_quiz_pack_snapshots snapshot
WHERE version.legacy_item_id = snapshot.item_id
  AND snapshot.snapshot_id = v76_v68_uuid('snapshot:pending:' || version.version_id::text)
  AND jsonb_typeof(snapshot.content_json -> 'chapters') = 'array'
  AND (version.chapter_count, version.quiz_count, version.question_count)
      IS DISTINCT FROM (snapshot.chapter_count, snapshot.quiz_count, snapshot.question_count);

DROP FUNCTION v76_v68_uuid(TEXT);
