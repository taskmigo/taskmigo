# JWT Security

## 1. Purpose

Console issues and validates self-contained JWT access tokens while preserving the behavior that
client lifecycle changes do not revoke tokens already issued.

## 2. Audience

This document is for API developers, security reviewers and operators responsible for signing-key
persistence.

## 3. Workflows

An active client authenticates at the Authorization Server and receives a signed JWT. The Resource
Server validates its signature, timestamps and authorities locally. Disabling or deleting the
client blocks subsequent authorize, token and refresh operations, but an existing JWT remains valid
until `exp`.

## 4. Configuration

The issuer is configured with `spring.security.oauth2.authorizationserver.issuer`. Client token
settings use the standard Spring client configuration. Access-token format remains self-contained
JWT.

## 5. API contract

Bearer tokens are sent through `Authorization: Bearer <token>`. `/api/**` requires authentication
unless explicitly public. Endpoint-specific scope requirements are documented by their feature.

## 6. Security

The application uses `.jwt()`. It does not use opaque tokens, introspection, a denylist, or a
database client-state lookup during API validation. An expired or invalidly signed JWT returns
`401`; insufficient scope returns `403`.

## 7. Persistence

RSA private JWKs are generated once and stored in `oauth2_signing_key`. Active keys are loaded for
signing and verification. A database uniqueness rule makes concurrent initialization safe.
Production deployments should replace database private-key storage with KMS or HSM integration.

## 8. Failure and edge cases

Invalid persisted JWKs and persistence/cryptographic failures fail explicitly. Re-enabling a client
can allow an unexpired refresh token to be used again; permanent deletion removes refresh-token
authorization records.

## 9. Test scenarios

Tests cover existing, missing and concurrent key initialization; selector match/no-match/multiple;
invalid JWK; persistence and cryptographic failures; active issuance; disable/delete behavior;
retained JWT validity; rejected new token requests; expired JWT `401`; and scope authorization.
