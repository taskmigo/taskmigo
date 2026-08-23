# Taskmigo

Taskmigo is a modern Redmine alternative. The repository is one shared codebase with independently runnable web and
worker applications plus the product documentation site.

## Stack

- Java
- Gradle multi-project build
- Spring Boot
- Spring Modulith
- NullAway with JSpecify
- Prettier with prettier-plugin-java
- Spring Security Authorization Server
- OpenAPI
- PostgreSQL
- Flyway
- Testcontainers
- Next.js with Fumadocs

Server dependency and plugin versions are defined in `server/gradle/libs.versions.toml`. Spring Boot and Spring Modulith
BOMs provide compatible versions for their dependency families.

## Project structure

| Project                  | Responsibility                                            | Output              |
| ------------------------ | --------------------------------------------------------- | ------------------- |
| `server/taskmigo-core`   | Shared domain modules, persistence, and Flyway migrations | Library JAR         |
| `server/taskmigo-web`    | HTTP API, OAuth authorization server, and resource server | Executable Boot JAR |
| `server/taskmigo-worker` | Background processing without an embedded web server      | Executable Boot JAR |
| `docs`                   | Documentation site and versioned product contract         | Static site         |

Both applications depend on `taskmigo-core`; core never depends on either executable.
Spring Modulith treats each direct package below `io.taskmigo` as an application module. Architecture tests reject module cycles,
access to internal packages, and dependencies not explicitly allowed by each module.

## Database lifecycle

Flyway is the only schema owner. `server/taskmigo-core/src/main/resources/db/migration` contains the database definition
and invariants.
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
cd server
docker compose up -d
./gradlew :taskmigo-web:bootRun
```

```bash
cd server
./gradlew :taskmigo-worker:bootRun
```

Run the documentation site independently:

```bash
cd docs
npm ci
npm run dev
```

Build the independently deployable container images from the repository root:

```bash
docker build --file server/taskmigo-web/Dockerfile --tag taskmigo-web server
docker build --file server/taskmigo-worker/Dockerfile --tag taskmigo-worker server
```

Both images use Temurin images pinned by manifest digest, contain only the selected executable Boot JAR plus its runtime,
and run as the non-root `taskmigo` user. The web image exposes port `8080`; the worker image has no listening port.

Dockerfile changes are checked by Hadolint in a path-filtered workflow; the job is not created for unrelated changes.

## Verify

The repository formatting gate scans every supported file from the repository root with Prettier. The server gate
enforces JSpecify nullness contracts with NullAway, validates the Spring Modulith boundaries, and runs the web integration
tests against a real PostgreSQL container.

```bash
npm ci
npm run format:check
cd server
./gradlew build
```

Format or check the entire repository. Unsupported file types are scanned and skipped:

```bash
npm run format:fix
npm run format:check
```

Verify the documentation site from `docs/`:

```bash
npm ci
npm run lint:check
npm run format:check
npm run types:check
npm run build
```

## GitHub Pages

The documentation workflow deploys `docs/out` after documentation changes reach `next`. Before the first deployment,
open the repository's **Settings → Pages** page and set **Build and deployment → Source** to **GitHub Actions**. This is a
one-time repository setting; workflow tokens intentionally cannot enable Pages on their own.
