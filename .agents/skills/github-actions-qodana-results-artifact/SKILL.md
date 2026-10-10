---
name: github-actions-qodana-results-artifact
description: Use when investigating a failed Taskmigo Qodana workflow run, especially when detailed findings are needed and raw GitHub Actions logs are large, truncated, slow to retrieve, or unavailable through the GitHub connector.
---

# GitHub Actions Qodana Results Artifact

Use the generated Qodana results artifact as the primary file-based diagnostic source for failed Taskmigo Qodana runs.

## Required evidence order

1. Anchor the investigation to the current PR head SHA.
2. Identify the failed Qodana workflow run for that SHA and record its run id and run attempt.
3. List artifacts for that exact failed run and locate `qodana-results-<run_id>-<run_attempt>`.
4. Download the artifact and read `qodana-results.json` as a file. It contains only the Qodana result objects needed for source-finding triage, without the rest of the SARIF document.
5. Search or query only the fields needed for diagnosis, especially `ruleId`, `message`, `level`, and `locations`. Group actionable findings by rule and source file before changing code.
6. Keep only small finding-focused windows in agent context rather than loading the complete results file when targeted search is sufficient.
7. Fetch the raw `Analyze` job log only when the results artifact is missing, the artifact failed to upload, Qodana failed before producing SARIF/results, or execution/import/configuration diagnostics are required.

Successful Qodana runs intentionally produce neither a diagnostic artifact nor a Qodana job-summary payload. Absence of `qodana-results-<run_id>-<run_attempt>` on a successful run is expected.

This ordering overrides the generic Qodana log fallback in `github-pr-ci-workflow` when the exact-run results artifact is available.

## Run identity rules

- The artifact must belong to the same failed Qodana workflow run and run attempt being diagnosed.
- Confirm that run belongs to the current PR head SHA before treating its contents as current-state evidence.
- Never substitute a results artifact from another run merely because the workflow or job name is the same.
- Artifacts from older SHAs are historical evidence only after the PR head changes.

## Interpretation rules

- `qodana-results.json` intentionally contains only `.runs[].results[]` from Qodana SARIF. Tool metadata, rule catalogs, invocation metadata, and other SARIF sections are not duplicated into the artifact.
- GitHub Code Scanning remains a secondary signal because upload/mapping behavior can differ from Qodana's own finding set.
- Distinguish source findings from Qodana bootstrap, project import, classpath, cache, or execution failures. When Qodana never produced results, diagnose those failures from step metadata or the raw job log instead of inventing source locations.
- Do not weaken Qodana profiles, thresholds, excludes, or suppressions merely to make CI green.

## Anti-patterns

Do not repeatedly request the large raw Qodana job log when `qodana-results.json` is available as an artifact. Do not download the full SARIF merely to recover findings already present in the results artifact. Do not infer file/line locations from the PR diff when the result objects contain them. Do not treat a missing results artifact on a successful run as a failure.
