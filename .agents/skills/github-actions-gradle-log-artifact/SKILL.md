---
name: github-actions-gradle-log-artifact
description: Use when investigating a failed GitHub Actions Server or Gradle job in Taskmigo, especially when the raw job log is large, truncated, slow to retrieve, or unavailable through the GitHub connector.
---

# GitHub Actions Gradle Log Artifact

Use the generated Gradle log artifact as the primary diagnostic source for Taskmigo Server workflow failures.

## Required evidence order

1. Anchor the investigation to the current PR head SHA.
2. Identify the failed `Server` workflow run, its run id, run attempt, failed job, and failed step.
3. If the failure is the Gradle build, list artifacts for that exact run and locate `server-build-log-<run_id>-<run_attempt>`.
4. Download the artifact and read `server-build.log` as a file. Search the file for targeted markers such as `FAILURE:`, `What went wrong`, `Execution failed for task`, `FAILED`, `error:`, `Caused by:`, `There were failing tests`, and `Checkstyle rule violations`.
5. Retain only the small error-focused windows needed for diagnosis. Do not load the whole file into agent context when targeted search is sufficient.
6. Fetch the raw GitHub Actions job log only when the artifact is missing, failed to upload, or the failure occurred before the Gradle log file was produced.

This ordering overrides the generic raw-job-log fallback in `github-pr-ci-workflow` for Taskmigo Server/Gradle failures.

## Run identity rules

- The artifact must belong to the same workflow run and run attempt being diagnosed.
- Confirm that run belongs to the current PR head SHA before treating its contents as current-state evidence.
- Never substitute an artifact from another run merely because the job name is the same.
- Artifacts from older SHAs are historical evidence only after the PR head changes.

## Anti-patterns

Do not repeatedly request a large raw Gradle job log after it is truncated or retrieval fails. Do not infer a Gradle failure from the PR diff when the artifact is available. Do not use the short console tail or `Agent failure summary` as a replacement for `server-build.log` when the artifact can be downloaded.
