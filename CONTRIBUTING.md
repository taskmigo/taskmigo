# Contributing to Taskmigo

Thank you for improving Taskmigo. Keep each pull request focused, reviewable, and independently verifiable.

## Development environment

- Java 26
- Node.js 24 for repository formatting
- Node.js 26 for documentation
- Docker for PostgreSQL integration tests

Install the root and documentation dependencies before running their checks:

```bash
npm ci
cd docs
npm ci
```

## Persistence rules

- Flyway owns the database schema. Do not create or update tables from application code.
- Application code must not execute raw SQL through `JdbcTemplate`, `JdbcClient`, native queries, or equivalent APIs.
- Use Spring Data repositories or the persistence API supplied by the owning library.
- Keep library-owned schemas aligned with their upstream definitions unless a documented database-specific substitution is
  required.
- Add an integration test for persistence behavior and migrations.

## Verification

Run the checks affected by your change before opening or updating a pull request.

Repository formatting:

```bash
npm run format:check
```

Server:

```bash
cd server
./gradlew --no-daemon build
```

The server build includes Checkstyle for production and test sources. Qodana runs in GitHub Actions when server files
change.

Documentation:

```bash
cd docs
npm run lint:check
npm run format:check
npm run types:check
npm run build
```

Dockerfiles are checked by Hadolint in GitHub Actions when a Dockerfile changes.

## Pull requests

- Keep the change focused and exclude unrelated edits.
- Add tests for new behavior and important failure cases.
- Document public behavior, operational configuration, and significant decisions.
- Describe what changed and how it was verified.
- Resolve all required pipeline checks before requesting review.
