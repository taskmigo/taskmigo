# OAuth Client Management

## 1. Purpose

The OAuth capability inside the `access` module synchronizes multiple Spring-configured OAuth
clients, preserves lifecycle state, and provides explicit administration APIs for enablement and
permanent deletion.

## 2. Audience

This document is for operators managing client configuration, API consumers with `api.admin`, and
developers changing OAuth client lifecycle behavior.

## 3. Workflows

Startup requires at least one configured client. Each configured client is upserted and active. A
returning client becomes configuration-managed again. An absent configuration-managed client is
disabled; an absent manually enabled client remains active. Synchronization never deletes data.

Permanent deletion is two-step: request a one-time confirmation, then send it in the delete
request. A configured client cannot be deleted. Re-adding a deleted client to configuration creates
it again.

## 4. Configuration

Use Spring Boot's standard map:

```text
spring.security.oauth2.authorizationserver.client.<registration-id>
```

All registrations may be supplied through normal Spring property sources, including
`SPRING_APPLICATION_JSON`. There is no fixed client ID or fixed number of clients. Empty or invalid
configuration fails startup and transaction rollback prevents partial synchronization.

## 5. API contract

All endpoints require `SCOPE_api.admin`.

| Method | Path | Result |
|---|---|---|
| `GET` | `/api/admin/oauth2-clients` | Client state list; secrets are never returned |
| `POST` | `/api/admin/oauth2-clients/{clientId}/enable` | Activates the client and stores a manual override |
| `POST` | `/api/admin/oauth2-clients/{clientId}/deletion-confirmations` | `201`; returns a one-time token with a five-minute expiry |
| `DELETE` | `/api/admin/oauth2-clients/{clientId}` | Requires `X-Deletion-Confirmation`; returns `204` |

Errors use Problem Details. Missing clients return `404` with `oauth_client_not_found`. Configured
clients and invalid confirmations return `409` with `client_still_configured` and
`invalid_deletion_confirmation`.

## 6. Security

Only active clients are visible to Spring Authorization Server authentication. Management and
provisioning can still read inactive clients. Confirmation tokens are generated with a secure
random source, returned once, and stored only as SHA-256 hashes.

## 7. Persistence

Flyway V4 adds `oauth2_registered_client_state` and
`oauth2_client_deletion_confirmation`. State, authorization and consent records cascade on
permanent client deletion. Confirmation history is retained so reused tokens remain invalid.

## 8. Failure and edge cases

Expired, malformed, reused, wrong-client and wrong-requester confirmations are rejected. A
replacement confirmation invalidates the prior one. Disable never auto-deletes. Secret upgrades
performed by Spring do not create a manual override.

## 9. Test scenarios

Tests cover empty/single/multiple configuration, insert/update/disable/re-entry, persistent manual
enable, public and confidential clients, invalid settings, rollback, secret-free listing,
confirmation lifecycle, deletion cascades and configuration re-add.
