# Contributing to Taskmigo

This file defines repository-wide contribution requirements. A more specific `CONTRIBUTING.md` in a subdirectory may add to or override these rules for that subtree.

## Development environment

- Java 26
- Node.js 24 for repository formatting
- Docker for PostgreSQL integration tests

Install the root dependencies before running repository checks:

```bash
npm ci
```

## AI-driven development

For non-trivial AI-assisted engineering work, follow the [AI-DLC guide](docs/engineering/ai-dlc.md) and the shared
[`taskmigo-delivery`](.agents/skills/taskmigo-delivery/SKILL.md) skill. Use the pinned upstream engine in a supported
native harness. ChatGPT Chat and connector-only agents must automatically use the
[`aidlc-chat`](.agents/skills/aidlc-chat/SKILL.md) skill instead. Existing repository instructions, specifications,
human approval requirements, and verification rules remain authoritative in both modes.

## Engineering knowledge

Use the [engineering knowledge index](docs/README.md) to find relevant developer guides. Review knowledge impact for every non-trivial task, including useful discoveries and mistakes. Update the owning document within approved scope, or explain why no update is necessary. Follow the [knowledge maintenance guide](docs/engineering/knowledge-maintenance.md); keep human-readable knowledge and evidence in the repository rather than private agent memory.

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

When the `Formatting` CI workflow reports differences, download the `formatting-patch` artifact from that failed run. It contains `formatting.patch`, generated from the exact Prettier output after `npm run format:fix`. Apply the patch as a whole (`git apply --check formatting.patch` followed by `git apply formatting.patch`) instead of manually fixing formatter output file by file. The `Formatting patch for AI agents` section in the job log is a fallback only when the artifact is unavailable. Push the repaired change and use the next `Formatting` CI run as verification.

Server:

```bash
cd server
./gradlew --no-daemon build
```

The server build includes Checkstyle for production and test sources. Qodana runs in GitHub Actions when server files
change.

Dockerfiles are checked by Hadolint in GitHub Actions when a Dockerfile changes.

Resolve all required pipeline failures before requesting review.

## Pull requests

Keep the pull request title and description current throughout the change. Update them whenever the implementation, scope, or verification information changes so reviewers never see stale metadata.

### Title

Use a short, plain-language title that describes the main change. Do not use a Conventional Commit prefix such as `feat:` or `ci:`.

Example: `Verify Markdown files`

### Description

Follow `.github/pull_request_template.md`. Keep its section structure and checklist, and update them to reflect the actual implementation and verification status.
