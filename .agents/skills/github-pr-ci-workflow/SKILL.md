---
name: github-pr-ci-workflow
description: "Drive GitHub bug fixes, pull requests, and CI to completion efficiently. Use whenever an agent fixes a reported defect, creates or updates a PR, checks GitHub Actions/pipeline status, investigates failed checks, pushes CI fixes, or is asked to continue until CI is healthy. Enforces latest-base bug reproduction, problem-scoped reviewable commit history, one coherent implementation before the final CI cycle, SHA-anchored fail-fast CI triage, hard anti-stall and mutation budgets, bounded GitHub connector batches, batched fixes, non-blocking status checks, and preservation of machine-actionable PR metadata during body rewrites. Avoids cross-problem squash commits, repeated self-review pushes, redundant fixes, stale workflow runs, repeated polling, oversized tool batches, repeated full-PR/log fetches, busy waiting, unnecessary reruns, retrying unavailable local Git/network paths, and accidentally dropping issue-closing directives."
---

# GitHub PR and CI Workflow

Use this workflow for PR implementation, CI triage, and final verification.

The goal is not to "watch CI." The goal is to extract the earliest actionable failure, fix all currently known failures together, and minimize expensive or redundant GitHub operations.

## Bug-fix reproduction gate

Before changing production code for a reported bug, establish whether the defect still exists on the latest target base branch.

1. **Re-anchor to the latest base revision.**
   - Read the current target branch (normally `next`) and record its exact SHA.
   - Treat the issue's original reviewed/reported SHA as historical evidence only.
   - Do not start from an old feature branch or stale issue snapshot when deciding whether a fix is still required.

2. **Reproduce before fixing.**
   - Reproduce the reported behavior against the exact latest-base SHA before making production changes.
   - Prefer an automated regression test, existing executable test, request, benchmark, or deterministic log/trace that exercises the real failure boundary.
   - When the issue already describes a measurable invariant (for example query count, status code, race behavior, or persistence state), encode that invariant directly in the reproduction.
   - Reading suspicious code is useful evidence but is not, by itself, a completed reproduction when the behavior can be executed.
   - When the reproduction test is a valid long-term regression test, keep it in the final fix rather than treating it as disposable scaffolding.
   - If local execution is unavailable and PR-triggered CI is the only practical way to execute the reproducer, one temporary draft/reproducer push is acceptable. Capture the failure evidence, then complete root-cause analysis, impact scanning, implementation, self-review, and preflight **before the next push**. Do not use remote CI as an interactive test runner for every implementation idea.

3. **Branch based on the reproduction result.**
   - **If the bug reproduces:** capture the failing evidence first, then investigate root cause and modify production code. The regression test should fail before the fix and pass after it.
   - **If the bug does not reproduce:** do not add a speculative or redundant production fix. Compare the issue's reviewed/reported SHA with the latest base, inspect the commits and pull requests that changed the affected behavior, and identify the PR that resolved the defect when evidence supports one.
   - If a prior PR already fixed the bug, update or close the issue with the reproducer result and the fixing PR reference instead of opening another implementation PR.
   - If the bug no longer reproduces but the fixing PR cannot be identified confidently, report that uncertainty explicitly; do not invent attribution.

4. **Preserve evidence in the bug-fix record.**
   - State the exact latest-base SHA used for reproduction in the issue/PR RCA when it materially helps reviewers.
   - For already-fixed bugs, link the fixing PR/commit and explain which changed invariant makes the old reproducer pass.
   - For still-reproducible bugs, keep the pre-fix failure evidence distinct from post-fix verification so a green final pipeline is not mistaken for proof that the original defect was reproduced.

## Core rules

1. **Anchor every CI decision to the current PR head SHA.**
   - Read the PR head SHA before checking workflow state.
   - Fetch workflow runs for that SHA.
   - After every push, re-read the head SHA and discard conclusions from older SHAs.
   - Never declare a PR green from runs attached to an earlier commit.

2. **Query from cheapest/smallest signal to most expensive signal.**
   - PR head SHA -> workflow runs -> jobs -> steps -> failing-job log/artifact.
   - Do not fetch the full PR diff merely to check CI status.
   - Do not download logs for successful or still-running jobs.
   - Do not dump large API responses into agent context; project only the fields needed for the current decision.

3. **Fail fast on completed failures.**
   - A failed fast job is immediately actionable even while slower jobs are still running.
   - Investigate all completed failures visible in the same snapshot before editing code.
   - Do not wait for Kubernetes/E2E/performance jobs to finish before fixing an already-failed formatting, compilation, static-analysis, or unit-test job.

4. **Batch fixes and implementation discoveries without crossing problem boundaries.**
   - Collect every currently known failure from the same head SHA.
   - Before pushing, also complete the bounded self-review and direct-consumer/adapter impact scan for the selected change.
   - Here, **problem** means one independently reviewable defect, finding, behavioral gap, or coherent repair concern. It does **not** mean a GitHub Issue ticket.
   - A single GitHub Issue may describe multiple independent problems. When those problems can be reviewed and understood independently, preserve them as separate final commits even though one PR closes one ticket.
   - Preserve a one-to-one final-history boundary: **one problem = one final commit, and one final commit = one problem**.
   - Keep implementation, regression tests, formatting, and CI/review repairs for the same problem together in that problem's final commit.
   - Never combine independent problems into one commit merely because they were reported in the same GitHub Issue or fixed in the same PR.
   - Never split one coherent problem into artificial commits such as "production code", "tests", "formatting", or "review fixes"; those pieces are not independently meaningful repairs.
   - If a change cannot be assigned cleanly to one problem, do not hide that ambiguity in a broad commit; first identify the actual repair boundary.
   - Defer adjacent improvements that are not required to solve the current problem or prevent a regression introduced by its fix.
   - Run the smallest relevant local checks when a local workspace is available.
   - Push once per coherent problem update, then start a fresh SHA-anchored CI cycle.
   - Avoid one-failure/one-commit loops and one-self-review-finding/one-push loops inside the same problem unless later failures were genuinely hidden by earlier execution evidence.

5. **Never busy-wait for CI.**
   - Do not use long blocking wait/sleep operations.
   - Do not repeatedly request the same in-progress status with no intervening work or new signal.
   - If only in-progress checks remain, use the current turn for useful work: code review, diff inspection, PR documentation, or validation of already-completed jobs.
   - If there is nothing actionable, report the exact current status rather than spinning on GitHub.

6. **Treat tool timeout as a query-design problem first.**
   - After an expensive request times out or returns an oversized/truncated payload, do not immediately repeat the same request.
   - Switch to a narrower endpoint or smaller resource.
   - Retry the identical expensive operation at most once, and only when there is evidence the failure was transient.

7. **Use hard anti-stall budgets, not judgment alone.**
   - For one head SHA, take at most one immediate workflow-summary snapshot after a push.
   - A second snapshot in the same user turn is allowed only after useful non-polling work has occurred or the user explicitly asks for fresh status.
   - Never issue two consecutive status-only queries that can return the same in-progress state.
   - Perform at most two automatic CI repair pushes after the initial implementation push in one user turn. If the current SHA is still not green after those repair cycles, stop tool activity and report the exact blocker/status instead of continuing to spin.
   - Once a new head SHA exists, do not poll workflow runs or jobs from an older SHA. Old completed logs may be consulted only if they were already known to contain a concrete diagnostic that still applies; they are never current-state evidence.

8. **Bound GitHub connector mutation batches.**
   - Never create one GitHub blob per file in an unbounded loop for a multi-file change.
   - Prefer one `create_tree` operation with inline file content for a focused multi-file edit.
   - If the payload is too large, chain a small number of tree updates and create one final commit for the current problem/change unit.
   - Keep a single Code Mode orchestration block to roughly 10 awaited GitHub connector calls or fewer. If more operations are genuinely needed, split them into intentional batches with a checkpoint between batches.
   - Do not print raw large API payloads into context. Parse and project only the fields or error window needed for the next decision.

9. **Treat explicit maintainer CI updates as stop signals.**
   - If the maintainer explicitly says all required pipeline/checks have passed for the current PR, stop automated CI polling immediately.
   - Do not issue another status-only workflow/run/job query merely to reconfirm the maintainer's update.
   - Process the interruption before any further CI calls: update PR verification/checklists or tracking state that can now be completed, then report the resulting state.
   - Re-query CI only when the maintainer explicitly asks for independent verification, the PR head changed after the maintainer's update, existing evidence directly conflicts with the update, or exact run data is required for a different concrete task.
   - A maintainer update does not authorize inventing details such as run ids, timestamps, or per-job results. Record only the state they actually confirmed.

10. **Document a reviewer-friendly root cause analysis for every bug fix.**
    - Once the defect is understood and fixed, update the pull request with a detailed Root Cause Analysis before finalization.
    - Write for a reviewer who understands the product/domain but may not know the affected implementation. Do not require prior knowledge of Taskmigo package structure, class names, or internal execution order.
    - Start with **reviewer context**: in 2–4 sentences, explain the affected behavior, why the relevant subsystem exists, and any term the failure story depends on. Introduce implementation names only after their responsibility is clear.
    - When useful, show one concrete input, policy, request, state transition, or configuration that reproduces the defect so the rest of the RCA has a shared example.
    - Explain the **failure mechanism in causal order**, from trigger to user-visible symptom. Prefer numbered steps when the defect crosses multiple components or phases. At every step, say both what the component was expected to guarantee and why the defect was allowed to continue.
    - Identify the **root cause** as the missing/incorrect contract, assumption, state transition, validation boundary, ownership rule, or interaction that made the failure possible. Do not merely rename the final exception or symptom.
    - Explain the **escape and detection gaps**: why existing tests, review assumptions, validation, static analysis, observability, or specification did not catch the defect. Do not use "human error" as a sufficient explanation.
    - Explain the **solution and prevention**: what invariant the fix establishes, why that invariant addresses the cause instead of one failing example, and what regression test or guardrail protects it.
    - Cite source evidence when it materially helps the reviewer verify a claim. Prefer direct links to the relevant production code and regression tests using immutable GitHub permalinks pinned to the **full fixed commit SHA**, for example `https://github.com/taskmigo/taskmigo/blob/<40-char-fixed-sha>/path/to/File.java#L10-L25`. Never use a moving branch such as `next` for RCA code evidence. If later edits change cited code, refresh the permalink to a fixed commit that contains the final cited implementation.
    - Historical/pre-fix links may be added when they are necessary to demonstrate the old behavior, but they supplement rather than replace fixed-commit evidence for the implemented correction.
    - Consider a small Mermaid diagram when the failure path crosses several components, layers, asynchronous steps, or state transitions and prose alone is harder to scan. Use responsibility-first labels, keep the graph focused on the causal path, and make the surrounding prose sufficient without the diagram. Do not add Mermaid for a simple one-step defect merely for decoration.
    - Base the analysis on direct evidence from the implementation, failing behavior, tests, logs, and issue or incident context. Do not infer the cause from commit messages or repeat the Summary/Changes sections as analysis.
    - If evidence is insufficient to establish part of the analysis, state what is unknown and what evidence is missing instead of speculating.
    - Revisit the Root Cause Analysis after every material repair that changes the diagnosis, the failure path, or cited code so the PR never carries a stale explanation or stale permalink.

11. **Preserve machine-actionable PR metadata across title/body rewrites.**
    - Before replacing or substantially rebuilding an existing PR body, read the current PR body and capture machine-actionable directives that are still semantically valid.
    - Treat metadata preservation separately from content generation: Summary, Changes, RCA, and other prose must still be rebuilt from the actual diff/evidence when required, but the old body remains authoritative for existing GitHub directives that would otherwise be lost.
    - At minimum, capture issue-closing directives using GitHub-supported closing keywords such as `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves`, and `resolved`, including every referenced issue.
    - Preserve the exact issue relationship unless the implementation no longer closes that issue, the maintainer explicitly asks to remove/change it, or the issue reference is proven stale.
    - When rebuilding the body from a PR template, reinsert valid closing directives in a stable visible location, preferably at the end of the Summary or as a standalone line immediately after it.
    - After every PR body update, re-read the PR and verify that every expected closing directive is still present. If a directive was accidentally dropped, repair the PR body immediately before doing further PR bookkeeping.
    - Do not treat a plain cross-reference, linked/connected issue relationship, or mention such as `#123` as equivalent to a closing directive. A PR merged into the repository default branch only auto-closes the issue when GitHub receives a valid closing keyword relationship (or the issue is closed separately).
    - For cross-repository issues, preserve the fully qualified `owner/repository#number` form when that was the original relationship.

12. **Use a change freeze before the final CI cycle.**
    - After the implementation is functionally complete, perform one bounded self-review and one complete direct-impact scan before the final implementation push.
    - Search for consumers of changed public contracts: enums, interfaces, DTOs, schemas, adapters, persistence binders, serializers, switch statements, generated artifacts, and tests.
    - Classify discoveries as blockers/required integration gaps versus adjacent improvements. Only the first category belongs in the current PR after the freeze.
    - Apply deterministic formatting before the push when possible.
    - Rewrite/finalize the PR body after the implementation has stabilized, not after every intermediate mutation.
    - Once the final CI cycle starts, do not expand scope because of a new internal code-quality idea. Change the head only for a concrete CI failure, a discovered blocker/required integration gap, a maintainer request, or material new external evidence.

## Pre-CI preflight

Before opening a PR or starting the final implementation CI cycle when no local build runner is available:

1. Search for references to removed, renamed, or extended classes, packages, methods, enums, interfaces, schema contracts, and persistence adapters.
2. For a changed public or cross-module contract, inspect every direct consumer category that can make the change incomplete: switch statements, serializers, persistence binders, translators/visitors, generated artifacts, API documentation, and tests.
3. Inspect compile-sensitive call sites changed by the refactor, especially method references, generic functional interfaces, Spring Data derived-query names, constructor injection, and moved package imports.
4. Confirm that new application/domain packages do not violate the repository architecture rules already visible in tests or `AGENTS.md`.
5. Run the bounded post-implementation self-review now. Resolve blockers and required integration gaps in one batch; defer adjacent improvements.
6. Apply deterministic formatter output when available before the push rather than using CI as the formatter.
7. For multi-file connector edits, verify the final branch tree/ref points to the intended commit before creating or updating the PR.
8. Finalize or substantially rewrite the PR body only after the implementation and cited fixed SHA have stabilized.
9. Do not claim local validation when only static inspection was possible; CI remains the execution evidence.

This preflight does not replace CI. Its purpose is to eliminate obvious compile/reference mistakes before starting an expensive workflow cycle.

## CI triage procedure

### Step 1: Establish the current revision

Capture:

- PR number.
- Base branch.
- Current head branch.
- Current head SHA.

All following CI results must match that SHA.

If the PR changed while you were working, restart CI triage from the new SHA.

### Step 2: Fetch workflow-run summaries

Fetch workflow runs associated with the current head SHA. Keep only compact fields such as:

- Workflow/run id.
- Workflow name.
- Status.
- Conclusion.
- Head SHA.
- URL when useful.

Do not fetch full logs here.

Classify runs into:

- **Actionable:** completed with failure, timed out, or cancelled unexpectedly.
- **Done:** completed successfully or intentionally skipped.
- **Pending:** queued or in progress.

Investigate Actionable first.

### Step 3: Drill into each failed run

For each failed run:

1. Fetch its jobs.
2. Select only failed/timed-out/cancelled jobs.
3. Fetch step summaries for those jobs.
4. Locate the first meaningful failing step.
5. Fetch that job's log only if the step summary does not already identify the fix.
6. Extract a small error-focused window instead of retaining the entire log in context.

When several workflows fail, gather the root cause from all of them before changing code.

### Step 4: Use check-specific fast paths

#### Formatting

- Treat the formatter-emitted diff as source of truth.
- Apply the emitted formatting rather than manually approximating it.
- Do not spend time reasoning about stylistic preferences when the formatter already provides the canonical result.

#### Markdown / YAML

- Read the reported file and line/rule first.
- Fix all reported files in the same pass.
- Avoid fetching unrelated workflow logs.

#### Server / Java

Triage the failing step category before opening large logs:

- Compilation / type checking.
- Checkstyle.
- NullAway/null-safety.
- Unit/integration test.
- Architecture test.
- Build/package failure.

Fix the concrete compiler/static-analysis diagnostic first. Do not infer a broader failure until the reported diagnostic is resolved.

#### Qodana

Treat the Qodana summary/SARIF output as the primary diagnostic channel. The workflow log is usually only useful for determining whether Qodana itself executed successfully.

Use this evidence order:

1. Re-anchor to the current PR head SHA and identify the failed Qodana run for that SHA.
2. Read the PR conversation/check metadata before downloading the job log.
   - Qodana Action commonly posts a bot summary on the PR with the inspection name, severity, and number of new problems.
   - A repository workflow may also publish SARIF results into `$GITHUB_STEP_SUMMARY`. When the available GitHub tooling exposes that summary, read it before any raw log.
   - If the user gives a specific Actions run URL, fetch that exact run first rather than searching for a different run.
3. When detailed SARIF findings are available, extract only the actionable fields:
   - inspection/rule id;
   - message;
   - source file;
   - start/end line or region;
   - severity;
   - count of findings.
   Group findings by inspection and file, then fix all findings supported by the same root cause in one batch.
4. Fetch the failed job log only when:
   - Qodana failed to initialize/import/analyze;
   - the summary is missing;
   - or execution/configuration diagnostics are required.
   Do not expect the raw log to contain contents written to `$GITHUB_STEP_SUMMARY`; those are separate output channels.
5. Treat GitHub Code Scanning as a secondary signal, not a substitute for the Qodana result.
   - A successful Code Scanning upload/check or "no new alerts" message does **not** prove the Qodana Action had zero findings.
   - PR mapping, fingerprints, upload behavior, and Qodana's own `failThreshold` can make those two signals differ.
6. If only an aggregate inspection/count is accessible and exact file/line locations are not:
   - do **not** invent locations;
   - inspect only the current PR diff for patterns that match the named inspection;
   - state internally which part is direct evidence versus correlation;
   - apply a fix only when the inspection semantics and finding count strongly support it;
   - otherwise report that detailed locations are unavailable rather than mutating CI to expose them.
7. Never modify Qodana workflow/configuration merely to obtain diagnostics.
   - Do not add artifact-upload steps, enable `upload-result`, connect Qodana Cloud, change `failThreshold`, alter the profile/excludes, or add suppressions just to make findings visible.
   - Such workflow/config changes are allowed only when the user explicitly asks to improve the CI/Qodana workflow itself, and should normally be made in a separate focused PR.
8. For `Nullability problems`, inspect nullness contracts before changing behavior:
   - enclosing/package `@NullMarked` scope;
   - `@Nullable` annotations;
   - method overrides and generic return contracts;
   - anonymous/test helper implementations;
   - moved/renamed packages that may cause an existing smell to be reported as "new".
   Prefer restoring the intended nullness contract over adding suppressions or defensive behavior changes unrelated to the finding.

Address all supported new Qodana problems in the same batch. Do not weaken static-analysis policy to make the pipeline green.

#### Kubernetes / E2E

- Inspect job steps first to determine whether failure is setup, deploy, readiness, Helm/integration, browser test, or report upload.
- Prefer the generated test/report artifact when the test runner completed.
- Do not block earlier fixes on this typically slower workflow.

#### Performance

- Inspect the benchmark/test threshold result and the concrete regression first.
- Distinguish infrastructure/setup failure from an actual performance threshold failure.

## After a fix is pushed

1. Re-read the PR and capture the **new head SHA**.
2. Forget old run ids as authoritative state.
3. Do not query old in-progress runs again just to see how they finish.
4. Fetch workflow runs for the new SHA once they exist.
5. Apply the same fail-fast triage.
6. If the new SHA has only pending runs and there is no useful non-polling work left, report the pending state and stop.
7. Do not use the pending interval to reopen design exploration that already passed the change freeze. Useful work is limited to evidence/documentation already required by the current change, not discovering optional new scope.

A new commit invalidates every previous current-state conclusion. Old completed diagnostics are historical evidence only.

## Local workspace and network failures

A local clone/build is useful only when the environment supports it.

If a local Git operation fails because the runtime cannot resolve or reach GitHub:

- Do not repeatedly retry clone/fetch with the same network path.
- Continue repository reads/writes through the available GitHub connector/API.
- Use remote CI as execution evidence when local execution is unavailable.
- Be explicit about which verification was remote vs local.

Do not turn an environment networking problem into a repository debugging task.

## GitHub mutation hygiene

- Prefer a separate focused branch for unrelated process/tooling changes rather than appending them to a feature PR.
- Do not send fork-only PR fields for a same-repository PR. In particular, omit `maintainer_can_modify` unless the operation is actually cross-repository and the API requires it.
- Do not run concurrent writes against the same file path.
- Before updating an existing file, fetch its current blob SHA.
- After a write, use the returned commit/blob SHA for any immediately subsequent sequential update.
- For many-file changes, prefer tree-level writes with inline content over per-file blob creation loops.
- When a connector/API operation fails because the orchestration itself is too large, reduce the batch size or switch primitives; do not replay the same oversized orchestration.

## Pull request finalization

Do not finalize the PR description from stale evidence.

Before marking pipeline verification complete, use either a final current-head CI snapshot or an explicit maintainer confirmation that all required checks passed for the current PR.

When using CI as the evidence:

1. Confirm the PR head SHA.
2. Confirm every required check for that SHA is complete and successful.
3. Confirm there are no hidden failed jobs inside a superficially successful workflow.
4. Re-check mergeability if that matters to the task.
5. Update the PR verification/checklist only after this final snapshot.

When the maintainer explicitly confirms all required checks passed:

- Treat that update as current-state evidence for the PR unless the head changes afterward.
- Do not poll again solely to reproduce the same green status.
- Update only the verification facts the maintainer actually confirmed.

When using the repository PR template:

- Build Summary/Changes from the actual code/diff, not commit messages.
- Before replacing an existing PR body, capture valid machine-actionable directives such as `Closes #123`; after the update, re-read the PR and verify those directives were preserved.
- Use pinned specification tags when applicable.
- Record concrete verification evidence.
- Do not mark "all required pipeline checks pass" while any required check is still pending.

## Context and payload discipline

Large GitHub payloads can slow the agent and truncate useful context.

Avoid:

- Full PR diff fetches for status-only questions.
- Full issue/PR comment history when one known bot summary is sufficient.
- Logs for successful jobs.
- Logs for every job in a failed run.
- Printing raw workflow-run/job objects into the conversation.

Prefer compact projections. Conceptually:

```text
runs: id, name, status, conclusion, head_sha
jobs: id, name, status, conclusion
steps: name, status, conclusion
log: only an error-focused excerpt when needed
```

If a response is truncated, immediately switch to smaller targeted calls rather than asking for the same oversized payload again.

## Anti-patterns

Do **not**:

- Poll the entire PR repeatedly while CI is running.
- Poll an old SHA after a new commit has been pushed.
- Issue consecutive status-only requests with no new signal between them.
- Exceed the per-turn repair budget trying to force a green pipeline in one response.
- Create dozens of blobs/tool calls in one orchestration block when one or a few tree writes can represent the same change.
- Wait for the slowest job before inspecting an already-failed fast job.
- Keep checking workflow runs from an old SHA after a push.
- Fix only the first failure when several completed jobs already expose independent failures.
- Re-run CI before determining whether the failure is deterministic code/configuration.
- Re-run all jobs when only one flaky job needs a retry and a targeted retry is supported.
- Fetch full logs before checking job/step summaries.
- Modify a Qodana workflow, threshold, profile, suppressions, or artifact settings solely to extract diagnostics from a failing Qodana run.
- Treat a successful GitHub Code Scanning upload/check as proof that the Qodana Action had no findings.
- Claim exact Qodana file/line locations when only aggregate inspection/count evidence is available.
- Retry a known-broken local Git/network operation several times.
- Update the PR checklist to green before final-head verification or explicit maintainer confirmation.
- Continue polling after the maintainer has explicitly confirmed all required checks passed, unless a documented exception requires fresh CI data.
- Ignore a maintainer interruption and finish an already-started polling loop before processing the new status.
- Assume an API mutation option is valid for both same-repository and fork PRs.
- Rebuild an existing PR body from the template without preserving and verifying still-valid issue-closing directives.
- Run a full adversarial self-review after every repository mutation or CI status change.
- Push a new SHA for each internally discovered cleanup or code-quality idea.
- Squash distinct problem commits together. Clean temporary/mechanical history only within each owning problem, preserving one final reviewable commit per independently reviewable problem.
- Rewrite immutable RCA permalinks after every intermediate commit instead of waiting for a stable fixed SHA.
- Use PR-triggered CI as an interactive implementation loop when one preflight can expose the same direct consumers or integration gaps.

## Decision loop

Use this loop until the current interaction has no further actionable work:

```text
scope frozen + coherent implementation pushed
    -> current PR head SHA
    -> maintainer explicitly confirms all required checks passed?
        yes -> stop polling -> finalize supported PR/tracking bookkeeping -> report state
        no  -> compact workflow-run snapshot
            -> any completed failures?
                yes -> failed jobs -> failed steps -> minimal logs -> collect all causes
                     -> fix as one batch -> targeted validation -> push
                     -> re-anchor to new SHA
                no  -> any useful work while checks run?
                        yes -> do it, then take one fresh snapshot if state may have changed
                        no  -> report exact pending state; do not busy-wait
            -> all required checks successful on current SHA
                -> final PR verification + mergeability check
```

Optimize for **time to first actionable diagnosis**, not time spent observing the pipeline.
