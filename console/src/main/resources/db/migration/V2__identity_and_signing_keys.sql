CREATE TABLE app_user (
    username varchar(100) PRIMARY KEY,
    password varchar(500) NOT NULL,
    enabled boolean NOT NULL
);

CREATE TABLE app_authority (
    username varchar(100) NOT NULL REFERENCES app_user(username) ON DELETE CASCADE,
    authority varchar(100) NOT NULL,
    PRIMARY KEY (username, authority)
);

CREATE TABLE oauth2_signing_key (
    key_id varchar(100) PRIMARY KEY,
    jwk text NOT NULL,
    active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX oauth2_one_active_signing_key
    ON oauth2_signing_key (active) WHERE active = true;
