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

-- Spring Modulith / Namastack durable audit outbox.
CREATE TABLE IF NOT EXISTS outbox_record
(
    id             VARCHAR(255)             NOT NULL,
    status         VARCHAR(20)              NOT NULL,
    record_key     VARCHAR(255)             NOT NULL,
    record_type    VARCHAR(255)             NOT NULL,
    payload        TEXT                     NOT NULL,
    context        TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at   TIMESTAMP WITH TIME ZONE,
    failure_count  INT                      NOT NULL,
    failure_reason VARCHAR(1000),
    next_retry_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    partition_no   INTEGER                  NOT NULL,
    handler_id     VARCHAR(1000)            NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS outbox_instance
(
    instance_id    VARCHAR(255) PRIMARY KEY,
    hostname       VARCHAR(255)             NOT NULL,
    port           INTEGER                  NOT NULL,
    status         VARCHAR(50)              NOT NULL,
    started_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox_partition
(
    partition_number INTEGER PRIMARY KEY,
    instance_id      VARCHAR(255),
    version          BIGINT                   NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_record_record_key_created ON outbox_record (record_key, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_record_partition_status_retry ON outbox_record (partition_no, status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_outbox_record_status_retry ON outbox_record (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_outbox_record_status ON outbox_record (status);
CREATE INDEX IF NOT EXISTS idx_outbox_record_record_key_completed_created ON outbox_record (record_key, completed_at, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_instance_status_heartbeat ON outbox_instance (status, last_heartbeat);
CREATE INDEX IF NOT EXISTS idx_outbox_instance_last_heartbeat ON outbox_instance (last_heartbeat);
CREATE INDEX IF NOT EXISTS idx_outbox_instance_status ON outbox_instance (status);
CREATE INDEX IF NOT EXISTS idx_outbox_partition_instance_id ON outbox_partition (instance_id);

CREATE TABLE audit_logs
(
    id                 UUID                     NOT NULL,
    source_event_id    UUID                     NOT NULL,
    entity_type        VARCHAR(100)             NOT NULL,
    entity_id          UUID                     NOT NULL,
    actor_id           VARCHAR(255)             NOT NULL,
    actor_display_name VARCHAR(255)             NOT NULL,
    occurred_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT uq_audit_logs_source_event UNIQUE (source_event_id)
);

CREATE TABLE audit_log_changes
(
    id           UUID         NOT NULL,
    audit_log_id UUID         NOT NULL,
    position     INTEGER      NOT NULL,
    field_name   VARCHAR(255) NOT NULL,
    sensitive    BOOLEAN      NOT NULL,
    before_value JSONB,
    after_value  JSONB,
    CONSTRAINT pk_audit_log_changes PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_changes_log FOREIGN KEY (audit_log_id) REFERENCES audit_logs (id) ON DELETE CASCADE,
    CONSTRAINT uq_audit_log_changes_position UNIQUE (audit_log_id, position),
    CONSTRAINT ck_audit_log_changes_sensitive_values
        CHECK (NOT sensitive OR (before_value IS NULL AND after_value IS NULL))
);

CREATE INDEX idx_audit_logs_entity_type_occurred ON audit_logs (entity_type, occurred_at DESC, id DESC);
