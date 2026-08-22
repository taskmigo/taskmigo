# Taskmigo

Taskmigo is a modern Redmine alternative. The repository is one shared codebase with independently runnable web and worker applications.

## Stack

- Java
- Gradle multi-project build
- Spring Boot
- Spring Modulith
- NullAway with JSpecify
- Prettier with prettier-plugin-java
- Spring Security Authorization Server
- PostgreSQL
- Flyway
- Testcontainers

Dependency and plugin versions are defined in `gradle/libs.versions.toml`. Spring Boot and Spring Modulith BOMs provide
compatible versions for their dependency families.

## Project structure

| Project           | Responsibility                                            | Output              |
| ----------------- | --------------------------------------------------------- | ------------------- |
| `taskmigo-core`   | Shared domain modules, persistence, and Flyway migrations | Library JAR         |
| `taskmigo-web`    | HTTP API, OAuth authorization server, and resource server | Executable Boot JAR |
| `taskmigo-worker` | Background processing without an embedded web server      | Executable Boot JAR |

Both applications depend on `taskmigo-core`; core never depends on either executable.
Spring Modulith treats each direct package below `io.taskmigo` as an application module. Architecture tests reject module cycles,
access to internal packages, and dependencies not explicitly allowed by each module.

## Database lifecycle

Flyway is the only schema owner. `taskmigo-core/src/main/resources/db/migration` contains the database definition and invariants.
Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate`; it never creates or updates tables.

The initial migration enforces database uniqueness plus cross-table invariants such as same-Organization Group members,
valid polymorphic Project principals, Project-owned Role assignments, and archived Project immutability.

## Authentication and authorization

Only `taskmigo-web` exposes OAuth 2.1 and HTTP endpoints. The bootstrap client uses `client_credentials` and the
`taskmigo.api` scope. Domain Users remain separate from authentication identities.

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

Start PostgreSQL, then run either application independently:

```bash
docker compose up -d
./gradlew :taskmigo-web:bootRun
```

```bash
./gradlew :taskmigo-worker:bootRun
```

Build the independently deployable container images from the repository root:

```bash
docker build --file taskmigo-web/Dockerfile --tag taskmigo-web .
docker build --file taskmigo-worker/Dockerfile --tag taskmigo-worker .
```

Both images use Temurin images pinned by manifest digest, contain only the selected executable Boot JAR plus its runtime,
and run as the non-root `taskmigo` user. The web image exposes port `8080`; the worker image has no listening port.

Dockerfile changes are checked by Hadolint in a path-filtered workflow; the job is not created for unrelated changes.

## Verify

The verification gate scans the entire repository with Prettier, enforces JSpecify nullness contracts with NullAway,
validates the Spring Modulith boundaries, and runs the web integration tests against a real PostgreSQL container.

```bash
npm ci
npm run format:check
./gradlew build
```

Format the entire repository or check formatting without changing files. Unsupported file types are scanned and skipped:

```bash
npm run format:fix
npm run format:check
```
