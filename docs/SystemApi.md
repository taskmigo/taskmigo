# System API

## 1. Purpose

The system feature contains three sample endpoints demonstrating public, authenticated and
administrator-scoped API behavior.

## 2. Audience

This document is for API consumers and developers validating Console security configuration.

## 3. Workflows

Call the public endpoint without a token. Obtain a JWT with the required scope before calling the
authenticated examples.

## 4. Configuration

The access module protects `/api/**` and explicitly permits `/api/public`. No endpoint-specific
client ID is configured.

## 5. API contract

| Method | Path | Access |
|---|---|---|
| `GET` | `/api/public` | Public |
| `GET` | `/api/me` | JWT with `api.read` |
| `GET` | `/api/admin` | JWT with `api.admin` |

## 6. Security

The default for `/api/**` is authenticated. `@PreAuthorize` enforces feature-specific scopes on
`/api/me` and `/api/admin`.

## 7. Persistence

These sample endpoints do not persist business data.

## 8. Failure and edge cases

Missing or expired bearer tokens return `401`. Authenticated tokens without the endpoint's scope
return `403`.

## 9. Test scenarios

Security integration tests cover valid JWT access, expired JWT rejection, `api.admin` access, and
insufficient-scope rejection.

The `system` module is not a destination for future business APIs. New domains such as tasks,
projects or notifications must own new Spring Modulith modules and controllers.
