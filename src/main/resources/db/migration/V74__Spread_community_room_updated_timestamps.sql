-- Migration V74: Spread out community room created_at and updated_at timestamps evenly across June 2026 to August 2026.

UPDATE community_rooms
SET
    updated_at = CASE
        WHEN name ILIKE '%TOEIC%' THEN TIMESTAMPTZ '2026-06-06 09:15:00+07'
        WHEN name ILIKE '%IT & Computer Science%' OR name ILIKE '%Khoa học máy tính%' THEN TIMESTAMPTZ '2026-06-18 14:30:00+07'
        WHEN name ILIKE '%React%' THEN TIMESTAMPTZ '2026-07-02 11:20:00+07'
        WHEN name ILIKE '%SkillSprint%' OR name ILIKE '%FPT%' THEN TIMESTAMPTZ '2026-07-15 16:45:00+07'
        WHEN name ILIKE '%Java%' THEN TIMESTAMPTZ '2026-07-28 10:10:00+07'
        WHEN name ILIKE '%Pomodoro%' THEN TIMESTAMPTZ '2026-08-03 22:04:00+07'
        ELSE TIMESTAMPTZ '2026-06-15 10:00:00+07'
    END,
    created_at = CASE
        WHEN name ILIKE '%TOEIC%' THEN TIMESTAMPTZ '2026-05-15 08:00:00+07'
        WHEN name ILIKE '%IT & Computer Science%' OR name ILIKE '%Khoa học máy tính%' THEN TIMESTAMPTZ '2026-05-18 10:00:00+07'
        WHEN name ILIKE '%React%' THEN TIMESTAMPTZ '2026-06-01 09:00:00+07'
        WHEN name ILIKE '%SkillSprint%' OR name ILIKE '%FPT%' THEN TIMESTAMPTZ '2026-06-10 14:00:00+07'
        WHEN name ILIKE '%Java%' THEN TIMESTAMPTZ '2026-06-20 11:30:00+07'
        WHEN name ILIKE '%Pomodoro%' THEN TIMESTAMPTZ '2026-07-01 08:00:00+07'
        ELSE TIMESTAMPTZ '2026-05-01 10:00:00+07'
    END;
