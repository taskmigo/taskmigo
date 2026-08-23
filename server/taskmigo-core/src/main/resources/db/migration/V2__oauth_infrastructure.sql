CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMPTZ,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL,
    CONSTRAINT uk_oauth2_registered_client_client_id UNIQUE (client_id)
);

CREATE TABLE oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY,
    registered_client_id VARCHAR(100) NOT NULL REFERENCES oauth2_registered_client(id),
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMPTZ,
    authorization_code_expires_at TIMESTAMPTZ,
    authorization_code_metadata TEXT,
    access_token_value TEXT,
    access_token_issued_at TIMESTAMPTZ,
    access_token_expires_at TIMESTAMPTZ,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMPTZ,
    oidc_id_token_expires_at TIMESTAMPTZ,
    oidc_id_token_metadata TEXT,
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMPTZ,
    refresh_token_expires_at TIMESTAMPTZ,
    refresh_token_metadata TEXT,
    user_code_value TEXT,
    user_code_issued_at TIMESTAMPTZ,
    user_code_expires_at TIMESTAMPTZ,
    user_code_metadata TEXT,
    device_code_value TEXT,
    device_code_issued_at TIMESTAMPTZ,
    device_code_expires_at TIMESTAMPTZ,
    device_code_metadata TEXT
);
CREATE INDEX ix_oauth2_authorization_registered_client_id ON oauth2_authorization(registered_client_id);
CREATE INDEX ix_oauth2_authorization_principal_name ON oauth2_authorization(principal_name);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL REFERENCES oauth2_registered_client(id),
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE oauth_client_management (
    registered_client_id VARCHAR(100) PRIMARY KEY REFERENCES oauth2_registered_client(id),
    registration_key VARCHAR(100) NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    trust_level VARCHAR(16) NOT NULL,
    managed_by VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_oauth_client_management_registration_key UNIQUE (registration_key),
    CONSTRAINT ck_oauth_client_management_client_type CHECK (client_type IN ('PUBLIC', 'CONFIDENTIAL')),
    CONSTRAINT ck_oauth_client_management_trust_level CHECK (trust_level IN ('FIRST_PARTY', 'THIRD_PARTY')),
    CONSTRAINT ck_oauth_client_management_managed_by CHECK (managed_by IN ('SYSTEM', 'ADMIN'))
);

CREATE TABLE oauth_service_principal (
    registered_client_id VARCHAR(100) PRIMARY KEY REFERENCES oauth2_registered_client(id),
    enabled BOOLEAN NOT NULL
);

CREATE TABLE oauth_service_principal_permissions (
    registered_client_id VARCHAR(100) NOT NULL REFERENCES oauth_service_principal(registered_client_id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (registered_client_id, permission_key)
);
