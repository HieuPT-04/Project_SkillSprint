-- ============================================================
-- SkillSprint Core MVP Schema — PostgreSQL DDL — 2 Roles Edition
-- Compatible with PostgreSQL 13+
-- Notes:
--   - users.user_id is AWS Cognito sub
--   - File originals stored in AWS S3; DB stores metadata/object keys only
--   - Requires uuid-ossp extension for uuid_generate_v4()
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

SET search_path TO public;

-- ============================================================
-- CORE IDENTITY / RBAC
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
  user_id         VARCHAR(100) PRIMARY KEY,
  email           VARCHAR(255) UNIQUE NOT NULL,
  full_name       VARCHAR(255) NOT NULL,
  avatar_url      TEXT,
  timezone        VARCHAR(50) DEFAULT 'Asia/Ho_Chi_Minh',
  status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  last_login_at   TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE IF NOT EXISTS roles (
  role_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  role_name    VARCHAR(50) UNIQUE NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  description  TEXT,
  is_active    BOOLEAN NOT NULL DEFAULT true,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_roles_role_name CHECK (role_name IN ('LEARNER', 'ADMIN'))
);

CREATE INDEX idx_roles_role_name ON roles(role_name);

CREATE TABLE IF NOT EXISTS permissions (
  permission_id   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  permission_name VARCHAR(100) UNIQUE NOT NULL,
  resource        VARCHAR(100) NOT NULL,
  action          VARCHAR(50) NOT NULL,
  description     TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_permissions_resource_action UNIQUE (resource, action)
);

CREATE TABLE IF NOT EXISTS role_permissions (
  rp_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  role_id       UUID NOT NULL,
  permission_id UUID NOT NULL,
  granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_role_permissions UNIQUE (role_id, permission_id),
  CONSTRAINT fk_role_permissions_role_id FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
  CONSTRAINT fk_role_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- ============================================================
-- WORKSPACE / ONBOARDING
-- ============================================================

CREATE TABLE IF NOT EXISTS study_workspaces (
  workspace_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id      VARCHAR(100) NOT NULL,
  name         VARCHAR(255) NOT NULL,
  description  TEXT,
  status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_study_workspaces_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
  CONSTRAINT fk_study_workspaces_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_study_workspaces_user_id ON study_workspaces(user_id);
CREATE INDEX idx_study_workspaces_status ON study_workspaces(status);

CREATE TABLE IF NOT EXISTS user_roles (
  user_role_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id      VARCHAR(100) NOT NULL,
  role_id      UUID NOT NULL,
  workspace_id UUID,
  granted_by   VARCHAR(100),
  granted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at   TIMESTAMPTZ,
  CONSTRAINT uq_user_roles UNIQUE (user_id, role_id, workspace_id),
  CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  CONSTRAINT fk_user_roles_role_id FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
  CONSTRAINT fk_user_roles_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_user_roles_granted_by FOREIGN KEY (granted_by) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_user_roles_workspace_id ON user_roles(workspace_id);

CREATE TABLE IF NOT EXISTS onboarding_profiles (
  profile_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id         UUID UNIQUE NOT NULL,
  target_goal          TEXT NOT NULL,
  study_hours_per_week NUMERIC(4,1),
  target_deadline      DATE,
  confidence           VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
  preferred_language   VARCHAR(20) DEFAULT 'vi',
  preferred_days       JSONB,
  preferred_time_slots JSONB,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_onboarding_confidence CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH')),
  CONSTRAINT chk_onboarding_preferred_language CHECK (preferred_language IN ('vi', 'en')),
  CONSTRAINT fk_onboarding_profiles_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_onboarding_profiles_workspace_id ON onboarding_profiles(workspace_id);

-- ============================================================
-- MATERIAL UPLOAD / PROCESSING
-- ============================================================

CREATE TABLE IF NOT EXISTS uploaded_materials (
  material_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id      UUID NOT NULL,
  user_id           VARCHAR(100) NOT NULL,
  original_file_name VARCHAR(500) NOT NULL,
  file_name         VARCHAR(500) NOT NULL,
  file_type         VARCHAR(50) NOT NULL,
  file_size_bytes   BIGINT,
  s3_bucket         VARCHAR(255),
  s3_object_key     TEXT,
  s3_url            TEXT,
  upload_status     VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
  processing_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  error_message     TEXT,
  uploaded_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_uploaded_materials_file_type CHECK (file_type IN ('PDF', 'DOCX', 'PPTX', 'TXT', 'ZIP')),
  CONSTRAINT chk_uploaded_materials_upload_status CHECK (upload_status IN ('UPLOADED', 'FAILED')),
  CONSTRAINT chk_uploaded_materials_processing_status CHECK (
    processing_status IN ('PENDING', 'EXTRACTING', 'EXTRACTED', 'CLEANING', 'CHUNKING', 'ANALYZING', 'REVIEW_REQUIRED', 'COMPLETED', 'FAILED')
  ),
  CONSTRAINT fk_uploaded_materials_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_uploaded_materials_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_uploaded_materials_workspace_id ON uploaded_materials(workspace_id);
CREATE INDEX idx_uploaded_materials_user_id ON uploaded_materials(user_id);
CREATE INDEX idx_uploaded_materials_processing_status ON uploaded_materials(processing_status);
CREATE INDEX idx_uploaded_materials_file_type ON uploaded_materials(file_type);

CREATE TABLE IF NOT EXISTS extracted_documents (
  document_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  material_id       UUID NOT NULL,
  workspace_id      UUID NOT NULL,
  extracted_text    TEXT,
  cleaned_text      TEXT,
  text_length       INTEGER,
  page_count        INTEGER,
  language          VARCHAR(20),
  extraction_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  extraction_error  TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_extracted_documents_status CHECK (extraction_status IN ('PENDING', 'EXTRACTED', 'CLEANED', 'FAILED')),
  CONSTRAINT fk_extracted_documents_material_id FOREIGN KEY (material_id) REFERENCES uploaded_materials(material_id) ON DELETE CASCADE,
  CONSTRAINT fk_extracted_documents_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_extracted_documents_material_id ON extracted_documents(material_id);
CREATE INDEX idx_extracted_documents_workspace_id ON extracted_documents(workspace_id);

CREATE TABLE IF NOT EXISTS material_chunks (
  chunk_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  material_id    UUID NOT NULL,
  document_id    UUID NOT NULL,
  workspace_id   UUID NOT NULL,
  chunk_index    INTEGER NOT NULL,
  content        TEXT NOT NULL,
  summary        TEXT,
  token_count    INTEGER,
  section_title  VARCHAR(500),
  source_info    JSONB,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_material_chunks_material_index UNIQUE (material_id, chunk_index),
  CONSTRAINT fk_material_chunks_material_id FOREIGN KEY (material_id) REFERENCES uploaded_materials(material_id) ON DELETE CASCADE,
  CONSTRAINT fk_material_chunks_document_id FOREIGN KEY (document_id) REFERENCES extracted_documents(document_id) ON DELETE CASCADE,
  CONSTRAINT fk_material_chunks_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_material_chunks_material_id ON material_chunks(material_id);
CREATE INDEX idx_material_chunks_document_id ON material_chunks(document_id);
CREATE INDEX idx_material_chunks_workspace_id ON material_chunks(workspace_id);

CREATE TABLE IF NOT EXISTS material_processing_jobs (
  job_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  material_id      UUID NOT NULL,
  workspace_id     UUID NOT NULL,
  user_id          VARCHAR(100) NOT NULL,
  status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  current_step     VARCHAR(50),
  progress_percent INTEGER NOT NULL DEFAULT 0,
  error_code       VARCHAR(100),
  error_message    TEXT,
  retryable        BOOLEAN NOT NULL DEFAULT false,
  started_at       TIMESTAMPTZ,
  finished_at      TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_material_processing_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'REVIEW_REQUIRED', 'COMPLETED', 'FAILED')),
  CONSTRAINT chk_material_processing_jobs_current_step CHECK (
    current_step IS NULL OR current_step IN ('EXTRACTING', 'CLEANING', 'CHUNKING', 'AI_CONTENT_ANALYSIS', 'SAVING_RESULT', 'WAITING_FOR_USER_REVIEW')
  ),
  CONSTRAINT chk_material_processing_jobs_progress CHECK (progress_percent BETWEEN 0 AND 100),
  CONSTRAINT fk_material_processing_jobs_material_id FOREIGN KEY (material_id) REFERENCES uploaded_materials(material_id) ON DELETE CASCADE,
  CONSTRAINT fk_material_processing_jobs_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_material_processing_jobs_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_material_processing_jobs_material_id ON material_processing_jobs(material_id);
CREATE INDEX idx_material_processing_jobs_workspace_id ON material_processing_jobs(workspace_id);
CREATE INDEX idx_material_processing_jobs_user_id ON material_processing_jobs(user_id);
CREATE INDEX idx_material_processing_jobs_status ON material_processing_jobs(status);

-- ============================================================
-- LEARNING STRUCTURE / ROADMAP
-- ============================================================

CREATE TABLE IF NOT EXISTS learning_structure_versions (
  structure_version_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id         UUID NOT NULL,
  material_id          UUID,
  version_no           INTEGER NOT NULL DEFAULT 1,
  status               VARCHAR(30) NOT NULL DEFAULT 'REVIEW_REQUIRED',
  generated_by         VARCHAR(30) NOT NULL DEFAULT 'AI',
  ai_model             VARCHAR(100),
  confidence_score     NUMERIC(5,2),
  input_summary        TEXT,
  warnings             JSONB,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmed_at         TIMESTAMPTZ,
  CONSTRAINT uq_learning_structure_versions_workspace_version UNIQUE (workspace_id, version_no),
  CONSTRAINT chk_learning_structure_versions_status CHECK (status IN ('REVIEW_REQUIRED', 'CONFIRMED', 'ARCHIVED', 'FAILED')),
  CONSTRAINT chk_learning_structure_versions_generated_by CHECK (generated_by IN ('AI', 'USER', 'SYSTEM')),
  CONSTRAINT fk_learning_structure_versions_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_structure_versions_material_id FOREIGN KEY (material_id) REFERENCES uploaded_materials(material_id) ON DELETE SET NULL
);

CREATE INDEX idx_learning_structure_versions_workspace_id ON learning_structure_versions(workspace_id);
CREATE INDEX idx_learning_structure_versions_material_id ON learning_structure_versions(material_id);
CREATE INDEX idx_learning_structure_versions_status ON learning_structure_versions(status);

CREATE TABLE IF NOT EXISTS chapters (
  chapter_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id         UUID NOT NULL,
  structure_version_id UUID NOT NULL,
  title                VARCHAR(500) NOT NULL,
  summary              TEXT,
  what_to_learn        JSONB,
  key_concepts         JSONB,
  learning_outcomes    JSONB,
  recommended_focus    JSONB,
  difficulty           VARCHAR(20),
  estimated_minutes    INTEGER,
  sequence_no          INTEGER NOT NULL,
  source_chunk_ids     JSONB,
  is_ai_generated      BOOLEAN NOT NULL DEFAULT true,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_chapters_structure_sequence UNIQUE (structure_version_id, sequence_no),
  CONSTRAINT chk_chapters_difficulty CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')),
  CONSTRAINT fk_chapters_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_chapters_structure_version_id FOREIGN KEY (structure_version_id) REFERENCES learning_structure_versions(structure_version_id) ON DELETE CASCADE
);

CREATE INDEX idx_chapters_workspace_id ON chapters(workspace_id);
CREATE INDEX idx_chapters_structure_version_id ON chapters(structure_version_id);

CREATE TABLE IF NOT EXISTS topics (
  topic_id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  chapter_id           UUID NOT NULL,
  workspace_id         UUID NOT NULL,
  structure_version_id UUID NOT NULL,
  title                VARCHAR(500) NOT NULL,
  summary_content      TEXT,
  what_to_learn        JSONB,
  key_concepts         JSONB,
  learning_outcomes    JSONB,
  recommended_focus    JSONB,
  difficulty           VARCHAR(20),
  estimated_minutes    INTEGER,
  sequence_no          INTEGER NOT NULL,
  source_chunk_ids     JSONB,
  is_ai_generated      BOOLEAN NOT NULL DEFAULT true,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_topics_chapter_sequence UNIQUE (chapter_id, sequence_no),
  CONSTRAINT chk_topics_difficulty CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')),
  CONSTRAINT fk_topics_chapter_id FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE CASCADE,
  CONSTRAINT fk_topics_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_topics_structure_version_id FOREIGN KEY (structure_version_id) REFERENCES learning_structure_versions(structure_version_id) ON DELETE CASCADE
);

CREATE INDEX idx_topics_chapter_id ON topics(chapter_id);
CREATE INDEX idx_topics_workspace_id ON topics(workspace_id);
CREATE INDEX idx_topics_structure_version_id ON topics(structure_version_id);

CREATE TABLE IF NOT EXISTS roadmaps (
  roadmap_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id         UUID NOT NULL,
  structure_version_id UUID NOT NULL,
  user_id              VARCHAR(100) NOT NULL,
  title                VARCHAR(500),
  description          TEXT,
  current_step_id      UUID,
  total_steps          INTEGER NOT NULL DEFAULT 0,
  completed_steps      INTEGER NOT NULL DEFAULT 0,
  progress_percent     NUMERIC(5,2) NOT NULL DEFAULT 0,
  version_no           INTEGER NOT NULL DEFAULT 1,
  status               VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  generated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_roadmaps_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
  CONSTRAINT chk_roadmaps_progress CHECK (progress_percent BETWEEN 0 AND 100),
  CONSTRAINT fk_roadmaps_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_roadmaps_structure_version_id FOREIGN KEY (structure_version_id) REFERENCES learning_structure_versions(structure_version_id) ON DELETE CASCADE,
  CONSTRAINT fk_roadmaps_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_roadmaps_workspace_id ON roadmaps(workspace_id);
CREATE INDEX idx_roadmaps_structure_version_id ON roadmaps(structure_version_id);
CREATE INDEX idx_roadmaps_user_id ON roadmaps(user_id);
CREATE INDEX idx_roadmaps_status ON roadmaps(status);

CREATE TABLE IF NOT EXISTS roadmap_steps (
  step_id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  roadmap_id            UUID NOT NULL,
  workspace_id          UUID NOT NULL,
  chapter_id            UUID,
  topic_id              UUID,
  title                 VARCHAR(500) NOT NULL,
  subtitle              VARCHAR(500),
  summary               TEXT,
  what_to_learn         JSONB,
  key_concepts          JSONB,
  learning_outcomes     JSONB,
  recommended_focus     JSONB,
  difficulty            VARCHAR(20),
  estimated_study_time  VARCHAR(100),
  estimated_minutes     INTEGER,
  sequence_no           INTEGER NOT NULL,
  status                VARCHAR(30) NOT NULL DEFAULT 'LOCKED',
  completed_at          TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_roadmap_steps_sequence UNIQUE (roadmap_id, sequence_no),
  CONSTRAINT chk_roadmap_steps_difficulty CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')),
  CONSTRAINT chk_roadmap_steps_status CHECK (status IN ('LOCKED', 'CURRENT', 'COMPLETED', 'SKIPPED')),
  CONSTRAINT fk_roadmap_steps_roadmap_id FOREIGN KEY (roadmap_id) REFERENCES roadmaps(roadmap_id) ON DELETE CASCADE,
  CONSTRAINT fk_roadmap_steps_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_roadmap_steps_chapter_id FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE SET NULL,
  CONSTRAINT fk_roadmap_steps_topic_id FOREIGN KEY (topic_id) REFERENCES topics(topic_id) ON DELETE SET NULL
);

CREATE INDEX idx_roadmap_steps_roadmap_id ON roadmap_steps(roadmap_id);
CREATE INDEX idx_roadmap_steps_workspace_id ON roadmap_steps(workspace_id);
CREATE INDEX idx_roadmap_steps_chapter_id ON roadmap_steps(chapter_id);
CREATE INDEX idx_roadmap_steps_topic_id ON roadmap_steps(topic_id);
CREATE INDEX idx_roadmap_steps_status ON roadmap_steps(status);

ALTER TABLE roadmaps
  ADD CONSTRAINT fk_roadmaps_current_step_id FOREIGN KEY (current_step_id) REFERENCES roadmap_steps(step_id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS roadmap_step_resources (
  resource_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  step_id           UUID NOT NULL,
  title             VARCHAR(500),
  platform          VARCHAR(50),
  resource_type     VARCHAR(50),
  search_query      TEXT,
  url               TEXT,
  reason            TEXT,
  is_ai_recommended BOOLEAN NOT NULL DEFAULT true,
  sequence_no       INTEGER NOT NULL DEFAULT 1,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_roadmap_step_resources_platform CHECK (
    platform IS NULL OR platform IN ('YouTube', 'Google', 'W3Schools', 'Coursera', 'Udemy', 'Other')
  ),
  CONSTRAINT chk_roadmap_step_resources_type CHECK (
    resource_type IS NULL OR resource_type IN ('VIDEO', 'ARTICLE', 'COURSE', 'SEARCH_QUERY')
  ),
  CONSTRAINT fk_roadmap_step_resources_step_id FOREIGN KEY (step_id) REFERENCES roadmap_steps(step_id) ON DELETE CASCADE
);

CREATE INDEX idx_roadmap_step_resources_step_id ON roadmap_step_resources(step_id);

CREATE TABLE IF NOT EXISTS roadmap_progress_logs (
  log_id     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  roadmap_id UUID NOT NULL,
  step_id    UUID,
  user_id    VARCHAR(100) NOT NULL,
  action_type VARCHAR(50) NOT NULL,
  old_status VARCHAR(30),
  new_status VARCHAR(30),
  metadata   JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_roadmap_progress_logs_action_type CHECK (
    action_type IN ('STEP_COMPLETED', 'STEP_SKIPPED', 'STEP_REOPENED', 'NEXT_STEP_ACTIVATED')
  ),
  CONSTRAINT fk_roadmap_progress_logs_roadmap_id FOREIGN KEY (roadmap_id) REFERENCES roadmaps(roadmap_id) ON DELETE CASCADE,
  CONSTRAINT fk_roadmap_progress_logs_step_id FOREIGN KEY (step_id) REFERENCES roadmap_steps(step_id) ON DELETE SET NULL,
  CONSTRAINT fk_roadmap_progress_logs_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_roadmap_progress_logs_roadmap_id ON roadmap_progress_logs(roadmap_id);
CREATE INDEX idx_roadmap_progress_logs_step_id ON roadmap_progress_logs(step_id);
CREATE INDEX idx_roadmap_progress_logs_user_id ON roadmap_progress_logs(user_id);

-- ============================================================
-- CALENDAR / EISENHOWER / PROGRESS
-- ============================================================

CREATE TABLE IF NOT EXISTS calendar_tasks (
  task_id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id          UUID NOT NULL,
  roadmap_id            UUID,
  roadmap_step_id       UUID,
  user_id               VARCHAR(100) NOT NULL,
  title                 VARCHAR(500) NOT NULL,
  description           TEXT,
  task_date             DATE NOT NULL,
  start_time            TIME,
  end_time              TIME,
  duration_minutes      INTEGER,
  category              VARCHAR(30) NOT NULL,
  priority              VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  status                VARCHAR(20) NOT NULL DEFAULT 'TODO',
  importance_score      NUMERIC(5,2),
  urgency_score         NUMERIC(5,2),
  is_important          BOOLEAN,
  is_urgent             BOOLEAN,
  eisenhower_quadrant   VARCHAR(30),
  classification_reason TEXT,
  classified_by         VARCHAR(30),
  classified_at         TIMESTAMPTZ,
  xp_reward             INTEGER,
  source                VARCHAR(30),
  completed_at          TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_calendar_tasks_category CHECK (category IN ('DEEP_STUDY', 'PRACTICE', 'REVIEW', 'PROJECT', 'PERSONAL')),
  CONSTRAINT chk_calendar_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
  CONSTRAINT chk_calendar_tasks_status CHECK (status IN ('TODO', 'COMPLETED', 'MISSED', 'CANCELLED')),
  CONSTRAINT chk_calendar_tasks_eisenhower CHECK (
    eisenhower_quadrant IS NULL OR eisenhower_quadrant IN ('DO_NOW', 'SCHEDULE', 'DELAY_OR_DELEGATE', 'ELIMINATE')
  ),
  CONSTRAINT chk_calendar_tasks_classified_by CHECK (
    classified_by IS NULL OR classified_by IN ('AI', 'RULE_BASED', 'USER')
  ),
  CONSTRAINT chk_calendar_tasks_source CHECK (
    source IS NULL OR source IN ('USER_CREATED', 'AI_GENERATED', 'SYSTEM_GENERATED')
  ),
  CONSTRAINT fk_calendar_tasks_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_calendar_tasks_roadmap_id FOREIGN KEY (roadmap_id) REFERENCES roadmaps(roadmap_id) ON DELETE SET NULL,
  CONSTRAINT fk_calendar_tasks_roadmap_step_id FOREIGN KEY (roadmap_step_id) REFERENCES roadmap_steps(step_id) ON DELETE SET NULL,
  CONSTRAINT fk_calendar_tasks_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_calendar_tasks_workspace_id ON calendar_tasks(workspace_id);
CREATE INDEX idx_calendar_tasks_roadmap_id ON calendar_tasks(roadmap_id);
CREATE INDEX idx_calendar_tasks_roadmap_step_id ON calendar_tasks(roadmap_step_id);
CREATE INDEX idx_calendar_tasks_user_id ON calendar_tasks(user_id);
CREATE INDEX idx_calendar_tasks_task_date ON calendar_tasks(task_date);
CREATE INDEX idx_calendar_tasks_status ON calendar_tasks(status);
CREATE INDEX idx_calendar_tasks_category ON calendar_tasks(category);
CREATE INDEX idx_calendar_tasks_eisenhower_quadrant ON calendar_tasks(eisenhower_quadrant);

CREATE TABLE IF NOT EXISTS calendar_schedule_runs (
  run_id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id             UUID NOT NULL,
  roadmap_id               UUID,
  user_id                  VARCHAR(100) NOT NULL,
  schedule_scope           VARCHAR(30),
  start_date               DATE,
  end_date                 DATE,
  available_days           JSONB,
  available_time_windows   JSONB,
  preferred_session_minutes INTEGER,
  max_sessions_per_day     INTEGER,
  include_review_sessions  BOOLEAN NOT NULL DEFAULT true,
  status                   VARCHAR(20) NOT NULL DEFAULT 'PREVIEW',
  generated_tasks          JSONB,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmed_at             TIMESTAMPTZ,
  CONSTRAINT chk_calendar_schedule_runs_scope CHECK (
    schedule_scope IS NULL OR schedule_scope IN ('CURRENT_STEP', 'PHASE', 'FULL_ROADMAP')
  ),
  CONSTRAINT chk_calendar_schedule_runs_status CHECK (status IN ('PREVIEW', 'CONFIRMED', 'CANCELLED')),
  CONSTRAINT fk_calendar_schedule_runs_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_calendar_schedule_runs_roadmap_id FOREIGN KEY (roadmap_id) REFERENCES roadmaps(roadmap_id) ON DELETE SET NULL,
  CONSTRAINT fk_calendar_schedule_runs_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_calendar_schedule_runs_workspace_id ON calendar_schedule_runs(workspace_id);
CREATE INDEX idx_calendar_schedule_runs_roadmap_id ON calendar_schedule_runs(roadmap_id);
CREATE INDEX idx_calendar_schedule_runs_user_id ON calendar_schedule_runs(user_id);
CREATE INDEX idx_calendar_schedule_runs_status ON calendar_schedule_runs(status);

CREATE TABLE IF NOT EXISTS workspace_progress (
  progress_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id       UUID UNIQUE NOT NULL,
  total_steps        INTEGER NOT NULL DEFAULT 0,
  completed_steps    INTEGER NOT NULL DEFAULT 0,
  total_tasks        INTEGER NOT NULL DEFAULT 0,
  completed_tasks    INTEGER NOT NULL DEFAULT 0,
  completion_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
  last_calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_workspace_progress_completion CHECK (completion_percent BETWEEN 0 AND 100),
  CONSTRAINT fk_workspace_progress_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_workspace_progress_workspace_id ON workspace_progress(workspace_id);

CREATE TABLE IF NOT EXISTS progress_logs (
  log_id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id          UUID NOT NULL,
  log_date              DATE NOT NULL DEFAULT current_date,
  steps_completed_today INTEGER NOT NULL DEFAULT 0,
  tasks_completed_today INTEGER NOT NULL DEFAULT 0,
  minutes_studied_today INTEGER NOT NULL DEFAULT 0,
  notes                 TEXT,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_progress_logs_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_progress_logs_workspace_id ON progress_logs(workspace_id);
CREATE INDEX idx_progress_logs_log_date ON progress_logs(log_date);

-- ============================================================
-- REMINDERS / NOTIFICATIONS / LOGS
-- ============================================================

CREATE TABLE IF NOT EXISTS reminders (
  reminder_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id     UUID NOT NULL,
  calendar_task_id UUID,
  roadmap_step_id  UUID,
  reminder_type    VARCHAR(30) NOT NULL,
  message          TEXT NOT NULL,
  scheduled_at     TIMESTAMPTZ NOT NULL,
  delivery_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  sent_at          TIMESTAMPTZ,
  CONSTRAINT chk_reminders_type CHECK (reminder_type IN ('TASK_REMINDER', 'DEADLINE_WARNING', 'PROGRESS_LAG', 'GENERAL')),
  CONSTRAINT chk_reminders_delivery_status CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
  CONSTRAINT fk_reminders_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_reminders_calendar_task_id FOREIGN KEY (calendar_task_id) REFERENCES calendar_tasks(task_id) ON DELETE CASCADE,
  CONSTRAINT fk_reminders_roadmap_step_id FOREIGN KEY (roadmap_step_id) REFERENCES roadmap_steps(step_id) ON DELETE SET NULL
);

CREATE INDEX idx_reminders_workspace_id ON reminders(workspace_id);
CREATE INDEX idx_reminders_calendar_task_id ON reminders(calendar_task_id);
CREATE INDEX idx_reminders_roadmap_step_id ON reminders(roadmap_step_id);
CREATE INDEX idx_reminders_delivery_status ON reminders(delivery_status);
CREATE INDEX idx_reminders_scheduled_at ON reminders(scheduled_at);

CREATE TABLE IF NOT EXISTS notifications (
  notification_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id         VARCHAR(100) NOT NULL,
  workspace_id    UUID,
  type            VARCHAR(50) NOT NULL,
  title           VARCHAR(500) NOT NULL,
  message         TEXT NOT NULL,
  is_read         BOOLEAN NOT NULL DEFAULT false,
  read_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_notifications_type CHECK (
    type IN ('MATERIAL_ANALYSIS_DONE', 'ROADMAP_READY', 'TASK_REMINDER', 'TASK_OVERDUE', 'AI_SCHEDULE_READY')
  ),
  CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  CONSTRAINT fk_notifications_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_workspace_id ON notifications(workspace_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

CREATE TABLE IF NOT EXISTS notification_logs (
  log_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  notification_id UUID,
  reminder_id     UUID,
  channel         VARCHAR(50) NOT NULL DEFAULT 'IN_APP',
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  sent_at         TIMESTAMPTZ,
  error_message   TEXT,CONSTRAINT chk_notification_logs_channel CHECK (channel IN ('IN_APP', 'WEBSOCKET', 'EMAIL', 'PUSH')),
  CONSTRAINT chk_notification_logs_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
  CONSTRAINT fk_notification_logs_notification_id FOREIGN KEY (notification_id) REFERENCES notifications(notification_id) ON DELETE CASCADE,
  CONSTRAINT fk_notification_logs_reminder_id FOREIGN KEY (reminder_id) REFERENCES reminders(reminder_id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_logs_notification_id ON notification_logs(notification_id);
CREATE INDEX idx_notification_logs_reminder_id ON notification_logs(reminder_id);

CREATE TABLE IF NOT EXISTS business_activity_logs (
  log_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id      VARCHAR(100),
  workspace_id UUID,
  entity_type  VARCHAR(100),
  entity_id    UUID,
  action_type  VARCHAR(100) NOT NULL,
  title        VARCHAR(500),
  description  TEXT,
  old_value    JSONB,
  new_value    JSONB,
  metadata     JSONB,
  ip_address   VARCHAR(100),
  user_agent   TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_business_activity_logs_entity_type CHECK (
    entity_type IS NULL OR entity_type IN ('WORKSPACE', 'MATERIAL', 'STRUCTURE', 'ROADMAP', 'STEP', 'CALENDAR_TASK', 'NOTIFICATION')
  ),
  CONSTRAINT chk_business_activity_logs_action_type CHECK (
    action_type IN (
      'WORKSPACE_CREATED', 'MATERIAL_UPLOADED', 'AI_STRUCTURE_GENERATED', 'STRUCTURE_CONFIRMED',
      'ROADMAP_GENERATED', 'ROADMAP_STEP_COMPLETED', 'AI_SCHEDULE_PREVIEW_CREATED', 'CALENDAR_TASK_COMPLETED', 'EISENHOWER_CLASSIFIED'
    )
  ),
  CONSTRAINT fk_business_activity_logs_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
  CONSTRAINT fk_business_activity_logs_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_business_activity_logs_user_id ON business_activity_logs(user_id);
CREATE INDEX idx_business_activity_logs_workspace_id ON business_activity_logs(workspace_id);
CREATE INDEX idx_business_activity_logs_entity_type ON business_activity_logs(entity_type);
CREATE INDEX idx_business_activity_logs_entity_id ON business_activity_logs(entity_id);
CREATE INDEX idx_business_activity_logs_action_type ON business_activity_logs(action_type);
CREATE INDEX idx_business_activity_logs_created_at ON business_activity_logs(created_at);

-- ============================================================
-- PLANS / SUBSCRIPTIONS / STUDY SESSIONS / POMODORO
-- ============================================================

CREATE TABLE IF NOT EXISTS service_plans (
  plan_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  plan_name        VARCHAR(100) NOT NULL,
  plan_type        VARCHAR(20) UNIQUE NOT NULL,
  monthly_price    NUMERIC(10,2) NOT NULL DEFAULT 0,
  ai_parsing_limit INTEGER NOT NULL DEFAULT 5,
  max_file_mb      INTEGER NOT NULL DEFAULT 20,
  max_workspace_mb INTEGER NOT NULL DEFAULT 100,
  is_active        BOOLEAN NOT NULL DEFAULT true,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_service_plans_plan_type CHECK (plan_type IN ('FREE', 'SKILL_BUILDER', 'PREMIUM'))
);

CREATE TABLE IF NOT EXISTS subscriptions (
  subscription_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id         VARCHAR(100) NOT NULL,
  plan_id         UUID NOT NULL,
  start_date      DATE NOT NULL,
  end_date        DATE,
  status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_subscriptions_status CHECK (status IN ('TRIAL', 'ACTIVE', 'CANCELED', 'EXPIRED', 'PAST_DUE')),
  CONSTRAINT fk_subscriptions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  CONSTRAINT fk_subscriptions_plan_id FOREIGN KEY (plan_id) REFERENCES service_plans(plan_id)
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_plan_id ON subscriptions(plan_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

CREATE TABLE IF NOT EXISTS study_sessions (
  session_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  workspace_id     UUID NOT NULL,
  calendar_task_id UUID,
  roadmap_step_id  UUID,
  user_id          VARCHAR(100) NOT NULL,
  started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at         TIMESTAMPTZ,
  duration_minutes INTEGER,
  notes            TEXT,
  CONSTRAINT fk_study_sessions_workspace_id FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_study_sessions_calendar_task_id FOREIGN KEY (calendar_task_id) REFERENCES calendar_tasks(task_id) ON DELETE SET NULL,
  CONSTRAINT fk_study_sessions_roadmap_step_id FOREIGN KEY (roadmap_step_id) REFERENCES roadmap_steps(step_id) ON DELETE SET NULL,
  CONSTRAINT fk_study_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_study_sessions_workspace_id ON study_sessions(workspace_id);
CREATE INDEX idx_study_sessions_calendar_task_id ON study_sessions(calendar_task_id);
CREATE INDEX idx_study_sessions_roadmap_step_id ON study_sessions(roadmap_step_id);
CREATE INDEX idx_study_sessions_user_id ON study_sessions(user_id);

CREATE TABLE IF NOT EXISTS pomodoro_sessions (
  pomodoro_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  calendar_task_id UUID,
  roadmap_step_id  UUID,
  user_id          VARCHAR(100) NOT NULL,
  duration_minutes INTEGER NOT NULL DEFAULT 25,
  status           VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at         TIMESTAMPTZ,
  CONSTRAINT chk_pomodoro_sessions_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'INTERRUPTED')),
  CONSTRAINT fk_pomodoro_sessions_calendar_task_id FOREIGN KEY (calendar_task_id) REFERENCES calendar_tasks(task_id) ON DELETE SET NULL,
  CONSTRAINT fk_pomodoro_sessions_roadmap_step_id FOREIGN KEY (roadmap_step_id) REFERENCES roadmap_steps(step_id) ON DELETE SET NULL,
  CONSTRAINT fk_pomodoro_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_pomodoro_sessions_calendar_task_id ON pomodoro_sessions(calendar_task_id);
CREATE INDEX idx_pomodoro_sessions_roadmap_step_id ON pomodoro_sessions(roadmap_step_id);
CREATE INDEX idx_pomodoro_sessions_user_id ON pomodoro_sessions(user_id);

-- ============================================================
-- UPDATED_AT HELPER TRIGGER
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_study_workspaces_updated_at BEFORE UPDATE ON study_workspaces FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_onboarding_profiles_updated_at BEFORE UPDATE ON onboarding_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_uploaded_materials_updated_at BEFORE UPDATE ON uploaded_materials FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_extracted_documents_updated_at BEFORE UPDATE ON extracted_documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_material_processing_jobs_updated_at BEFORE UPDATE ON material_processing_jobs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_chapters_updated_at BEFORE UPDATE ON chapters FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_topics_updated_at BEFORE UPDATE ON topics FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_roadmaps_updated_at BEFORE UPDATE ON roadmaps FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_roadmap_steps_updated_at BEFORE UPDATE ON roadmap_steps FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_calendar_tasks_updated_at BEFORE UPDATE ON calendar_tasks FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- BASIC SEED DATA
-- ============================================================

INSERT INTO roles (role_name, display_name, description)
VALUES
  ('LEARNER', 'Learner', 'Default learning user'),
  ('ADMIN', 'Admin', 'System administrator')
  ON CONFLICT (role_name) DO NOTHING;

INSERT INTO permissions (permission_name, resource, action, description)
VALUES
  ('workspace:create', 'workspace', 'create', 'Create workspace'),
  ('workspace:read', 'workspace', 'read', 'Read workspace'),
  ('workspace:update', 'workspace', 'update', 'Update workspace'),
  ('workspace:delete', 'workspace', 'delete', 'Delete workspace'),
  ('material:upload', 'material', 'create', 'Upload learning material'),
  ('material:read', 'material', 'read', 'Read uploaded material'),
  ('roadmap:read', 'roadmap', 'read', 'Read roadmap'),
  ('roadmap:update', 'roadmap', 'update', 'Update roadmap progress'),
  ('calendar:create', 'calendar', 'create', 'Create calendar tasks'),
  ('calendar:read', 'calendar', 'read', 'Read calendar'),
  ('calendar:update', 'calendar', 'update', 'Update calendar tasks'),
  ('admin:manage', 'admin', 'manage', 'Manage system')
  ON CONFLICT (permission_name) DO NOTHING;

INSERT INTO service_plans (plan_name, plan_type, monthly_price, ai_parsing_limit, max_file_mb, max_workspace_mb)
VALUES
  ('Free', 'FREE', 0, 2, 20, 100),
  ('Skill Builder', 'SKILL_BUILDER', 89000, 20, 50, 500),
  ('Premium', 'PREMIUM', 199000, 100, 100, 2000)
  ON CONFLICT (plan_type) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
  JOIN permissions p ON p.permission_name IN (
    'workspace:create', 'workspace:read', 'workspace:update',
    'material:upload', 'material:read',
    'roadmap:read', 'roadmap:update',
    'calendar:create', 'calendar:read', 'calendar:update'
  )
WHERE r.role_name = 'LEARNER'
  ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
  CROSS JOIN permissions p
WHERE r.role_name = 'ADMIN'
  ON CONFLICT (role_id, permission_id) DO NOTHING;