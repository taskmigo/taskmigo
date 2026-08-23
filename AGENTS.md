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

## Pull requests

- Before creating or updating a pull request, read the applicable contribution guidance and `.github/pull_request_template.md` from the target branch.
- After every material change, re-evaluate the pull request title, description, and checklist and update them when needed so they never become stale.
- Only mark checklist items complete when they are satisfied.

## Verification

- A task is not complete until all required local verification and CI checks for the pull request have passed.
- Run or observe all verification required by the applicable repository instructions, then monitor the required CI checks after pushing changes.
- If the `Formatting` workflow fails, use the `prettier-formatted-files` artifact from that workflow run as the preferred formatting repair source. The artifact contains only files rewritten by Prettier, preserves repository-relative paths, and expires after 1 day.
- Apply the files from `prettier-formatted-files` over the working tree instead of installing formatting tooling solely to reproduce CI formatting. Push the repaired files and use the next `Formatting` CI run to verify the result.
- If any required CI check fails, inspect the failure, fix the cause when it is related to the task, push the correction, and continue this cycle until all required checks pass.
- Do not stop work, report completion, or hand off a task while required verification is failing or pending. Stop only when all required checks pass or an external blocker that cannot be resolved from the repository is identified and clearly reported.
