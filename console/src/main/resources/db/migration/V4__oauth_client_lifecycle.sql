DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM oauth2_authorization oauth_authorization
        LEFT JOIN oauth2_registered_client client
            ON client.id = oauth_authorization.registered_client_id
        WHERE client.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot add OAuth client lifecycle constraints: orphan authorizations exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM oauth2_authorization_consent consent
        LEFT JOIN oauth2_registered_client client
            ON client.id = consent.registered_client_id
        WHERE client.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot add OAuth client lifecycle constraints: orphan consents exist';
    END IF;
END
$$;

ALTER TABLE oauth2_authorization
    ADD CONSTRAINT oauth2_authorization_registered_client_fk
    FOREIGN KEY (registered_client_id)
    REFERENCES oauth2_registered_client(id)
    ON DELETE CASCADE;

ALTER TABLE oauth2_authorization_consent
    ADD CONSTRAINT oauth2_consent_registered_client_fk
    FOREIGN KEY (registered_client_id)
    REFERENCES oauth2_registered_client(id)
    ON DELETE CASCADE;

CREATE TABLE oauth2_registered_client_state (
    registered_client_id varchar(100) PRIMARY KEY
        REFERENCES oauth2_registered_client(id) ON DELETE CASCADE,
    active boolean NOT NULL,
    manual_override boolean NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

INSERT INTO oauth2_registered_client_state (
    registered_client_id,
    active,
    manual_override,
    updated_at
)
SELECT id, true, false, CURRENT_TIMESTAMP
FROM oauth2_registered_client;

CREATE TABLE oauth2_client_deletion_confirmation (
    id uuid PRIMARY KEY,
    registered_client_id varchar(100) NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    requested_by varchar(200) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone DEFAULT NULL
);

CREATE INDEX oauth2_client_deletion_confirmation_expiry_idx
    ON oauth2_client_deletion_confirmation (expires_at);
