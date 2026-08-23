# Contributing to Taskmigo

This file defines repository-wide contribution requirements. A more specific `CONTRIBUTING.md` in a subdirectory may add to or override these rules for that subtree.

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

## Change quality

- Keep each change focused and exclude unrelated edits.
- Add tests for new behavior and important failure cases.
- Document public behavior, operational configuration, and significant decisions when applicable.

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

When the `Formatting` CI workflow reports differences, inspect the `Formatting diff` section in the failed `Check formatting` step. The workflow runs Prettier and prints the resulting unified diff directly in the CI log. If local formatting tooling is unavailable, apply that diff to the working tree instead of installing Node.js or Prettier solely to reproduce the CI formatter output, then push the result and use the next `Formatting` CI run as verification.

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

Resolve all required pipeline failures before requesting review.

## Pull requests

Keep the pull request title and description current throughout the change. Update them whenever the implementation, scope, or verification information changes so reviewers never see stale metadata.

### Title

Use a short, plain-language title that describes the main change. Do not use a Conventional Commit prefix such as `feat:` or `ci:`.

Example: `Verify Markdown files`

### Description

Follow `.github/pull_request_template.md`. Keep its section structure and checklist, and update them to reflect the actual implementation and verification status.
