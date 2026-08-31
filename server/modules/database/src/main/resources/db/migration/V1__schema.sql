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
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000)
);
CREATE INDEX ix_groups_organization_id ON groups(organization_id);

CREATE TABLE group_members (
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX ix_group_members_user_id ON group_members(user_id);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000)
);
CREATE INDEX ix_roles_organization_id ON roles(organization_id);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_key)
);

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
    IF group_organization IS DISTINCT FROM user_organization THEN
        RAISE EXCEPTION 'Group members must belong to the Group organization' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_group_members_same_organization BEFORE INSERT OR UPDATE ON group_members
FOR EACH ROW EXECUTE FUNCTION taskmigo_enforce_group_member_organization();

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
