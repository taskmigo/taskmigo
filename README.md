# Taskmigo

Taskmigo is a modern Redmine alternative. This service implements the current v0 resource-management contract from `taskmigo/docs`.

## Stack

- Java 26
- Gradle 9.7.1
- Spring Boot 4.1.0
- Spring Security Authorization Server 7.1.0, managed by Spring Boot
- PostgreSQL 18.4
- Flyway 12.4.0, managed by Spring Boot
- Testcontainers 2.0.5, managed by Spring Boot

Spring Boot's dependency management is the source of truth for compatible Spring and third-party dependency versions.
The Gradle build uses a Java 26 toolchain, so compilation and tests consistently target Java 26.

## Database lifecycle

Flyway is the only schema owner. `src/main/resources/db/migration` contains the database definition and invariants.
Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate`; it never creates or updates tables.

The initial migration enforces database uniqueness plus cross-table invariants such as same-Organization Group members,
valid polymorphic Project principals, Project-owned Role assignments, and archived Project immutability.

## Authentication and authorization

Spring Security Authorization Server exposes OAuth 2.1 endpoints. The bootstrap client uses `client_credentials` and the
`taskmigo.api` scope. Domain Users remain separate from authentication identities, matching the v0 resource-model contract.

Set production credentials through environment variables:

```bash
export TASKMIGO_OAUTH_CLIENT_SECRET='replace-me'
```

Get a local token:

```bash
curl -u "taskmigo-cli:${TASKMIGO_OAUTH_CLIENT_SECRET:-taskmigo-dev-secret}" \
  -d grant_type=client_credentials \
  -d scope=taskmigo.api \
  http://localhost:8080/oauth2/token
```

Use the returned bearer token for `/api/v0/**`.

## Run

```bash
docker compose up -d
./gradlew bootRun
```

## Verify

`./gradlew build` runs integration tests against a real PostgreSQL 18.4 container. The tests verify Flyway schema creation,
resource invariants, live external-Group authorization, archived Project behavior, and the OAuth client-credentials flow.

```bash
./gradlew build
```
