ALTER TABLE users
    ALTER COLUMN organization_id DROP NOT NULL,
    ALTER COLUMN normalized_email DROP NOT NULL,
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_hash VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT ck_users_system_identity CHECK (
        (
            is_system
            AND id = '00000000-0000-0000-0000-000000000001'::UUID
            AND username = 'system'
            AND display_name = 'System'
            AND organization_id IS NULL
            AND normalized_email IS NULL
            AND status = 'ACTIVE'
            AND password_hash IS NOT NULL
            AND btrim(password_hash) <> ''
        )
        OR
        (
            NOT is_system
            AND username <> 'system'
            AND organization_id IS NOT NULL
            AND normalized_email IS NOT NULL
        )
    );

CREATE UNIQUE INDEX uk_users_single_system ON users (is_system) WHERE is_system;
