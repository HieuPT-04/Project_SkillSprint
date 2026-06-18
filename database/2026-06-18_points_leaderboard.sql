CREATE TABLE IF NOT EXISTS point_events (
    point_event_id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    workspace_id UUID,
    event_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    points INTEGER NOT NULL,
    description VARCHAR(500),
    event_date DATE NOT NULL,
    week_start_date DATE NOT NULL,
    month_start_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_point_events_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),
    CONSTRAINT fk_point_events_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES study_workspaces(workspace_id),
    CONSTRAINT uk_point_events_source
        UNIQUE (user_id, event_type, source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_point_events_week
    ON point_events(week_start_date, points DESC);

CREATE INDEX IF NOT EXISTS idx_point_events_month
    ON point_events(month_start_date, points DESC);

CREATE INDEX IF NOT EXISTS idx_point_events_user_created
    ON point_events(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_point_events_user_date
    ON point_events(user_id, event_date);

CREATE TABLE IF NOT EXISTS user_point_summaries (
    user_id VARCHAR(100) PRIMARY KEY,
    total_points INTEGER NOT NULL DEFAULT 0,
    current_week_points INTEGER NOT NULL DEFAULT 0,
    current_week_start_date DATE,
    current_month_points INTEGER NOT NULL DEFAULT 0,
    current_month_start_date DATE,
    streak_days INTEGER NOT NULL DEFAULT 0,
    last_point_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_point_summaries_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_point_summaries_total
    ON user_point_summaries(total_points DESC);

CREATE TABLE IF NOT EXISTS user_quiz_scores (
    user_quiz_score_id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    quiz_id UUID NOT NULL,
    best_attempt_id UUID,
    best_score_percent INTEGER NOT NULL DEFAULT 0,
    earned_points INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_quiz_scores_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),
    CONSTRAINT fk_user_quiz_scores_quiz
        FOREIGN KEY (quiz_id)
        REFERENCES quizzes(quiz_id),
    CONSTRAINT fk_user_quiz_scores_best_attempt
        FOREIGN KEY (best_attempt_id)
        REFERENCES quiz_attempts(attempt_id),
    CONSTRAINT uk_user_quiz_scores_user_quiz
        UNIQUE (user_id, quiz_id)
);

CREATE INDEX IF NOT EXISTS idx_user_quiz_scores_user
    ON user_quiz_scores(user_id);
