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

- Run or observe all verification required by the applicable repository instructions before declaring the work complete.
- If a required CI check fails, inspect the failure, fix the cause, and continue until the check passes or an external blocker is identified.
- Do not claim completion while required verification is failing or still pending.
