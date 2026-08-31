# AGENTS.md

Instructions for AI agents and automated contributors working in this repository.

## Instruction scope

- Read this file before making repository changes.
- For every file being changed, discover applicable `AGENTS.md` and `CONTRIBUTING.md` files from the repository root down to that file's directory.
- A file in a subdirectory applies to that directory and its descendants.
- More specific instructions take precedence when they conflict; otherwise, applicable instructions are cumulative.

## Repository skills

- Repository-specific agent skills live under `.agents/skills/<skill-name>/SKILL.md`.
- Before starting work, inspect `.agents/skills/` and discover the skills relevant to the task. Do not rely only on the list below because new skills may be added over time.
- Read the complete `SKILL.md` for every relevant skill before applying it, and follow its trigger conditions, workflow, and constraints.
- Use all relevant skills when more than one applies. Repository instructions in applicable `AGENTS.md` and `CONTRIBUTING.md` files remain authoritative if guidance conflicts.

### Available skills

- `java-javadoc` (`.agents/skills/java-javadoc/SKILL.md`): write and review Java Javadoc using Markdown documentation comments (JEP 467). Use it when adding, writing, fixing, or reviewing Javadoc, documenting Java classes, methods, or fields, or generating public Java APIs that should include documentation comments.

## Working changes

- Keep changes focused and preserve unrelated work.
- Group related file changes into a logical chunk instead of creating one commit per file.
- Prefer one commit per completed chunk. Squash temporary or mechanical commits before updating the working branch.

## Database schema lifecycle

- Keep exactly one Flyway SQL schema file for each application major version. The migration version follows the application major version; while the application remains on major version `1`, all schema changes belong in `V1__schema.sql`.
- Do not create an additional Flyway migration file for feature work within the same application major version. Amend the current major-version schema file instead.
- Start a new Flyway schema file only when the application itself moves to a new major version.
- Tests and build tooling that verify database ownership must preserve this one-schema-file-per-major invariant.

## Pull requests

- Before creating or updating a pull request, read the applicable contribution guidance and `.github/pull_request_template.md` from the target branch.
- After every material change, re-evaluate the pull request title, description, and checklist and update them when needed so they never become stale.
- Only mark checklist items complete when they are satisfied.

## Verification

- A task is not complete until all required local verification and CI checks for the pull request have passed.
- Run or observe all verification required by the applicable repository instructions, then monitor the required CI checks after pushing changes.
- Treat the current GitHub Actions job log as the primary source of truth for workflow/bootstrap failures. Read the failing step before attempting local reproduction.
- Prefer structured machine-readable reports over rendered log summaries when a workflow provides them.
- Keep CI diagnostics bounded. Workflows intended for agents must not dump unbounded raw reports, full SARIF payloads, dependency trees, or thousands of repetitive findings into the job log.
- When a failure contains many findings, first identify whether they share one systemic cause such as unresolved dependencies, stale caches, configuration drift, generated code, or one repeated inspection. Fix the root cause before editing individual findings.
- Distinguish tool/bootstrap failures from source findings. A Qodana failure caused by project import, unresolved classpath entries, cache inconsistency, or linter startup must be fixed at the workflow/tooling layer rather than suppressed in source code.
- For Qodana source findings, do not rely on findings copied to stdout. Download the `qodana-sarif` artifact from the failing workflow run and read `qodana.sarif.json` directly. Use SARIF fields such as `ruleId`, `message`, `locations`, and `physicalLocation.region` to identify the affected file and line. Use the `Scan` log only to diagnose Qodana startup, project import, Gradle/classpath, cache, or report-generation failures.
- The `qodana-sarif` artifact is intentionally uploaded only on failure and retained briefly for agent repair work. If it is unavailable, use the GitHub code-scanning SARIF result as the fallback rather than asking the workflow to print the full report.
- Do not enable Qodana `upload-result` merely to make CI agent-readable. The lightweight SARIF artifact plus the existing GitHub code-scanning upload are sufficient; enable a full Qodana report only when a specific manual-debugging need justifies the extra upload cost.
- Do not download unrelated workflow artifacts, enable extra report uploads, install tooling, or rerun expensive commands solely to recover information that is already available in the designated structured report or job log.
- If the `Formatting` workflow fails, inspect the `Formatting diff` or `Formatting patch for AI agents` section in the failed `Check formatting` step. The workflow runs Prettier and prints the resulting unified diff directly in the CI log.
- Apply that diff to the working tree instead of installing formatting tooling solely to reproduce CI formatting. Push the repaired files and use the next `Formatting` CI run to verify the result.
- If any required CI check fails, inspect the failure, fix the cause when it is related to the task, push the correction, and continue this cycle until all required checks pass.
- Do not stop work, report completion, or hand off a task while required verification is failing or pending. Stop only when all required checks pass or an external blocker that cannot be resolved from the repository is identified and clearly reported.
