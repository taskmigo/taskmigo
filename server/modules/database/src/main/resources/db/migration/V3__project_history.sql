CREATE TABLE project_history (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    action VARCHAR(64) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    actor_display_name VARCHAR(255) NOT NULL,
    target_type VARCHAR(16),
    target_id VARCHAR(255),
    target_display_name VARCHAR(255),
    changes_json TEXT NOT NULL,
    data_json TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_project_history_target CHECK (
        (target_type IS NULL AND target_id IS NULL AND target_display_name IS NULL)
        OR (target_type IS NOT NULL AND target_id IS NOT NULL AND target_display_name IS NOT NULL)
    )
);

CREATE INDEX ix_project_history_project_cursor
    ON project_history(project_id, occurred_at DESC, id DESC);
