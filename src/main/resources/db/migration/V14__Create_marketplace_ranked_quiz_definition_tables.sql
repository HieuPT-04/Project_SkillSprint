CREATE TABLE marketplace_ranked_quiz_definitions (
    definition_id UUID PRIMARY KEY,
    pack_version_id UUID NOT NULL UNIQUE REFERENCES marketplace_pack_versions(version_id),
    questions_per_step INTEGER NOT NULL CHECK (questions_per_step > 0),
    total_question_count INTEGER NOT NULL CHECK (total_question_count > 0),
    daily_attempt_limit INTEGER NOT NULL CHECK (daily_attempt_limit > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE marketplace_ranked_question_selections (
    selection_id UUID PRIMARY KEY,
    definition_id UUID NOT NULL REFERENCES marketplace_ranked_quiz_definitions(definition_id),
    source_step_key VARCHAR(100) NOT NULL,
    step_order INTEGER NOT NULL CHECK (step_order > 0),
    question_id UUID NOT NULL,
    selection_order INTEGER NOT NULL CHECK (selection_order > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_marketplace_ranked_selection_question
        UNIQUE (definition_id, source_step_key, question_id),
    CONSTRAINT uq_marketplace_ranked_selection_order
        UNIQUE (definition_id, source_step_key, selection_order)
);

CREATE INDEX idx_marketplace_ranked_selection_definition_step
    ON marketplace_ranked_question_selections (definition_id, step_order, selection_order);
