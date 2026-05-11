-- ============================================================
-- SkillSprint Core MVP Schema - Production-ready RBAC Edition
-- Database: PostgreSQL
-- Note: users.user_id stores AWS Cognito sub
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- AUTHENTICATION & USERS
-- ============================================================

CREATE TABLE users (
    user_id VARCHAR(100) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    full_name VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    avatar_s3_key TEXT,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ============================================================
-- RBAC
-- ============================================================

CREATE TABLE roles (
    role_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_name VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_roles_role_name ON roles(role_name);

CREATE TABLE permissions (
    permission_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    permission_name VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_permissions_resource_action UNIQUE (resource, action)
);

CREATE TABLE role_permissions (
    rp_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_role_permissions UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

CREATE TABLE user_roles (
    user_role_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    workspace_id UUID,
    granted_by VARCHAR(100) REFERENCES users(user_id) ON DELETE SET NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    CONSTRAINT uq_user_roles UNIQUE (user_id, role_id, workspace_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_user_roles_workspace_id ON user_roles(workspace_id);

-- ============================================================
-- WORKSPACE MANAGEMENT
-- ============================================================

CREATE TABLE study_workspaces (
    workspace_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE user_roles
ADD CONSTRAINT fk_user_roles_workspace
FOREIGN KEY (workspace_id) REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE;

CREATE INDEX idx_study_workspaces_user_id ON study_workspaces(user_id);

CREATE TABLE onboarding_profiles (
    profile_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID UNIQUE NOT NULL REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    target_goal TEXT NOT NULL,
    study_hours_per_week NUMERIC(4,1) NOT NULL,
    target_deadline DATE,
    confidence VARCHAR(10) NOT NULL DEFAULT 'MEDIUM'
        CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_onboarding_profiles_workspace_id ON onboarding_profiles(workspace_id);

-- ============================================================
-- UPLOAD & FILE PROCESSING
-- ============================================================

CREATE TABLE uploaded_materials (
    material_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    original_file_name VARCHAR(500) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    s3_bucket VARCHAR(255),
    s3_object_key TEXT,
    file_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'UPLOADED', 'PROCESSING', 'EXTRACTED', 'PARSING', 'COMPLETED', 'FAILED')),
    error_message TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_uploaded_materials_workspace_id ON uploaded_materials(workspace_id);
CREATE INDEX idx_uploaded_materials_user_id ON uploaded_materials(user_id);
CREATE INDEX idx_uploaded_materials_status ON uploaded_materials(status);

CREATE TABLE extracted_documents (
    document_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    material_id UUID NOT NULL REFERENCES uploaded_materials(material_id) ON DELETE CASCADE,
    extracted_text TEXT,
    page_count INTEGER,
    word_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_extracted_documents_material_id ON extracted_documents(material_id);

-- ============================================================
-- COURSE STRUCTURE GENERATION
-- ============================================================

CREATE TABLE chapters (
    chapter_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chapters_workspace_sequence UNIQUE (workspace_id, sequence_no)
);

CREATE INDEX idx_chapters_workspace_id ON chapters(workspace_id);

CREATE TABLE topics (
    topic_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chapter_id UUID NOT NULL REFERENCES chapters(chapter_id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    sequence_no INTEGER NOT NULL,
    summary_content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_topics_chapter_sequence UNIQUE (chapter_id, sequence_no)
);

CREATE INDEX idx_topics_chapter_id ON topics(chapter_id);

-- ============================================================
-- PLANNING & ROADMAP
-- ============================================================

CREATE TABLE roadmaps (
    roadmap_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED', 'ADJUSTED')),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_roadmaps_workspace_id ON roadmaps(workspace_id);
CREATE INDEX idx_roadmaps_status ON roadmaps(status);

CREATE TABLE roadmap_items (
    item_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    roadmap_id UUID NOT NULL REFERENCES roadmaps(roadmap_id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    phase_order INTEGER NOT NULL,
    start_date DATE,
    end_date DATE,
    CONSTRAINT uq_roadmap_items_roadmap_phase UNIQUE (roadmap_id, phase_order)
);

CREATE INDEX idx_roadmap_items_roadmap_id ON roadmap_items(roadmap_id);

CREATE TABLE study_tasks (
    task_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    item_id UUID NOT NULL REFERENCES roadmap_items(item_id) ON DELETE CASCADE,
    topic_id UUID REFERENCES topics(topic_id) ON DELETE SET NULL,
    title VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO'
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'SKIPPED')),
    due_date DATE,
    is_important BOOLEAN NOT NULL DEFAULT FALSE,
    is_urgent BOOLEAN NOT NULL DEFAULT FALSE,
    planned_minutes INTEGER,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_study_tasks_item_id ON study_tasks(item_id);
CREATE INDEX idx_study_tasks_topic_id ON study_tasks(topic_id);
CREATE INDEX idx_study_tasks_status ON study_tasks(status);
CREATE INDEX idx_study_tasks_due_date ON study_tasks(due_date);

CREATE TABLE adaptive_adjustments (
    adjustment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    roadmap_id UUID NOT NULL REFERENCES roadmaps(roadmap_id) ON DELETE CASCADE,
    task_id UUID REFERENCES study_tasks(task_id) ON DELETE SET NULL,
    reason TEXT NOT NULL,
    suggested_changes TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'APPLIED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_adaptive_adjustments_roadmap_id ON adaptive_adjustments(roadmap_id);
CREATE INDEX idx_adaptive_adjustments_task_id ON adaptive_adjustments(task_id);

-- ============================================================
-- PROGRESS TRACKING & EXECUTION
-- ============================================================

CREATE TABLE study_sessions (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES study_tasks(task_id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    duration_minutes INTEGER,
    notes TEXT
);

CREATE INDEX idx_study_sessions_workspace_id ON study_sessions(workspace_id);
CREATE INDEX idx_study_sessions_task_id ON study_sessions(task_id);

CREATE TABLE pomodoro_sessions (
    pomodoro_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id UUID NOT NULL REFERENCES study_tasks(task_id) ON DELETE CASCADE,
    duration_minutes INTEGER NOT NULL DEFAULT 25,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'INTERRUPTED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ
);

CREATE INDEX idx_pomodoro_sessions_task_id ON pomodoro_sessions(task_id);

CREATE TABLE workspace_progress (
    progress_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID UNIQUE NOT NULL REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    total_tasks INTEGER NOT NULL DEFAULT 0,
    completed_tasks INTEGER NOT NULL DEFAULT 0,
    completion_percent NUMERIC(5,2) NOT NULL DEFAULT 0
        CHECK (completion_percent BETWEEN 0 AND 100),
    last_calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspace_progress_workspace_id ON workspace_progress(workspace_id);

CREATE TABLE progress_logs (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    log_date DATE NOT NULL DEFAULT CURRENT_DATE,
    tasks_completed_today INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_progress_logs_workspace_id ON progress_logs(workspace_id);
CREATE INDEX idx_progress_logs_log_date ON progress_logs(log_date);

-- ============================================================
-- REMINDERS & NOTIFICATIONS
-- ============================================================

CREATE TABLE reminders (
    reminder_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    task_id UUID REFERENCES study_tasks(task_id) ON DELETE CASCADE,
    reminder_type VARCHAR(30) NOT NULL
        CHECK (reminder_type IN ('DEADLINE_WARNING', 'PROGRESS_LAG', 'GENERAL')),
    message TEXT NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'SENT', 'READ', 'FAILED')),
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_reminders_workspace_id ON reminders(workspace_id);
CREATE INDEX idx_reminders_task_id ON reminders(task_id);
CREATE INDEX idx_reminders_delivery_status ON reminders(delivery_status);
CREATE INDEX idx_reminders_scheduled_at ON reminders(scheduled_at);

CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    workspace_id UUID REFERENCES study_workspaces(workspace_id) ON DELETE CASCADE,
    reminder_id UUID REFERENCES reminders(reminder_id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    notification_type VARCHAR(50) NOT NULL
        CHECK (notification_type IN (
            'MATERIAL_COMPLETED',
            'MATERIAL_FAILED',
            'ROADMAP_GENERATED',
            'TASK_OVERDUE',
            'BEHIND_SCHEDULE',
            'REMINDER_CREATED',
            'TASK_RESCHEDULED',
            'GENERAL'
        )),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_workspace_id ON notifications(workspace_id);
CREATE INDEX idx_notifications_reminder_id ON notifications(reminder_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

CREATE TABLE notification_logs (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    notification_id UUID REFERENCES notifications(notification_id) ON DELETE CASCADE,
    reminder_id UUID REFERENCES reminders(reminder_id) ON DELETE CASCADE,
    channel VARCHAR(50) NOT NULL DEFAULT 'IN_APP',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'READ', 'FAILED')),
    sent_at TIMESTAMPTZ,
    error_message TEXT,
    CONSTRAINT ck_notification_logs_target CHECK (
        notification_id IS NOT NULL OR reminder_id IS NOT NULL
    )
);

CREATE INDEX idx_notification_logs_notification_id ON notification_logs(notification_id);
CREATE INDEX idx_notification_logs_reminder_id ON notification_logs(reminder_id);

-- ============================================================
-- BUSINESS LOGGING
-- ============================================================

CREATE TABLE business_activity_logs (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) REFERENCES users(user_id) ON DELETE SET NULL,
    workspace_id UUID REFERENCES study_workspaces(workspace_id) ON DELETE SET NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    action_type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    old_value JSONB,
    new_value JSONB,
    metadata JSONB,
    ip_address VARCHAR(100),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_business_activity_logs_user_id ON business_activity_logs(user_id);
CREATE INDEX idx_business_activity_logs_workspace_id ON business_activity_logs(workspace_id);
CREATE INDEX idx_business_activity_logs_entity ON business_activity_logs(entity_type, entity_id);
CREATE INDEX idx_business_activity_logs_action_type ON business_activity_logs(action_type);
CREATE INDEX idx_business_activity_logs_created_at ON business_activity_logs(created_at);

-- ============================================================
-- ADJUSTMENT / RESCHEDULE
-- ============================================================

CREATE TABLE reschedule_logs (
    reschedule_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id UUID NOT NULL REFERENCES study_tasks(task_id) ON DELETE CASCADE,
    old_due_date DATE NOT NULL,
    new_due_date DATE NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reschedule_logs_task_id ON reschedule_logs(task_id);

-- ============================================================
-- FEATURE GATING / SUBSCRIPTIONS
-- ============================================================

CREATE TABLE service_plans (
    plan_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_name VARCHAR(100) NOT NULL,
    plan_type VARCHAR(20) UNIQUE NOT NULL
        CHECK (plan_type IN ('FREE', 'SKILL_BUILDER', 'PREMIUM')),
    monthly_price NUMERIC(10,2) NOT NULL DEFAULT 0
        CHECK (monthly_price >= 0),
    ai_parsing_limit INTEGER NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE subscriptions (
    subscription_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES service_plans(plan_id),
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('TRIAL', 'ACTIVE', 'CANCELED', 'EXPIRED', 'PAST_DUE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_plan_id ON subscriptions(plan_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

-- ============================================================
-- BASE SEED DATA
-- ============================================================

INSERT INTO roles (role_name, display_name, description)
VALUES
    ('LEARNER', 'Learner', 'Default learner role'),
    ('ADMIN', 'Admin', 'System administrator role')
ON CONFLICT (role_name) DO NOTHING;

INSERT INTO permissions (permission_name, resource, action, description)
VALUES
    ('workspace:create', 'workspace', 'create', 'Create study workspace'),
    ('workspace:read', 'workspace', 'read', 'Read own study workspace'),
    ('workspace:update', 'workspace', 'update', 'Update own study workspace'),
    ('workspace:delete', 'workspace', 'delete', 'Delete own study workspace'),
    ('material:upload', 'material', 'upload', 'Upload learning material'),
    ('material:read', 'material', 'read', 'Read uploaded material metadata'),
    ('roadmap:generate', 'roadmap', 'generate', 'Generate roadmap'),
    ('task:update', 'task', 'update', 'Update study task'),
    ('progress:read', 'progress', 'read', 'Read learning progress'),
    ('admin:manage_users', 'admin', 'manage_users', 'Manage users')
ON CONFLICT (permission_name) DO NOTHING;
