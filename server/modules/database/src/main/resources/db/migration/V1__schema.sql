CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    organization_key VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    CONSTRAINT uk_organizations_key UNIQUE (organization_key),
    CONSTRAINT ck_organizations_key_not_blank CHECK (btrim(organization_key) <> ''),
    CONSTRAINT ck_organizations_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    username VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    password_hash VARCHAR(255),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_users_names CHECK (btrim(first_name) <> '' AND btrim(last_name) <> '')
);
CREATE INDEX ix_users_organization_id ON users(organization_id);

CREATE TABLE user_emails (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    normalized_email VARCHAR(320) NOT NULL,
    PRIMARY KEY (user_id, normalized_email),
    CONSTRAINT uk_user_emails_normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_user_emails_normalized_email CHECK (normalized_email = lower(btrim(normalized_email)))
);

CREATE TABLE groups (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    group_key VARCHAR(128) NOT NULL,
    authorization_origin VARCHAR(16) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    CONSTRAINT ck_groups_key_not_blank CHECK (btrim(group_key) <> ''),
    CONSTRAINT ck_groups_authorization_origin CHECK (authorization_origin IN ('SYSTEM', 'CUSTOM')),
    CONSTRAINT ck_groups_authorization_scope CHECK (
        (authorization_origin = 'SYSTEM' AND organization_id IS NULL)
        OR (authorization_origin = 'CUSTOM' AND organization_id IS NOT NULL)
    )
);
CREATE INDEX ix_groups_organization_id ON groups(organization_id);
CREATE UNIQUE INDEX uk_groups_system_key ON groups(group_key) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uk_groups_organization_key ON groups(organization_id, group_key) WHERE organization_id IS NOT NULL;

CREATE TABLE group_members (
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX ix_group_members_user_id ON group_members(user_id);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    role_key VARCHAR(128) NOT NULL,
    authorization_origin VARCHAR(16) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    CONSTRAINT ck_roles_key_not_blank CHECK (btrim(role_key) <> ''),
    CONSTRAINT ck_roles_authorization_origin CHECK (authorization_origin IN ('SYSTEM', 'CUSTOM')),
    CONSTRAINT ck_roles_authorization_scope CHECK (
        (authorization_origin = 'SYSTEM' AND organization_id IS NULL)
        OR (authorization_origin = 'CUSTOM' AND organization_id IS NOT NULL)
    )
);
CREATE INDEX ix_roles_organization_id ON roles(organization_id);
CREATE UNIQUE INDEX uk_roles_system_key ON roles(role_key) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uk_roles_organization_key ON roles(organization_id, role_key) WHERE organization_id IS NOT NULL;

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_key)
);

CREATE TABLE authorization_statements (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    statement_key VARCHAR(128) NOT NULL,
    name VARCHAR(200),
    description VARCHAR(1000),
    match_method VARCHAR(16) NOT NULL,
    match_path VARCHAR(512) NOT NULL,
    target VARCHAR(16) NOT NULL,
    effect VARCHAR(16),
    condition_expression VARCHAR(1024),
    origin VARCHAR(16) NOT NULL,
    CONSTRAINT ck_authorization_statements_key_not_blank CHECK (btrim(statement_key) <> ''),
    CONSTRAINT ck_authorization_statements_method_upper CHECK (match_method = upper(match_method)),
    CONSTRAINT ck_authorization_statements_target CHECK (target IN ('REQUEST', 'OBJECT')),
    CONSTRAINT ck_authorization_statements_effect CHECK (effect IS NULL OR effect IN ('ALLOW', 'DENY')),
    CONSTRAINT ck_authorization_statements_origin CHECK (origin IN ('SYSTEM', 'CUSTOM')),
    CONSTRAINT ck_authorization_statements_scope CHECK (
        (origin = 'SYSTEM' AND organization_id IS NULL)
        OR (origin = 'CUSTOM' AND organization_id IS NOT NULL)
    ),
    CONSTRAINT ck_authorization_request_effect CHECK (target <> 'REQUEST' OR effect IS NOT NULL)
);
CREATE INDEX ix_authorization_statements_scope_method ON authorization_statements(organization_id, match_method);
CREATE INDEX ix_authorization_statements_system_method ON authorization_statements(match_method) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uk_authorization_statements_system_key
    ON authorization_statements(statement_key) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uk_authorization_statements_organization_key
    ON authorization_statements(organization_id, statement_key) WHERE organization_id IS NOT NULL;

CREATE TABLE authorization_statement_field_rules (
    id UUID PRIMARY KEY,
    statement_id UUID NOT NULL REFERENCES authorization_statements(id) ON DELETE CASCADE,
    effect VARCHAR(16) NOT NULL,
    field_names TEXT NOT NULL,
    condition_expression VARCHAR(1024),
    CONSTRAINT ck_authorization_field_rules_effect CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT ck_authorization_field_rules_names CHECK (btrim(field_names) <> '')
);
CREATE INDEX ix_authorization_statement_field_rules_statement ON authorization_statement_field_rules(statement_id);

CREATE TABLE authorization_role_statements (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    statement_id UUID NOT NULL REFERENCES authorization_statements(id) ON DELETE CASCADE,
    CONSTRAINT uk_authorization_role_statement UNIQUE (role_id, statement_id)
);
CREATE INDEX ix_authorization_role_statements_statement ON authorization_role_statements(statement_id);

CREATE TABLE authorization_role_inheritance (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    included_role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_authorization_role_inheritance UNIQUE (role_id, included_role_id),
    CONSTRAINT ck_authorization_role_not_self CHECK (role_id <> included_role_id)
);
CREATE INDEX ix_authorization_role_inheritance_included ON authorization_role_inheritance(included_role_id);

CREATE TABLE authorization_group_statements (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    statement_id UUID NOT NULL REFERENCES authorization_statements(id) ON DELETE CASCADE,
    CONSTRAINT uk_authorization_group_statement UNIQUE (group_id, statement_id)
);
CREATE INDEX ix_authorization_group_statements_statement ON authorization_group_statements(statement_id);

CREATE TABLE authorization_group_inheritance (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    included_group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT uk_authorization_group_inheritance UNIQUE (group_id, included_group_id),
    CONSTRAINT ck_authorization_group_not_self CHECK (group_id <> included_group_id)
);
CREATE INDEX ix_authorization_group_inheritance_included ON authorization_group_inheritance(included_group_id);

CREATE TABLE authorization_user_statements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    statement_id UUID NOT NULL REFERENCES authorization_statements(id) ON DELETE CASCADE,
    CONSTRAINT uk_authorization_user_statement UNIQUE (user_id, statement_id)
);
CREATE INDEX ix_authorization_user_statements_statement ON authorization_user_statements(statement_id);

CREATE TABLE authorization_user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_authorization_user_role UNIQUE (user_id, role_id)
);
CREATE INDEX ix_authorization_user_roles_role ON authorization_user_roles(role_id);

CREATE TABLE acl_policies (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    definition JSONB NOT NULL,
    CONSTRAINT uk_acl_policies_organization_name UNIQUE (organization_id, name),
    CONSTRAINT ck_acl_policies_kind CHECK (kind IN ('acl/request', 'acl/response')),
    CONSTRAINT ck_acl_policies_name_not_blank CHECK (btrim(name) <> '')
);
CREATE INDEX ix_acl_policies_organization_id ON acl_policies(organization_id);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    project_key VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(16) NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_projects_organization_key UNIQUE (organization_id, project_key),
    CONSTRAINT ck_projects_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_projects_archive_state CHECK (
        (status = 'ACTIVE' AND archived_at IS NULL)
        OR (status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);
CREATE INDEX ix_projects_organization_id ON projects(organization_id);
CREATE INDEX ix_projects_archived_at ON projects(archived_at) WHERE status = 'ARCHIVED';

CREATE TABLE project_members (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    principal_type VARCHAR(16) NOT NULL,
    principal_id UUID NOT NULL,
    CONSTRAINT uk_project_members_principal UNIQUE (project_id, principal_type, principal_id),
    CONSTRAINT ck_project_members_principal_type CHECK (principal_type IN ('USER', 'GROUP'))
);
CREATE INDEX ix_project_members_project_id ON project_members(project_id);
CREATE INDEX ix_project_members_principal ON project_members(principal_type, principal_id);

CREATE TABLE project_member_roles (
    project_member_id UUID NOT NULL REFERENCES project_members(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (project_member_id, role_id)
);
CREATE INDEX ix_project_member_roles_role_id ON project_member_roles(role_id);

CREATE TABLE project_history (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
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

CREATE FUNCTION taskmigo_enforce_group_member_organization() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE group_organization UUID; user_organization UUID;
BEGIN
    SELECT organization_id INTO group_organization FROM groups WHERE id = NEW.group_id;
    SELECT organization_id INTO user_organization FROM users WHERE id = NEW.user_id;
    IF group_organization IS NOT NULL AND group_organization IS DISTINCT FROM user_organization THEN
        RAISE EXCEPTION 'Group members must belong to the Group organization' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_group_members_same_organization BEFORE INSERT OR UPDATE ON group_members
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_group_member_organization();

CREATE FUNCTION taskmigo_enforce_authorization_role_statement_scope() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE owner_organization UUID; target_organization UUID;
BEGIN
    SELECT organization_id INTO owner_organization FROM roles WHERE id = NEW.role_id;
    SELECT organization_id INTO target_organization FROM authorization_statements WHERE id = NEW.statement_id;
    IF target_organization IS NOT NULL AND target_organization IS DISTINCT FROM owner_organization THEN
        RAISE EXCEPTION 'Role and Statement authorization scopes are incompatible' USING ERRCODE = '23514';
    END IF;
    IF owner_organization IS NULL AND target_organization IS NOT NULL THEN
        RAISE EXCEPTION 'System Role cannot include custom Statement' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_authorization_role_statement_scope BEFORE INSERT OR UPDATE ON authorization_role_statements
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_authorization_role_statement_scope();

CREATE FUNCTION taskmigo_enforce_authorization_role_inheritance_scope() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE owner_organization UUID; target_organization UUID;
BEGIN
    SELECT organization_id INTO owner_organization FROM roles WHERE id = NEW.role_id;
    SELECT organization_id INTO target_organization FROM roles WHERE id = NEW.included_role_id;
    IF target_organization IS NOT NULL AND target_organization IS DISTINCT FROM owner_organization THEN
        RAISE EXCEPTION 'Role inheritance scopes are incompatible' USING ERRCODE = '23514';
    END IF;
    IF owner_organization IS NULL AND target_organization IS NOT NULL THEN
        RAISE EXCEPTION 'System Role cannot include custom Role' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_authorization_role_inheritance_scope BEFORE INSERT OR UPDATE ON authorization_role_inheritance
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_authorization_role_inheritance_scope();

CREATE FUNCTION taskmigo_enforce_authorization_group_statement_scope() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE owner_organization UUID; target_organization UUID;
BEGIN
    SELECT organization_id INTO owner_organization FROM groups WHERE id = NEW.group_id;
    SELECT organization_id INTO target_organization FROM authorization_statements WHERE id = NEW.statement_id;
    IF target_organization IS NOT NULL AND target_organization IS DISTINCT FROM owner_organization THEN
        RAISE EXCEPTION 'Group and Statement authorization scopes are incompatible' USING ERRCODE = '23514';
    END IF;
    IF owner_organization IS NULL AND target_organization IS NOT NULL THEN
        RAISE EXCEPTION 'System Group cannot include custom Statement' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_authorization_group_statement_scope BEFORE INSERT OR UPDATE ON authorization_group_statements
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_authorization_group_statement_scope();

CREATE FUNCTION taskmigo_enforce_authorization_group_inheritance_scope() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE owner_organization UUID; target_organization UUID;
BEGIN
    SELECT organization_id INTO owner_organization FROM groups WHERE id = NEW.group_id;
    SELECT organization_id INTO target_organization FROM groups WHERE id = NEW.included_group_id;
    IF target_organization IS NOT NULL AND target_organization IS DISTINCT FROM owner_organization THEN
        RAISE EXCEPTION 'Group inheritance scopes are incompatible' USING ERRCODE = '23514';
    END IF;
    IF owner_organization IS NULL AND target_organization IS NOT NULL THEN
        RAISE EXCEPTION 'System Group cannot include custom Group' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_authorization_group_inheritance_scope BEFORE INSERT OR UPDATE ON authorization_group_inheritance
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_authorization_group_inheritance_scope();

CREATE FUNCTION taskmigo_enforce_authorization_user_statement_scope() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE user_organization UUID; target_organization UUID;
BEGIN
    SELECT organization_id INTO user_organization FROM users WHERE id = NEW.user_id;
    SELECT organization_id INTO target_organization FROM authorization_statements WHERE id = NEW.statement_id;
    IF target_organization IS NOT NULL AND target_organization IS DISTINCT FROM user_organization THEN
        RAISE EXCEPTION 'User and Statement authorization scopes are incompatible' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_authorization_user_statement_scope BEFORE INSERT OR UPDATE ON authorization_user_statements
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_authorization_user_statement_scope();

CREATE FUNCTION taskmigo_enforce_authorization_user_role_scope() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE user_organization UUID; target_organization UUID;
BEGIN
    SELECT organization_id INTO user_organization FROM users WHERE id = NEW.user_id;
    SELECT organization_id INTO target_organization FROM roles WHERE id = NEW.role_id;
    IF target_organization IS NOT NULL AND target_organization IS DISTINCT FROM user_organization THEN
        RAISE EXCEPTION 'User and Role authorization scopes are incompatible' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_authorization_user_role_scope BEFORE INSERT OR UPDATE ON authorization_user_roles
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_authorization_user_role_scope();

CREATE FUNCTION taskmigo_reject_authorization_role_cycle() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        WITH RECURSIVE reachable(role_id) AS (
            SELECT NEW.included_role_id
            UNION
            SELECT inheritance.included_role_id
            FROM authorization_role_inheritance inheritance
            JOIN reachable ON inheritance.role_id = reachable.role_id
        )
        SELECT 1 FROM reachable WHERE role_id = NEW.role_id
    ) THEN
        RAISE EXCEPTION 'Role inheritance cycle' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_authorization_role_cycle BEFORE INSERT OR UPDATE ON authorization_role_inheritance
FOR EACH ROW EXECUTE FUNCTION taskmigo_reject_authorization_role_cycle();

CREATE FUNCTION taskmigo_reject_authorization_group_cycle() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        WITH RECURSIVE reachable(group_id) AS (
            SELECT NEW.included_group_id
            UNION
            SELECT inheritance.included_group_id
            FROM authorization_group_inheritance inheritance
            JOIN reachable ON inheritance.group_id = reachable.group_id
        )
        SELECT 1 FROM reachable WHERE group_id = NEW.group_id
    ) THEN
        RAISE EXCEPTION 'Group inheritance cycle' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_authorization_group_cycle BEFORE INSERT OR UPDATE ON authorization_group_inheritance
FOR EACH ROW EXECUTE FUNCTION taskmigo_reject_authorization_group_cycle();

CREATE FUNCTION taskmigo_enforce_archived_project_read_only() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'ARCHIVED' THEN
        RAISE EXCEPTION 'Archived Projects are read-only' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_projects_archived_read_only BEFORE UPDATE ON projects
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_archived_project_read_only();

CREATE FUNCTION taskmigo_enforce_project_member_principal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.principal_type = 'USER' AND NOT EXISTS (SELECT 1 FROM users WHERE id = NEW.principal_id) THEN
        RAISE EXCEPTION 'Project USER principal does not exist' USING ERRCODE = '23503';
    ELSIF NEW.principal_type = 'GROUP' AND NOT EXISTS (SELECT 1 FROM groups WHERE id = NEW.principal_id) THEN
        RAISE EXCEPTION 'Project GROUP principal does not exist' USING ERRCODE = '23503';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_project_members_principal_exists BEFORE INSERT OR UPDATE OF principal_type, principal_id ON project_members
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_project_member_principal();

CREATE FUNCTION taskmigo_enforce_project_member_role_organization() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE project_organization UUID; role_organization UUID;
BEGIN
    SELECT p.organization_id INTO project_organization FROM project_members pm JOIN projects p ON p.id = pm.project_id
     WHERE pm.id = NEW.project_member_id;
    SELECT organization_id INTO role_organization FROM roles WHERE id = NEW.role_id;
    IF project_organization IS DISTINCT FROM role_organization THEN
        RAISE EXCEPTION 'Project Member Roles must belong to the Project organization' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_project_member_roles_same_organization BEFORE INSERT OR UPDATE ON project_member_roles
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_project_member_role_organization();

CREATE FUNCTION taskmigo_enforce_active_project_member_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE target_project UUID; target_status VARCHAR(16);
BEGIN
    target_project := COALESCE(NEW.project_id, OLD.project_id);
    SELECT status INTO target_status FROM projects WHERE id = target_project;
    IF target_status = 'ARCHIVED' THEN
        RAISE EXCEPTION 'Archived Projects are read-only' USING ERRCODE = '23514';
    END IF;
    RETURN COALESCE(NEW, OLD);
END; $$;
CREATE TRIGGER trg_project_members_active_project BEFORE INSERT OR UPDATE OR DELETE ON project_members
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_active_project_member_mutation();

CREATE FUNCTION taskmigo_enforce_active_project_role_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE target_member UUID; target_status VARCHAR(16);
BEGIN
    target_member := COALESCE(NEW.project_member_id, OLD.project_member_id);
    SELECT p.status INTO target_status FROM project_members pm JOIN projects p ON p.id = pm.project_id WHERE pm.id = target_member;
    IF target_status = 'ARCHIVED' THEN
        RAISE EXCEPTION 'Archived Projects are read-only' USING ERRCODE = '23514';
    END IF;
    RETURN COALESCE(NEW, OLD);
END; $$;
CREATE TRIGGER trg_project_member_roles_active_project BEFORE INSERT OR UPDATE OR DELETE ON project_member_roles
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_active_project_role_mutation();

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
