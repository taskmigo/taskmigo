---
name: github-actions-formatting-patch-artifact
description: Use when Taskmigo's GitHub Actions Formatting workflow fails or an agent needs to repair Prettier-generated formatting differences.
---

# GitHub Actions Formatting Patch Artifact

Use the Formatting workflow's generated patch artifact as the canonical repair source for Prettier failures.

## Required repair order

1. Anchor the repair to the current PR head SHA.
2. Identify the failed `Formatting` workflow run for that exact SHA.
3. List artifacts for that run and locate `formatting-patch`.
4. Download the artifact and extract `formatting.patch`.
5. **Apply the whole patch instead of manually fixing files.**
   - With a local workspace, run `git apply --check formatting.patch` and then `git apply formatting.patch`.
   - With connector-only repository access, treat the artifact patch as the exact change set and apply it in one focused tree-level mutation rather than reconstructing formatter output file by file.
6. Verify the resulting diff contains only the formatter changes represented by the artifact, then push the owning problem commit and use the next `Formatting` run as verification.
7. Use the `Formatting patch for AI agents` log section only when the artifact is missing, failed to upload, or cannot be read.

This artifact-first ordering overrides the generic Formatting fallback in `github-pr-ci-workflow`.

## Run identity rules

- The patch artifact must come from the failed Formatting run for the current PR head SHA.
- If the PR head changes, discard the old artifact and fetch the artifact produced for the new head.
- If `git apply --check` fails, first verify that the patch and workspace belong to the same head SHA. Do not resolve patch rejects by manually approximating Prettier output.

## Anti-patterns

Do not manually reformat one file at a time while a current-head `formatting-patch` artifact exists. Do not infer Prettier layout from style preferences. Do not install or rerun formatting tooling solely to recreate output already captured in the artifact. Do not use a patch artifact from an older SHA as current-state repair input.
