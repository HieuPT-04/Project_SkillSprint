-- Adds admin-customizable badge styling columns to service_plans.
-- The ADMIN_DEFAULT enum value is stored via EnumType.STRING in the existing
-- plan_type VARCHAR(20) column, so no DB type change is required for the enum.
-- In dev (ddl-auto: update) Hibernate adds these automatically; run this in prod.

ALTER TABLE service_plans
    ADD COLUMN IF NOT EXISTS badge_color    TEXT,
    ADD COLUMN IF NOT EXISTS badge_icon     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS animation_type VARCHAR(20);
