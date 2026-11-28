# AI-driven development

Taskmigo uses the upstream [AWS AI-DLC workflow engine](https://github.com/awslabs/aidlc-workflows) for structured
AI-assisted engineering. The version in [`.aidlc-version`](../../.aidlc-version) is shared by the team. The native
engine manages adaptive workflows, human interaction, intent state and audit evidence. It is a development tool, not
a Taskmigo application dependency.

## ChatGPT Chat (no CLI required)

Chat is a first-class Taskmigo workflow, not an official AWS native harness. Repository skill discovery activates
[`aidlc-chat`](../../.agents/skills/aidlc-chat/SKILL.md) for non-trivial connector-only work. Start with a normal
request such as `Implement issue #123` or `Continue issue #123`. No slash command, native hooks, or Work mode is
required. A GitHub connection with access to the repository is required for durable state and implementation.

The agent uses the same profile guidance and delivery rules as the native engine. It records material stage
transitions as issue comments and references existing proposals, PRs, and CI evidence. The checkpoint does not
authorize a stage or certify a check. Human approval in the current conversation is valid for the exact proposal;
a new session must reconfirm a material decision if independent human approval cannot be verified. Delegated GitHub
identities can make agent-written comments appear human-authored, so comment authorship alone is not sufficient.

### Checkpoint contract

Use the marker `<!-- taskmigo-ai-dlc:v1 -->` on its own line, followed by a fenced JSON block. The complete example
is in the chat skill. The checkpoint has these fields:

- `issue`: positive GitHub issue number matching the comment's issue.
- `profile`: non-empty upstream profile name.
- `stage`: `inception`, `approval`, `construction`, `verification`, or `ready`.
- `proposal`: null or an object with positive `comment_id` and ISO UTC `updated_at` for the proposal comment.
- `approval`: `pending`, `session`, `external`, or `not-required`. This is descriptive metadata, not proof.
- `pr` and `head`: null or the linked PR number and its full 40-character Git commit SHA, respectively.
- `next`: concise next action or blocker.

A checkpoint must reference a proposal if approval is pending, session-based, or external. An approval-stage
checkpoint requires pending approval. PR and head must be present together; verification requires both. Ready
without a PR is allowed for research-only work. Read the actual proposal revision, PR head, and required checks
before acting. Append checkpoints rather than editing old comments. A malformed or conflicting checkpoint is not
permission to advance. The CI validator checks checkpoint shape, not approval provenance or CI success.

Switching between Chat and native harnesses is an explicit handoff via the linked GitHub issue and intent references.
The receiving agent validates evidence and runs its own required gates. There is no automatic synchronization of
native workflow internals, and no fake native audit records.

## Native setup

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
a second profile engine. Chat uses these profiles as risk-based guidance; native harnesses execute the upstream stages.

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

For native execution, start in Copilot chat:

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
| Resume state and audit trail       | Upstream AI-DLC intent or linked GitHub issue checkpoints           |
| Durable engineering rules          | Repository instructions, skills and deterministic checks            |

Resume an existing intent instead of recreating it. Re-read the approved scope, current branch, PR head, and relevant
verification evidence. A changed head invalidates the old verification result. Treat material design changes as
requiring fresh approval.

Keep generated harness runtime and machine-local configuration out of product PRs. Inspect `git status` after
configuration, preserve repository-owned instructions, and stage explicit paths rather than using `git add -A`.
Follow upstream artifact ownership for shared intent records and audit shards; review their contents before committing.
Never commit credentials, personal data, unrestricted logs, local cursors or machine-local runtime state. Reference
existing issue, specification and CI evidence rather than duplicating it. Chat agents disclose that native hooks
and gates were not executed.

## Built-in capabilities and repository skills

Use the pinned AI-DLC release as the generic lifecycle implementation. Its native stages and protocols own planning,
approval, independent review, test strategy and execution, and learning when scheduled. Taskmigo skills provide
repository-specific constraints and connector-only execution, not a second native engine.

The following assessment is based on the [v2.11.5 source](https://github.com/awslabs/aidlc-workflows/tree/v2.11.5).
Revisit it when upgrading the pin; a similarly named capability is not evidence of equivalent behavior.

- **Adversarial review:** the [architecture reviewer](https://github.com/awslabs/aidlc-workflows/blob/v2.11.5/core/knowledge/aidlc-architecture-reviewer-agent/reviewing.md) and [unit review protocol](https://github.com/awslabs/aidlc-workflows/blob/v2.11.5/core/aidlc-common/protocols/stage-protocol-reviewer-unit.md) own native independent reviews. Retain `adversarial-self-review` for semantic-boundary proof, solution minimization, scope freeze and direct-consumer preflight. Reuse matching findings; do not dispatch duplicate independent reviews.
- **Testing:** [Code Generation](https://github.com/awslabs/aidlc-workflows/blob/v2.11.5/core/aidlc-common/stages/construction/code-generation.md) and [Build and Test](https://github.com/awslabs/aidlc-workflows/blob/v2.11.5/core/aidlc-common/stages/construction/build-and-test.md) own generic test strategy and execution. Retain `spring-boot-testing` for concrete Spring Boot, JUnit, AssertJ and Testcontainers conventions and templates. Generic test ratios or counts do not replace risk-based repository coverage.
- **PR and CI:** the upstream pipeline methodology supplies generic pipeline planning. Retain `github-pr-ci-workflow` for current-head checks, PR template and issue links, GitHub mutation discipline and recovery from actual CI failures.
- **CI artifacts:** retain the four `github-actions-*-artifact` skills. Upstream generic verification does not define Taskmigo's canonical formatting patch, generated OpenAPI snapshot, Gradle log or Qodana results artifact contract.
- **Connector refactors:** retain `github-connector-large-refactors`. Native worktree and delivery orchestration do not implement connector-only atomic Git tree writes, expected-head leases or mutation budgets.
- **Java documentation:** retain `java-javadoc` for Taskmigo's JEP 467 Markdown Javadoc conventions.
- **Knowledge:** use the native [Learnings protocol](https://github.com/awslabs/aidlc-workflows/blob/v2.11.5/core/aidlc-common/protocols/stage-protocol-learnings.md) for human corrections when enabled. Retain `engineering-knowledge` for verified, human-readable engineering guides and useful discoveries from successful work. The built-in [aidlc-knowledge skill](https://github.com/awslabs/aidlc-workflows/blob/v2.11.5/core/skills/aidlc-knowledge/SKILL.md) indexes existing documents; it does not curate these guides and onboarding repository files creates managed copies.
- **Delivery:** retain `taskmigo-delivery` and `aidlc-chat` as the shared repository-policy bridge and connector-only lifecycle. Chat cannot execute native hooks or engine-owned protocols.

No legacy skill has a complete upstream replacement at this pin. Delegate generic operations to the native stage
when scheduled, reuse matching evidence, and perform the remaining Taskmigo obligations once. In Chat, invoke
repository procedures directly. Do not replace a specific repository rule with a weaker generic recommendation,
skip required checks, or maintain another native state machine.

## Continuous knowledge development

Both Chat and native harnesses use the same [engineering knowledge base](../README.md). During discovery, read the relevant guides. Before the final implementation push or investigation handoff, review what the task established: useful architecture and implementation knowledge, proven techniques, operational insights, and lessons from mistakes. Use the [knowledge maintenance guide](knowledge-maintenance.md) and the [engineering-knowledge skill](../../.agents/skills/engineering-knowledge/SKILL.md) to update the owning document or record a focused follow-up when evidence or scope is incomplete.

Knowledge review is part of Taskmigo delivery policy, not a new upstream engine stage or a separate AI memory system. New documents are conditional on usefulness. The PR checklist records whether relevant knowledge was updated or why it did not need to change; a checked box cannot certify technical accuracy.

## Verification and upgrades

The AI-DLC CI workflow verifies release provenance, projects each supported harness into a separate disposable
checkout, and checks that repository-owned instructions, skills and CI files are preserved. It does not call a model,
grant hook trust, or run an interactive lifecycle.

Before treating this rollout as complete, run an interactive pilot in Chat and both native harnesses: start a focused
bugfix, approve the proposed scope, reject and revise a material change, resume the intent in a fresh session, and
verify the final PR against its current head SHA. Confirm that an agent cannot substitute its own approval for the
maintainer's response. In Chat, open a new conversation, resume the issue, verify that the old approval is not
silently trusted, and confirm a stale head cannot advance verification. Record actual outcomes and any missing hook
capabilities in the tracking issue.

For upgrades, change the shared version in a focused PR and rerun the integration checks. Preview configuration
changes using `aidlc config --dry-run` and inspect any proposed changes to repository-owned files. Use the upstream
pin and refresh commands rather than editing the generated engine. Keep existing product CI requirements unchanged.
