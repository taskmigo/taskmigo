---
name: taskmigo-delivery
description: Use for AI-DLC workflows and non-trivial AI-assisted features, bug fixes, refactors, security changes, and CI work in Taskmigo. Connect the upstream workflow to repository specifications, GitHub issues, pull requests, approval, adversarial review, and current-head CI evidence.
---

# Taskmigo delivery

Use the upstream AI-DLC engine when available. In ChatGPT Chat or a connector-only environment, automatically use the aidlc-chat skill for conversational execution and GitHub checkpoints. Do not implement a second native engine, invent approval evidence, or manually edit upstream state to advance a gate. Read the applicable repository instructions and discover relevant repository skills before work. The repository rules remain binding even if an upstream profile skips a stage.

## Inception

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

## State and feedback

Use upstream intent state to resume native workflows, and the aidlc-chat checkpoint contract to resume conversational workflows. Record issue, specification revision, PR, and current verification evidence as references in the intent. Do not copy full issue discussions, logs, credentials, tokens, personal data, or unrelated code into artifacts. Review the generated artifacts before committing or sharing them. Preserve human-authored approvals and upstream audit records; do not synthesize or backdate them.

When switching agents, reload the intent, approved scope, current PR head, and actual CI evidence. If the engine is unavailable, use the conversational workflow and report that native gates and hooks were not executed. Capture recurring lessons as a focused proposal for a skill, repository instruction, or deterministic test; do not silently change quality policy.
