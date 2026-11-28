---
name: taskmigo-delivery
description: Use for AI-DLC workflows and non-trivial AI-assisted features, bug fixes, refactors, security changes, and CI work in Taskmigo. Connect the upstream workflow to repository specifications, GitHub issues, pull requests, approval, adversarial review, and current-head CI evidence.
---

# Taskmigo delivery

Use the upstream AI-DLC engine when available. In ChatGPT Chat or a connector-only environment, automatically use the aidlc-chat skill for conversational execution and GitHub checkpoints. Do not implement a second native engine, invent approval evidence, or manually edit upstream state to advance a gate. Read the applicable repository instructions and discover relevant repository skills before work. The repository rules remain binding even if an upstream profile skips a stage.

## Native capability routing

- In a running native workflow, let upstream directives own generic lifecycle sequencing, plan approval, artifact reviews, testing strategy, build/test stages, and CI pipeline planning. Do not create a parallel ceremony, duplicate artifacts, or manually advance native state. Use the applicable repository skills as Taskmigo-specific constraints and execution procedures inside those stages.
- Reuse native review and test evidence for matching acceptance criteria and the exact reviewed revision. Complete only uncovered repository-specific checks; the bounded adversarial self-review and current-head GitHub verification remain required. An upstream READY verdict does not replace them.
- When the current directive enables the native Learnings protocol, use it for reusable human corrections instead of asking a second equivalent learning question. Keep the shared documentation review for useful engineering discoveries, including discoveries that did not result from a mistake. Native approval or memory does not authorize a change to Taskmigo policy.
- Chat and other connector-only sessions use the repository skills directly for the same obligations. Do not try to run native protocols outside their engine or claim native sensors, reviewers, or learning writes ran.

## Inception

- Discover relevant engineering knowledge through the engineering-knowledge skill before analysis. Read only the applicable articles and verify claims against source and specification.

- Establish the intent and acceptance criteria in the GitHub issue or the user's explicit request. Reuse an existing issue rather than creating a duplicate. For non-trivial delivery work, link the issue and implementation PR.
- Inspect the code and authoritative specification when the task changes or depends on product behavior. Record the applicable specification revision; link a specific release tag in the PR as required by its template.
- Select the smallest upstream profile appropriate to risk. Use the guide for profile selection. A small diff is not necessarily low risk.
- For new product behavior, architecture boundaries, security invariants, or material scope changes, obtain explicit human approval of the proposed behavior and design before implementation. Do not count an AI-written summary, issue label, or earlier unrelated approval as authorization. Reopen the gate if the approved scope materially changes. Breaking changes within an explicitly approved scope are permitted when the maintainer allows them.
- Use the adversarial-self-review skill to compare alternatives and freeze the selected scope.
- Identify affected generated artifacts and dependent contracts using the Generated Artifact Impact Check below. Plan their canonical generation, synchronization and verification alongside source changes, not as a later CI repair.

## Construction and delivery

- Apply the relevant repository skills, contribution guidance, and test requirements. Preserve unrelated behavior and group coherent changes.
- Run a bounded adversarial review and direct-consumer preflight before the final push. Add tests that prove changed invariants and important failure cases.
- Follow the github-pr-ci-workflow skill when working with PRs and CI. Use the current head SHA, exact diagnostics and canonical artifacts. Never substitute an AI review or a successful engine stage for required verification.
- Complete the Generated Artifact Impact Check before the final implementation CI cycle and revalidate the evidence after relevant source changes.
- Report pending or failed verification as incomplete. Keep the PR template and issue relationship accurate. Do not merge or deploy without explicit maintainer authorization; a workflow gate is not deployment permission.

## Generated Artifact Impact Check (mandatory)

This repository-specific check applies to native AI-DLC and Chat, including profiles that omit a separate planning or build stage. Use the existing native stage evidence when it covers the same inputs and outputs; do not add a second native stage or state machine.

1. **Identify impact during planning.** Inspect the actual changed source and its direct consumers, tracked generated outputs, canonical generators and existing CI verification. Record applicable artifact paths, generation/check commands and dependency order in the existing plan or issue. REST contract changes include DTOs, validation, error contracts, response schemas, query metadata, security and OpenAPI configuration, not only route changes. Check generated SDKs or source indexes when they actually exist and consume the changed inputs; review authored specifications, configuration documentation and hand-written clients separately.
2. **Generate during Construction.** With a working local environment, run the repository's canonical generator after source changes and before the final implementation push. Regenerate downstream outputs from the updated upstream artifact in dependency order. Inspect the resulting diff for intended changes, include exact generated files with their owning problem commit, and run the relevant verification. An unchanged output is acceptable only with actual generation/check evidence.
3. **Handle unavailable local execution explicitly.** In Chat or connector-only work, record the affected artifacts, missing execution capability, expected generator/CI job and next action before pushing. Use the existing CI generation path and earliest actionable canonical artifact for the exact head, run and attempt. If remote generation needs a draft implementation push, identify it as evidence collection, not a final verification cycle. Fix build blockers and batch all known repairs; do not hand-edit generated output, recreate it from guesses, add redundant runs, or loosen CI gates. Follow the artifact-specific skill and GitHub mutation budgets.
4. **Reconcile and verify.** Inspect the canonical output, synchronize it with the owning change, and verify required checks on the resulting head. Reopen the impact check when generator inputs change or the evidence no longer matches the revision. Mark the PR checklist complete only when every applicable output is synchronized and relevant verification is supported by evidence, or the actual impact analysis establishes that generation is not applicable. Record pending generation in the existing issue/intent and Chat checkpoint next action; it blocks Ready even if other tests pass.

For the current OpenAPI snapshot and connector-only recovery commands, use the [CI evidence guide](../../../docs/engineering/ci-evidence.md) and `github-actions-openapi-snapshot-artifact`. The check is a mandatory delivery obligation, not a claim that a native hook enforces agent compliance.

## Knowledge review

- Run the engineering-knowledge skill before the final implementation push or research handoff. Capture useful discoveries as well as mistakes; update existing human-readable guides and link evidence. Review the knowledge impact even when no new document is warranted.
- Complete the PR knowledge-impact checklist with document links or a concise no-change reason. Record unverified or out-of-scope candidates in the issue, and do not reopen frozen scope or restart CI merely for an adjacent learning idea.

## State and feedback

Use upstream intent state to resume native workflows, and the aidlc-chat checkpoint contract to resume conversational workflows. Record issue, specification revision, PR, and current verification evidence as references in the intent. Do not copy full issue discussions, logs, credentials, tokens, personal data, or unrelated code into artifacts. Review the generated artifacts before committing or sharing them. Preserve human-authored approvals and upstream audit records; do not synthesize or backdate them.

When switching agents, reload the intent, approved scope, current PR head, and actual CI evidence. If the engine is unavailable, use the conversational workflow and report that native gates and hooks were not executed. Do not silently change quality policy; route reusable learning through the engineering-knowledge skill.
