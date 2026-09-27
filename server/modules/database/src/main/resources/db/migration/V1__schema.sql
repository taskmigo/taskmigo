CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    password_hash VARCHAR(255),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_users_names CHECK (btrim(first_name) <> '' AND btrim(last_name) <> '')
);

CREATE TABLE user_emails (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    normalized_email VARCHAR(320) NOT NULL,
    PRIMARY KEY (user_id, normalized_email),
    CONSTRAINT uk_user_emails_normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_user_emails_normalized_email CHECK (normalized_email = lower(btrim(normalized_email)))
);

CREATE TABLE groups (
    id UUID PRIMARY KEY,
    code VARCHAR(200) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description VARCHAR(1000)
);

CREATE TABLE group_members (
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX ix_group_members_user_id ON group_members(user_id);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000)
);

CREATE TABLE role_hierarchy (
    parent_role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    child_role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_role_id, child_role_id)
);
CREATE INDEX ix_role_hierarchy_child_role_id ON role_hierarchy(child_role_id);

CREATE TABLE role_hierarchy_closure (
    ancestor_role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    descendant_role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (ancestor_role_id, descendant_role_id)
);
CREATE INDEX ix_role_hierarchy_closure_descendant_role_id ON role_hierarchy_closure(descendant_role_id);

CREATE TABLE group_hierarchy (
    parent_group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    child_group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_group_id, child_group_id)
);
CREATE INDEX ix_group_hierarchy_child_group_id ON group_hierarchy(child_group_id);

CREATE TABLE group_hierarchy_closure (
    ancestor_group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    descendant_group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (ancestor_group_id, descendant_group_id)
);
CREATE INDEX ix_group_hierarchy_closure_descendant_group_id ON group_hierarchy_closure(descendant_group_id);

CREATE TABLE statements (
    id UUID PRIMARY KEY,
    code VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    effect VARCHAR(16) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    method VARCHAR(16) NOT NULL,
    path VARCHAR(2000) NOT NULL,
    policy TEXT NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_statements_code UNIQUE (code),
    CONSTRAINT ck_statements_effect CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT ck_statements_scope CHECK (scope IN ('OBJECT', 'REQUEST')),
    CONSTRAINT ck_statements_policy_nonblank CHECK (btrim(policy) <> '')
);

CREATE FUNCTION set_statement_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = GREATEST(clock_timestamp(), OLD.updated_at + INTERVAL '1 microsecond');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_statement_updated_at
BEFORE UPDATE ON statements
FOR EACH ROW
EXECUTE FUNCTION set_statement_updated_at();

CREATE TABLE role_statements (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    statement_id UUID NOT NULL REFERENCES statements(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, statement_id)
);
CREATE INDEX ix_role_statements_statement_id ON role_statements(statement_id);

CREATE TABLE subject_role_bindings (
    id UUID PRIMARY KEY,
    subject_type VARCHAR(100) NOT NULL,
    subject_id UUID NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_subject_role_bindings_subject_role UNIQUE (subject_type, subject_id, role_id)
);
CREATE INDEX ix_subject_role_bindings_subject ON subject_role_bindings(subject_type, subject_id);
CREATE INDEX ix_subject_role_bindings_role_id ON subject_role_bindings(role_id);

CREATE TABLE subject_statement_bindings (
    id UUID PRIMARY KEY,
    subject_type VARCHAR(100) NOT NULL,
    subject_id UUID NOT NULL,
    statement_id UUID NOT NULL REFERENCES statements(id) ON DELETE CASCADE,
    CONSTRAINT uk_subject_statement_bindings_subject_statement UNIQUE (subject_type, subject_id, statement_id)
);
CREATE INDEX ix_subject_statement_bindings_subject ON subject_statement_bindings(subject_type, subject_id);
CREATE INDEX ix_subject_statement_bindings_statement_id ON subject_statement_bindings(statement_id);

CREATE TABLE oauth2_registered_client (
    id varchar(100) NOT NULL,
    client_id varchar(100) NOT NULL,
    client_id_issued_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret varchar(200) DEFAULT NULL,
    client_secret_expires_at timestamptz DEFAULT NULL,
    client_name varchar(200) NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types varchar(1000) NOT NULL,
    redirect_uris varchar(1000) DEFAULT NULL,
    post_logout_redirect_uris varchar(1000) DEFAULT NULL,
    scopes varchar(1000) NOT NULL,
    client_settings varchar(2000) NOT NULL,
    token_settings varchar(2000) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization (
    id varchar(100) NOT NULL,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000) DEFAULT NULL,
    attributes text DEFAULT NULL,
    state varchar(500) DEFAULT NULL,
    authorization_code_value text DEFAULT NULL,
    authorization_code_issued_at timestamptz DEFAULT NULL,
    authorization_code_expires_at timestamptz DEFAULT NULL,
    authorization_code_metadata text DEFAULT NULL,
    access_token_value text DEFAULT NULL,
    access_token_issued_at timestamptz DEFAULT NULL,
    access_token_expires_at timestamptz DEFAULT NULL,
    access_token_metadata text DEFAULT NULL,
    access_token_type varchar(100) DEFAULT NULL,
    access_token_scopes varchar(1000) DEFAULT NULL,
    oidc_id_token_value text DEFAULT NULL,
    oidc_id_token_issued_at timestamptz DEFAULT NULL,
    oidc_id_token_expires_at timestamptz DEFAULT NULL,
    oidc_id_token_metadata text DEFAULT NULL,
    refresh_token_value text DEFAULT NULL,
    refresh_token_issued_at timestamptz DEFAULT NULL,
    refresh_token_expires_at timestamptz DEFAULT NULL,
    refresh_token_metadata text DEFAULT NULL,
    user_code_value text DEFAULT NULL,
    user_code_issued_at timestamptz DEFAULT NULL,
    user_code_expires_at timestamptz DEFAULT NULL,
    user_code_metadata text DEFAULT NULL,
    device_code_value text DEFAULT NULL,
    device_code_issued_at timestamptz DEFAULT NULL,
    device_code_expires_at timestamptz DEFAULT NULL,
    device_code_metadata text DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);


CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    actor_username VARCHAR(255) NOT NULL,
    occurred_at timestamptz NOT NULL,
    changes_json TEXT NOT NULL,
    CONSTRAINT uk_audit_logs_source_event_id UNIQUE (source_event_id)
);
CREATE INDEX ix_audit_logs_entity_time
    ON audit_logs (entity_type, occurred_at DESC, id DESC);

-- JobRunr 8.6.1 final PostgreSQL schema. Flyway remains the only schema owner;
-- JobRunr runs with jobrunr.database.skip-create=true.
CREATE TABLE jobrunr_jobs (
    id VARCHAR(36) PRIMARY KEY,
    version INTEGER NOT NULL,
    jobAsJson TEXT NOT NULL,
    jobSignature VARCHAR(512) NOT NULL,
    state VARCHAR(36) NOT NULL,
    createdAt TIMESTAMP NOT NULL,
    updatedAt TIMESTAMP NOT NULL,
    scheduledAt TIMESTAMP,
    recurringJobId VARCHAR(128)
);
CREATE INDEX jobrunr_state_idx ON jobrunr_jobs (state);
CREATE INDEX jobrunr_job_signature_idx ON jobrunr_jobs (jobSignature);
CREATE INDEX jobrunr_job_created_at_idx ON jobrunr_jobs (createdAt);
CREATE INDEX jobrunr_job_scheduled_at_idx ON jobrunr_jobs (scheduledAt);
CREATE INDEX jobrunr_job_rci_idx ON jobrunr_jobs (recurringJobId);
CREATE INDEX jobrunr_jobs_state_updated_idx ON jobrunr_jobs (state ASC, updatedAt ASC);

CREATE TABLE jobrunr_recurring_jobs (
    id VARCHAR(128) PRIMARY KEY,
    version INTEGER NOT NULL,
    jobAsJson TEXT NOT NULL,
    createdAt BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX jobrunr_recurring_job_created_at_idx ON jobrunr_recurring_jobs (createdAt);

CREATE TABLE jobrunr_backgroundjobservers (
    id VARCHAR(36) PRIMARY KEY,
    workerPoolSize INTEGER NOT NULL,
    pollIntervalInSeconds INTEGER NOT NULL,
    firstHeartbeat TIMESTAMP(6) NOT NULL,
    lastHeartbeat TIMESTAMP(6) NOT NULL,
    running INTEGER NOT NULL,
    systemTotalMemory BIGINT NOT NULL,
    systemFreeMemory BIGINT NOT NULL,
    systemCpuLoad NUMERIC(3, 2) NOT NULL,
    processMaxMemory BIGINT NOT NULL,
    processFreeMemory BIGINT NOT NULL,
    processAllocatedMemory BIGINT NOT NULL,
    processCpuLoad NUMERIC(3, 2) NOT NULL,
    deleteSucceededJobsAfter VARCHAR(32),
    permanentlyDeleteJobsAfter VARCHAR(32),
    name VARCHAR(128)
);
CREATE INDEX jobrunr_bgjobsrvrs_fsthb_idx ON jobrunr_backgroundjobservers (firstHeartbeat);
CREATE INDEX jobrunr_bgjobsrvrs_lsthb_idx ON jobrunr_backgroundjobservers (lastHeartbeat);

CREATE TABLE jobrunr_metadata (
    id VARCHAR(156) PRIMARY KEY,
    name VARCHAR(92) NOT NULL,
    owner VARCHAR(64) NOT NULL,
    value TEXT NOT NULL,
    createdAt TIMESTAMP NOT NULL,
    updatedAt TIMESTAMP NOT NULL
);
INSERT INTO jobrunr_metadata (id, name, owner, value, createdAt, updatedAt)
VALUES (
    'succeeded-jobs-counter-cluster',
    'succeeded-jobs-counter',
    'cluster',
    '0',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

CREATE VIEW jobrunr_jobs_stats AS
WITH job_stat_results AS (
    SELECT state, count(*) AS count
    FROM jobrunr_jobs
    GROUP BY state
)
SELECT
    coalesce((SELECT sum(count) FROM job_stat_results), 0) AS total,
    coalesce((SELECT sum(count) FROM job_stat_results WHERE state = 'AWAITING'), 0) AS awaiting,
    coalesce((SELECT sum(count) FROM job_stat_results WHERE state = 'SCHEDULED'), 0) AS scheduled,
    coalesce((SELECT sum(count) FROM job_stat_results WHERE state = 'ENQUEUED'), 0) AS enqueued,
    coalesce((SELECT sum(count) FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
    coalesce((SELECT sum(count) FROM job_stat_results WHERE state = 'PROCESSED'), 0) AS processed,
    coalesce((SELECT sum(count) FROM job_stat_results WHERE state = 'FAILED'), 0) AS failed,
    coalesce((SELECT sum(count) FROM job_stat_results WHERE state = 'SUCCEEDED'), 0) AS succeeded,
    coalesce(
        (
            SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
            FROM jobrunr_metadata
            WHERE id = 'succeeded-jobs-counter-cluster'
        ),
        0
    ) AS allTimeSucceeded,
    coalesce((SELECT sum(count) FROM job_stat_results WHERE state = 'DELETED'), 0) AS deleted,
    (SELECT count(*) FROM jobrunr_backgroundjobservers) AS nbrOfBackgroundJobServers,
    (SELECT count(*) FROM jobrunr_recurring_jobs) AS nbrOfRecurringJobs;
