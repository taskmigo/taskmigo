# AGENTS.md

Instructions for AI agents working on the Taskmigo documentation site under `docs/`.

## Source of truth

- Use the content on the current branch under `content/versions/v0/` as the product contract.
- Read the pages relevant to the task before changing behavior.
- Keep product-specific rules in the documentation, not in this file.

## Repository and GitHub workflow

- Read `AGENTS.md` from the target branch before modifying repository content.
- Prefer the GitHub plugin for repository, branch, commit, pull-request, and workflow operations.
- Rebase work onto the latest `next` before publishing when the base branch has advanced.
- Keep remote writes scoped and intentional. Preserve unrelated changes and avoid commit noise.
- Treat the current GitHub Actions job log as the source of truth for CI failures. When a workflow prints an agent-readable patch, diff, finding index, or explicit repair instruction, inspect and apply that output before attempting to reproduce the failure locally.
- Do not download workflow artifacts or install tooling solely to recover information that the workflow already prints directly in its log.

## Authoring

- Write public documentation in English.
- Prefer concrete rules and examples over abstract prose.
- Keep each rule in one canonical location and link to it elsewhere.
- Use Mermaid when relationships are easier to understand visually.
- Follow existing Fumadocs and MDX patterns.

## Current product scope

The current v0 contract is intentionally limited to resource management:

- Organizations own Users, Groups, Roles, and Projects.
- A User has exactly one home Organization.
- A Group belongs to one Organization and contains Users from that same Organization only.
- A Role belongs to one Organization and may be assigned only inside Projects owned by that Organization.
- A Project belongs to one Organization but may include Users and Groups from other Organizations.
- Project Membership represents participation, not ownership. Cross-organization staffing must never copy or transfer a User or Group.
- Project authorization is additive. Direct User roles and Group-derived roles are unioned. There is no explicit deny or role inheritance.
- Organization-level authorization, SSO configuration, issues, workflows, billing, contracts, staffing allocations, and invoicing are outside the current contract unless explicitly added later.

## Change checklist

- Update every affected canonical page, example, cross-link, and navigation entry.
- Update the nearest `meta.json` when adding, removing, or reordering pages.
- Remove stale pages and generated-looking artifacts that point to contracts no longer present.
- Do not hand-edit generated output.

## Verification and publishing workflow

Before installing dependencies or running verification in a new environment, read [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md).

Run the repository gates in this order:

```bash
npm run lint:check
npm run format:check
npm run types:check
npm run build
```

Fix every failure and restart from `npm run lint:check` after any content or code change that can affect the result.
For a local formatting failure, use `npm run format:fix`. If the GitHub Actions `Formatting` workflow fails and prints a `Formatting patch for AI agents`, apply that CI-produced patch directly instead of installing or invoking formatting tooling solely to reproduce it.

Never claim verification passed unless the required commands or corresponding required CI checks actually passed.

Keep commit history concise. By default, make at most one commit and one push per completed round of work.
