---
name: github-pr-ci-workflow
description: "Drive GitHub pull requests and CI to completion efficiently. Use whenever an agent creates or updates a PR, checks GitHub Actions/pipeline status, investigates failed checks, pushes CI fixes, or is asked to continue until CI is healthy. Enforces SHA-anchored, fail-fast CI triage; small targeted GitHub queries; batched fixes; and non-blocking status checks. Avoids stale workflow runs, repeated full-PR/log fetches, busy waiting, unnecessary reruns, and retrying unavailable local Git/network paths."
---

# GitHub PR and CI Workflow

Use this workflow for PR implementation, CI triage, and final verification.

The goal is not to "watch CI." The goal is to extract the earliest actionable failure, fix all currently known failures together, and minimize expensive or redundant GitHub operations.

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

4. **Batch fixes.**
   - Collect every currently known failure from the same head SHA.
   - Fix them in one coherent change when possible.
   - Run the smallest relevant local checks when a local workspace is available.
   - Push once, then start a fresh SHA-anchored CI cycle.
   - Avoid one-failure/one-commit loops unless later failures were genuinely hidden by an earlier failure.

5. **Never busy-wait for CI.**
   - Do not use long blocking wait/sleep operations.
   - Do not repeatedly request the same in-progress status with no intervening work or new signal.
   - If only in-progress checks remain, use the current turn for useful work: code review, diff inspection, PR documentation, or validation of already-completed jobs.
   - If there is nothing actionable, report the exact current status rather than spinning on GitHub.

6. **Treat tool timeout as a query-design problem first.**
   - After an expensive request times out or returns an oversized/truncated payload, do not immediately repeat the same request.
   - Switch to a narrower endpoint or smaller resource.
   - Retry the identical expensive operation at most once, and only when there is evidence the failure was transient.

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

- Read the Qodana PR summary/comment or SARIF summary before full job logs.
- Address all reported new problems in the same batch.
- Only inspect raw Qodana logs when the summary is insufficient or Qodana itself failed to execute.

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
3. Fetch workflow runs for the new SHA.
4. Apply the same fail-fast triage.
5. Do not continue monitoring workflow runs tied to the previous SHA.

A new commit invalidates the previous "all green" conclusion.

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

## Pull request finalization

Do not finalize the PR description from stale evidence.

Before marking pipeline verification complete:

1. Confirm the PR head SHA.
2. Confirm every required check for that SHA is complete and successful.
3. Confirm there are no hidden failed jobs inside a superficially successful workflow.
4. Re-check mergeability if that matters to the task.
5. Update the PR verification/checklist only after this final snapshot.

When using the repository PR template:

- Build Summary/Changes from the actual code/diff, not commit messages.
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
- Wait for the slowest job before inspecting an already-failed fast job.
- Keep checking workflow runs from an old SHA after a push.
- Fix only the first failure when several completed jobs already expose independent failures.
- Re-run CI before determining whether the failure is deterministic code/configuration.
- Re-run all jobs when only one flaky job needs a retry and a targeted retry is supported.
- Fetch full logs before checking job/step summaries.
- Retry a known-broken local Git/network operation several times.
- Update the PR checklist to green before final-head verification.
- Assume an API mutation option is valid for both same-repository and fork PRs.

## Decision loop

Use this loop until the current interaction has no further actionable work:

```text
current PR head SHA
    -> compact workflow-run snapshot
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
