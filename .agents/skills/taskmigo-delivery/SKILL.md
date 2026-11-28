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

## Construction and delivery

- Apply the relevant repository skills, contribution guidance, and test requirements. Preserve unrelated behavior and group coherent changes.
- Run a bounded adversarial review and direct-consumer preflight before the final push. Add tests that prove changed invariants and important failure cases.
- Follow the github-pr-ci-workflow skill when working with PRs and CI. Use the current head SHA, exact diagnostics and canonical artifacts. Never substitute an AI review or a successful engine stage for required verification.
- Report pending or failed verification as incomplete. Keep the PR template and issue relationship accurate. Do not merge or deploy without explicit maintainer authorization; a workflow gate is not deployment permission.

## Knowledge review

- Run the engineering-knowledge skill before the final implementation push or research handoff. Capture useful discoveries as well as mistakes; update existing human-readable guides and link evidence. Review the knowledge impact even when no new document is warranted.
- Complete the PR knowledge-impact checklist with document links or a concise no-change reason. Record unverified or out-of-scope candidates in the issue, and do not reopen frozen scope or restart CI merely for an adjacent learning idea.

## State and feedback

Use upstream intent state to resume native workflows, and the aidlc-chat checkpoint contract to resume conversational workflows. Record issue, specification revision, PR, and current verification evidence as references in the intent. Do not copy full issue discussions, logs, credentials, tokens, personal data, or unrelated code into artifacts. Review the generated artifacts before committing or sharing them. Preserve human-authored approvals and upstream audit records; do not synthesize or backdate them.

When switching agents, reload the intent, approved scope, current PR head, and actual CI evidence. If the engine is unavailable, use the conversational workflow and report that native gates and hooks were not executed. Do not silently change quality policy; route reusable learning through the engineering-knowledge skill.
