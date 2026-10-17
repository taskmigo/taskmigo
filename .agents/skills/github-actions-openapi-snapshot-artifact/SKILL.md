---
name: github-actions-openapi-snapshot-artifact
description: Use when Taskmigo's GitHub Actions Server workflow fails because the generated OpenAPI snapshot differs from the checked-in snapshot.
---

# GitHub Actions OpenAPI Snapshot Artifact

Use the generated OpenAPI artifact as the canonical repair source when the Server workflow reports a stale OpenAPI snapshot.

## Required repair order

1. Anchor the repair to the current PR head SHA.
2. Identify the failed `Server` workflow run for that exact SHA, including run id and run attempt, and confirm `Verify OpenAPI snapshot` failed because the generated snapshot differs.
3. List artifacts for that exact run and locate `generated-openapi-<run_id>-<run_attempt>`.
4. Download the artifact and extract `openapi.yaml`.
5. Replace `server/apps/web/src/main/resources/static/api/docs/openapi.yaml` with the artifact file exactly.
   - With a local workspace, copy the extracted file over the checked-in snapshot.
   - With connector-only access, use the extracted artifact content as one complete file replacement.
6. Verify the replacement did not introduce unrelated edits, then update the owning problem commit and use the next `Server` run as verification.
7. Use the job-log diff or regenerate locally only when the current-head artifact is missing, failed to upload, or cannot be read.

## Run identity rules

- The artifact must come from the failed Server run and run attempt for the current PR head SHA.
- If the PR head changes, discard artifacts from older SHAs.
- If OpenAPI generation itself fails before a snapshot mismatch is established, diagnose that failure from the current job log instead of treating an older or unchanged snapshot as canonical.

## Anti-patterns

Do not reconstruct a large generated OpenAPI file from console diff output while a current-head artifact exists. Do not hand-edit generated sections. Do not use an artifact from another run, attempt, or SHA merely because its filename looks similar.
