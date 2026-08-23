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

Documentation:

```bash
cd docs
npm run lint:check
npm run format:check
npm run types:check
npm run build
```

Dockerfiles are checked by Hadolint in GitHub Actions when a Dockerfile changes.

## Pull request checklist

- [ ] The change has one clear purpose and no unrelated edits.
- [ ] Tests cover new behavior and important failure cases.
- [ ] Public behavior and operational configuration are documented.
- [ ] Database changes use Flyway and preserve database invariants.
- [ ] Application persistence uses Spring Data or the owning library API, without raw SQL.
- [ ] `npm run format:check` passes.
- [ ] `cd server && ./gradlew --no-daemon build` passes when server code changes.
- [ ] Documentation checks pass when files under `docs/` change.
- [ ] Dockerfile checks pass when a Dockerfile changes.
- [ ] The pull request description explains the change and lists the verification performed.
