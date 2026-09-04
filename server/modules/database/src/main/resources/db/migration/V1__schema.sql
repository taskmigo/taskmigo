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
    name VARCHAR(200) NOT NULL,
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
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role_id ON user_roles(role_id);

CREATE TABLE role_hierarchy (
    parent_role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    child_role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_role_id, child_role_id)
);
CREATE INDEX ix_role_hierarchy_child_role_id ON role_hierarchy(child_role_id);

CREATE TABLE group_hierarchy (
    parent_group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    child_group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_group_id, child_group_id)
);
CREATE INDEX ix_group_hierarchy_child_group_id ON group_hierarchy(child_group_id);

CREATE TABLE group_roles (
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, role_id)
);
CREATE INDEX ix_group_roles_role_id ON group_roles(role_id);

CREATE TABLE statements (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    effect VARCHAR(16) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    method VARCHAR(16) NOT NULL,
    path VARCHAR(2000) NOT NULL,
    policy TEXT NOT NULL,
    CONSTRAINT uk_statements_name UNIQUE (name),
    CONSTRAINT ck_statements_effect CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT ck_statements_scope CHECK (scope IN ('OBJECT', 'REQUEST')),
    CONSTRAINT ck_statements_policy_nonblank CHECK (btrim(policy) <> '')
);

CREATE TABLE role_statements (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    statement_id UUID NOT NULL REFERENCES statements(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, statement_id)
);
CREATE INDEX ix_role_statements_statement_id ON role_statements(statement_id);

CREATE TABLE user_statements (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    statement_id UUID NOT NULL REFERENCES statements(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, statement_id)
);
CREATE INDEX ix_user_statements_statement_id ON user_statements(statement_id);

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
