# Reading CI evidence

CI is executable evidence about a specific revision and environment, not a general judgment about a pull request. Use this guide to find the meaningful failure information without reading thousands of lines or making a speculative change. The [contribution guide](../../CONTRIBUTING.md) and [repository instructions](../../AGENTS.md) remain authoritative.

## Start with the exact revision

Identify the PR head SHA, base branch, workflow run and attempt. Inspect failed jobs and their first meaningful failing step. A green result for an older head cannot certify a new push. A pending or cancelled run is not a pass, and the absence of a workflow may only mean that its path filters did not match. Check the actual required checks.

## Find the diagnostic owner

- [Formatting](../../.github/workflows/formatting.yaml) runs the repository formatter and produces the canonical `formatting-patch` artifact if tracked files change. Apply the complete patch to its matching revision; review it before pushing.
- [Server](../../.github/workflows/server.yaml) builds and tests the Gradle project. For a build failure, inspect the failed task and `server-build-log-<run_id>-<run_attempt>` artifact. The OpenAPI snapshot verification step is separate: a stale snapshot produces `generated-openapi-<run_id>-<run_attempt>`, containing the generated contract rather than an error log.
- [Qodana](../../.github/workflows/qodana.yaml) analyzes server source. Distinguish startup, import and report-generation failures from actual source findings. Inspect the diagnostics provided by the current workflow and follow the applicable repository rules; do not add suppressions or change analysis policy to hide a defect.
- [Authorization performance](../../.github/workflows/authorization-performance.yaml) runs JMH benchmarks and uploads machine-readable results, human-readable output, logs and profiling recordings. A successful compilation does not establish performance; compare actual benchmark evidence under comparable conditions.
- [Kubernetes integration](../../.github/workflows/kubernetes-integration.yaml) verifies deployment-level behavior. Identify whether the failure occurred during deployment, readiness, test execution or reporting before changing application code.

For exact artifact retrieval and agent-side triage, discover the relevant [repository skills](../../.agents/skills/). Use the workflow at the failing revision to confirm what evidence actually exists; names and report delivery can change over time.

## Prevent stale generated artifacts

A generated contract is a direct consumer of its generator inputs. Identify affected outputs during planning, not after
a test failure. For example, [PR #343](https://github.com/taskmigo/taskmigo/pull/343) changed Ticket API contracts
but did not synchronize the tracked OpenAPI snapshot. The
[Server run 46161133201](https://github.com/taskmigo/taskmigo/actions/runs/46161133201) completed build/tests before
snapshot verification exposed the drift. The canonical artifact repaired the output, but proactive generation would
have made that dependency explicit earlier.

For changes that can affect Taskmigo's OpenAPI contract, run the same generator used by the
[Server workflow](../../.github/workflows/server.yaml) from the repository root:

```sh
cd server
./gradlew --no-daemon --console=plain :apps:openapi-generator:generateOpenApi
git diff -- apps/web/src/main/resources/static/api/docs/openapi.yaml
```

Review the snapshot diff and synchronize the file with the implementation. Include applicable downstream generated
outputs only when the repository actually has them and provides a canonical generator. Review the authored
specification, configuration guide and hand-written SDK consumers as separate dependencies.

When no local runner is available, record the generation as pending and identify the existing CI job before a draft
evidence-collection push. The Server workflow currently runs generation after a successful Gradle build; earlier
build failures can therefore prevent the artifact from existing. Resolve those blockers first. When snapshot
verification reports drift, use the exact-head `generated-openapi-<run_id>-<run_attempt>` artifact and follow the
[OpenAPI artifact skill](../../.agents/skills/github-actions-openapi-snapshot-artifact/SKILL.md). Replace the tracked
snapshot exactly, verify the resulting diff and blob bytes, and re-run required verification on the synchronized head.
Do not fabricate output, reuse an older run's artifact, or skip verification. A connector-only session may still need
a second CI cycle; the improvement is that the dependency is planned and handled at the earliest actionable point.

The [Generated Artifact Impact Check](../../.agents/skills/taskmigo-delivery/SKILL.md#generated-artifact-impact-check-mandatory)
is a delivery policy and is recorded in the PR checklist. Existing executable CI checks remain the verification
authority.

## Example: formatter reports unrelated files

Repository-wide formatting checks can report a file outside the intended change. Before applying a repair, inspect the emitted patch and compare the affected preimages with the base branch. If the same preimage already exists on the base branch, the formatting difference was not introduced by this PR.

Apply the canonical patch as required, and keep an unrelated baseline repair separately reviewable rather than describing it as a feature change. Do not change formatter configuration merely to make the check green.

This occurred during the [AI-DLC rollout](https://github.com/taskmigo/taskmigo/pull/345): four existing Java files were reported by Formatting even though the initial engineering-tooling change touched no server source. The canonical patch established the exact repair, and comparison with the base revision identified the pre-existing drift.

## Closing a verification loop

Batch related repairs, run the affected verification according to repository rules, and re-anchor to the new head after pushing. Re-check all required results before requesting review. When local execution or an artifact is unavailable, record the limitation and the remaining check rather than claiming success.

If a failure exposes a reusable design invariant or recurring diagnostic trap, add the explanation to the owning guide or propose a deterministic check. Do not copy the entire failure log into the knowledge base.
