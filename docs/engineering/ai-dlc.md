# AI-driven development

Taskmigo uses the upstream [AWS AI-DLC workflow engine](https://github.com/awslabs/aidlc-workflows) for structured
AI-assisted engineering. The version in [`.aidlc-version`](../../.aidlc-version) is shared by the team. The native
engine manages adaptive workflows, human interaction, intent state and audit evidence. It is a development tool, not
a Taskmigo application dependency.

## Setup

Install the native AI-DLC command using the pinned release from the
[upstream releases](https://github.com/awslabs/aidlc-workflows/releases). Follow the
[verified installation instructions](https://awslabs.github.io/aidlc-workflows/guide/18-install-and-lifecycle/#project-pins-and-ci)
for your operating system; do not install an unreviewed latest release. Install GitHub Copilot or Codex separately.
Run the following commands from the repository root after installation.

For GitHub Copilot (CLI or VS Code):

```sh
task aidlc:copilot
```

For Codex CLI:

```sh
task aidlc:codex
```

Without Task, run the equivalent commands:

```sh
aidlc config --harness copilot --mcp none --download
aidlc doctor
```

Use `--harness codex` instead for Codex. Configure only one of these harnesses per checkout: their upstream-managed
root instruction blocks are incompatible. Use independent clones for the two harnesses; do not force an incompatible
configuration. The upstream runtime is generated locally, not vendored into Taskmigo.

Read the [Copilot](https://awslabs.github.io/aidlc-workflows/guide/harnesses/copilot/) or
[Codex](https://awslabs.github.io/aidlc-workflows/guide/harnesses/codex-cli/) guide for supported host versions and
hook trust. For Copilot CLI, trust the project folder; for VS Code, trust the workspace and enable chat hooks. For
Codex, inspect and trust the generated project hooks. Keep provider credentials in user settings, not the repository.
Use your existing model provider; this integration does not require Amazon Bedrock or add MCP servers.

Run the host-level doctor (`/aidlc --doctor` in Copilot or `$aidlc --doctor` in Codex) before starting. Do not
bypass hook trust, approval, sandbox, or repository verification controls to make the doctor pass.

## Choose a workflow

Use the [upstream profiles](https://awslabs.github.io/aidlc-workflows/guide/workflow-profiles/) rather than maintaining
a second profile engine.

| Work                                   | Starting profile                        |
| -------------------------------------- | --------------------------------------- |
| Small, well-understood change          | `express`                               |
| Known defect or regression             | `bugfix`                                |
| Behavior-preserving refactor           | `refactor`                              |
| Feature with material design decisions | `classic` or `feature`                  |
| Security-sensitive design              | `enterprise` or a reviewed custom route |
| Focused vulnerability fix              | `security-patch`                        |
| CI or infrastructure changes           | `infra` or `bugfix` for a known defect  |
| Feasibility experiment                 | `poc`                                   |

Choose a deeper route when risk or ambiguity demands it. For research-only work, stop at the approved investigation
deliverable rather than starting implementation. A profile never overrides Taskmigo rules or required checks.

Start in Copilot chat:

```text
/aidlc bugfix Investigate issue #123. Apply the Taskmigo delivery skill and repository instructions.
```

In Codex, use `$aidlc` instead. Replace the example issue number with the actual issue. Ask the agent to use
[`taskmigo-delivery`](../../.agents/skills/taskmigo-delivery/SKILL.md) for repository-specific delivery policy.

## Delivery and evidence

Inception establishes intent, acceptance criteria, design and approval. Construction implements the approved scope,
tests changed invariants and applies the existing adversarial review and CI skills. The delivery gate requires
current-head GitHub evidence and an accurate PR. Deployment and merge remain separately authorized maintainer actions.

| Artifact                           | Owner                                                               |
| ---------------------------------- | ------------------------------------------------------------------- |
| Intent and acceptance criteria     | GitHub issue                                                        |
| Product behavior                   | [Taskmigo specification](https://github.com/taskmigo/specification) |
| Design and human approval          | AI-DLC intent and linked issue or decision record                   |
| Source and regression tests        | Implementation branch                                               |
| Review and implementation evidence | Pull request                                                        |
| Required verification              | GitHub Actions for the current head SHA                             |
| Resume state and audit trail       | Upstream AI-DLC intent                                              |
| Durable engineering rules          | Repository instructions, skills and deterministic checks            |

Resume an existing intent instead of recreating it. Re-read the approved scope, current branch, PR head, and relevant
verification evidence. A changed head invalidates the old verification result. Treat material design changes as
requiring fresh approval.

Keep generated harness runtime and machine-local configuration out of product PRs. Inspect `git status` after
configuration, preserve repository-owned instructions, and stage explicit paths rather than using `git add -A`.
Follow upstream artifact ownership for shared intent records and audit shards; review their contents before committing.
Never commit credentials, personal data, unrestricted logs, local cursors or machine-local runtime state. Reference
existing issue, specification and CI evidence rather than duplicating it. If a connector-only agent cannot access
a configured host, it must disclose that limitation and follow the repository delivery policy without claiming
upstream gate execution.

## Verification and upgrades

The AI-DLC CI workflow verifies release provenance, projects each supported harness into a separate disposable
checkout, and checks that repository-owned instructions, skills and CI files are preserved. It does not call a model,
grant hook trust, or run an interactive lifecycle.

Before treating this rollout as complete, run an interactive pilot on both supported harnesses: start a focused
bugfix, approve the proposed scope, reject and revise a material change, resume the intent in a fresh session, and
verify the final PR against its current head SHA. Confirm that an agent cannot substitute its own approval for the
maintainer's response. Record actual outcomes and any missing hook capabilities in the tracking issue.

For upgrades, change the shared version in a focused PR and rerun the integration checks. Preview configuration
changes using `aidlc config --dry-run` and inspect any proposed changes to repository-owned files. Use the upstream
pin and refresh commands rather than editing the generated engine. Keep existing product CI requirements unchanged.
