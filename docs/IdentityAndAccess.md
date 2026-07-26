# Identity and Access

## 1. Purpose

The identity capability inside the `access` module persists Console users and authorities,
provisions the bootstrap administrator, supplies Spring Security's `UserDetailsService`, and
enables form login.

## 2. Audience

This document is for operators configuring the first administrator and developers extending
identity or login behavior.

## 3. Workflows

At startup, Console encodes the configured bootstrap password. A missing user is created and an
existing user is updated and re-enabled. `ROLE_USER` and `ROLE_ADMIN` are granted while unrelated
existing roles are preserved. Form login loads active or disabled users from PostgreSQL.

## 4. Configuration

- `BOOTSTRAP_USERNAME` maps to `app.security.bootstrap.username` and defaults to `developer`.
- `BOOTSTRAP_PASSWORD` maps to `app.security.bootstrap.password` and has no source-code default.

Passwords are encoded through the application `PasswordEncoder`. The local Compose wrapper removes
conflicting exported variables so its documented sample credentials are deterministic.

## 5. API contract

Identity currently has no REST management API. Future identity endpoints must be implemented in
`access.internal.web.identity`.

## 6. Security

Constructor injection is used throughout. Disabled users are returned as disabled `UserDetails`,
allowing Spring Security to reject authentication consistently. Form login is separate from
bearer-token validation.

## 7. Persistence

Flyway V2 creates `app_user`, `app_authority`, and the signing-key table. JPA owns user and
authority access; startup code does not contain schema SQL.

## 8. Failure and edge cases

Startup fails when password encoding or user persistence fails. Re-provisioning is idempotent,
resets the configured password, re-enables the user, and never removes additional roles. Docker
Compose gives exported shell variables precedence over an env file; use the provided wrapper for
the deterministic development sample.

## 9. Test scenarios

Tests cover create, update, re-enable, role preservation, active/disabled/missing user lookup,
password matching, password encoder failure and repository failure. PostgreSQL integration verifies
Flyway and the bootstrap result.
