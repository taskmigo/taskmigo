# Taskmigo

Taskmigo is a modern Redmine alternative. The repository contains independently runnable server applications, shared
server modules, the Next.js client, and the documentation site.

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

| Project | Responsibility | Output |
| --- | --- | --- |
| `server/apps/bootstrap` | Database migration and installation-state reconciliation | Executable Boot JAR |
| `server/apps/web` | HTTP API, OAuth authorization server, and resource server | Executable Boot JAR |
| `server/apps/worker` | Background processing without an embedded web server | Executable Boot JAR |
| `server/modules/database` | Shared datasource configuration and canonical Flyway migration history | Library JAR |
| `server/modules/identity` | OAuth identity contracts shared by independently runnable applications | Library JAR |
| `server/modules/organization` | Users, organizations, groups, roles, and permissions | Library JAR |
| `server/modules/project` | Projects, memberships, authorization, and project history | Library JAR |
| `server/modules/foundation` | Cross-domain primitives used by server modules | Library JAR |
| `client` | Next.js application and client-side packages | Application |
| `docs` | Documentation site and versioned product contract | Static site |

Applications compose shared modules; shared modules never depend on executable applications. Spring Modulith verifies the
runtime application-module boundaries used by `web` and `worker`.

## Database lifecycle

Database lifecycle has one execution owner: `server/apps/bootstrap`.

The deployment order is:

1. Run `apps/bootstrap` to completion.
2. Bootstrap runs Flyway migrations from `modules/database/src/main/resources/db/migration`.
3. Bootstrap reconciles installation-specific persistent state such as the reserved `system` user and Taskmigo-managed
   OAuth clients.
4. Start `apps/web` and `apps/worker` only after bootstrap succeeds.

`web` and `worker` explicitly disable Flyway and never reconcile installation state. Hibernate uses
`spring.jpa.hibernate.ddl-auto=validate`; runtime applications therefore fail fast when started against an incompatible
schema instead of mutating it.

Flyway is the only schema owner. Feature modules must not contain migration directories or duplicate the shared database
configuration. The server build verifies this ownership rule.

Schema migrations and runtime bootstrap are intentionally separate concerns:

- **Flyway migration** changes versioned physical schema and version-coupled reference data.
- **Bootstrap reconciliation** applies installation-specific desired state sourced from configuration or secrets.
- **Development/demo seed data** is not part of production bootstrap and must remain opt-in.

## Authentication and authorization

Only `apps/web` exposes OAuth and HTTP endpoints. OAuth client registrations are persisted in PostgreSQL and must be
reconciled by `apps/bootstrap` before `web` starts. Domain users remain separate from authentication methods.

Declare internal clients with Spring Boot Authorization Server properties. The bootstrap application consumes the same
configuration namespace:

```bash
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_CLIENTID=cli
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_CLIENTSECRET='replace-me'
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_CLIENTAUTHENTICATIONMETHODS=client_secret_basic
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_AUTHORIZATIONGRANTTYPES=client_credentials
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_SCOPES=taskmigo.api
export TASKMIGO_BOOTSTRAP_USER_PASSWORD='replace-me'
```

For browser login bootstrap also configure:

```bash
export TASKMIGO_AUTH_BROWSER_ENABLED=true
export TASKMIGO_AUTH_CLIENT_SECRET='replace-me'
export TASKMIGO_CLIENT_URL=http://localhost:3000
```

The web application separately needs its signing key:

```bash
export TASKMIGO_OAUTH_SIGNING_KEY_FILE=/run/secrets/oauth-signing-key.pem
```

Production instances must receive the same externally provisioned signing key. The web application does not generate one
by default. For a single-instance local environment only, set `TASKMIGO_OAUTH_SIGNING_KEY_AUTO_CREATE=true`.

## Run

Start PostgreSQL and export the bootstrap configuration:

```bash
cd server
docker compose up -d
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_CLIENTID=cli
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_CLIENTSECRET='local-secret'
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_CLIENTAUTHENTICATIONMETHODS=client_secret_basic
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_AUTHORIZATIONGRANTTYPES=client_credentials
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_SCOPES=taskmigo.api
export TASKMIGO_BOOTSTRAP_USER_PASSWORD='local-password'
./gradlew :apps:bootstrap:bootRun
```

After bootstrap exits successfully, start the runtime applications:

```bash
export TASKMIGO_OAUTH_SIGNING_KEY_AUTO_CREATE=true
./gradlew :apps:web:bootRun
```

```bash
./gradlew :apps:worker:bootRun
```

Get a local machine token:

```bash
curl -u "${SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_CLIENTID}:${SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_CLI_REGISTRATION_CLIENTSECRET}" \
  -d grant_type=client_credentials \
  -d scope=taskmigo.api \
  http://localhost:8080/oauth2/token
```

Use the returned bearer token for `/api/v0/**`.

Run the documentation site independently:

```bash
cd docs
npm ci
npm run dev
```

Build the independently deployable server images from the repository root:

```bash
docker build --file server/apps/bootstrap/Dockerfile --tag taskmigo-bootstrap server
docker build --file server/apps/web/Dockerfile --tag taskmigo-web server
docker build --file server/apps/worker/Dockerfile --tag taskmigo-worker server
```

Run the bootstrap image as a one-shot deployment job before rolling out the web and worker images. All images use Temurin
images pinned by manifest digest and run as the non-root `taskmigo` user.

Dockerfile changes are checked by Hadolint in a path-filtered workflow.

## Verify

The repository formatting gate scans supported files from the repository root with Prettier. The server gate enforces
JSpecify nullness contracts with NullAway, validates Spring Modulith boundaries, verifies database lifecycle ownership,
and runs integration tests against PostgreSQL containers.

```bash
npm ci
npm run format:check
cd server
./gradlew --no-daemon build
```

Format or check the entire repository:

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
