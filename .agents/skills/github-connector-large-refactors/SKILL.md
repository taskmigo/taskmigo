---
name: github-connector-large-refactors
description: Use when moving, renaming, or modifying many repository files through GitHub connector/API without a writable local Git push path, especially package-tree refactors, large mechanical moves, or edits expected to require many repository reads or mutations.
---

# GitHub Connector Large Refactors

Use this with **github-pr-ci-workflow** when the change is connector-driven and broad.

## Core invariant

**Intermediate blobs and trees are not progress.** Progress means a durable branch commit whose diff has been checked against the intended scope. A tree SHA that is not reachable from the branch is only temporary state.

## Before the first mutation

1. Anchor the exact branch head SHA and base tree SHA.
2. Build an explicit move/edit manifest: old path -> new path plus the mechanical replacement rules.
3. Count affected files. If more than 8 files are involved, or a whole package/tree moves, do not use per-file Contents API edits.
4. Estimate connector work needed before the next durable commit. If the estimate exceeds 12 GitHub calls or 3 intermediate tree writes, change strategy before starting.
5. For large source files, fetch the exact blob by SHA. Never reconstruct a file from truncated tool output or manually splice partial snippets when the full blob is available.

## Execution contract

For broad mechanical refactors:

- Prefer one bulk Git tree commit. Use a local/scratch snapshot for mechanical transforms and diff inspection when available, even if pushing still requires the connector.
- Batch independent reads; do not serially fetch one file at a time when they can be collected together.
- Use at most 3 intermediate tree objects before creating the commit. If a fourth tree seems necessary, stop and consolidate or switch approach.
- After 2 connector batches without a durable commit, run a checkpoint: compare the manifest with staged paths, identify what remains, and either create the commit or abandon the temporary tree and use a better strategy.
- Do not send more than one consecutive progress update whose only result is “still staging/moving files.” The next update must contain a durable commit, a concrete verified finding, or a strategy change.

## Hard stop: stall detection

Stop the current tool pattern immediately when any condition becomes true:

- 12 GitHub calls since the last durable commit with no branch-visible result;
- 3 intermediate trees created without a commit;
- the same file/resource is fetched more than twice without new evidence;
- a tool response is truncated and the next action would manually reconstruct missing content;
- two consecutive batches produce only preparation state and no verifiable deliverable.

Do **not** make “one more” similar call. Re-plan using a bulk tree, exact blob fetches, a local snapshot/script, a smaller coherent change boundary, or report the concrete blocker.

## Commit gate

Before moving the branch ref:

1. Search the staged tree for retired paths/references required to disappear in this task.
2. Compare the new commit against its parent and inspect changed-file scope.
3. Confirm mechanical moves contain only intended package/import/path edits unless the task explicitly allows semantic changes.
4. Update the branch with an expected-head lease.

## Rationalizations to reject

| Rationalization | Reality |
| --- | --- |
| “The intermediate tree means I am almost done.” | It is unreachable temporary state, not a deliverable. |
| “I already spent many calls, so finishing this way is cheaper.” | Sunk cost is not evidence that the strategy remains efficient. |
| “Atomic commit means I must stage every file manually.” | Atomic history does not require unbounded manual connector work. |
| “Connector-only means per-file editing.” | Git trees, exact blobs, batching, and local scratch transforms are available strategies. |
| “One more tree should finish it.” | Crossing the tree budget is the signal to change strategy. |
