<!-- markdownlint-disable MD041 -->

## Summary

<!--

Briefly explain the core problem and high-level strategy (1–3 sentences).
- Mandatory (AI): You MUST analyze and read the raw code diff/changes directly to infer the underlying problem and strategy.
- Focus on WHAT problem is solved and WHY this approach was taken.
- FORBIDDEN (AI): Do NOT rely on, paraphrase, or summarize commit messages, existing PR titles, or existing PR descriptions.
- FORBIDDEN (AI): Do NOT write meta-commentary (e.g., "This PR introduces changes to..."). Get straight to the point.

-->

## Specification

<!--

Link the Taskmigo specification implemented by this pull request.
- MUST link to a SPECIFIC RELEASE TAG or tagged commit (e.g., https://github.com/taskmigo/specification/tree/v1.2.0/specification).
- FORBIDDEN: Do NOT link to branches (e.g., `main`, `next`, `develop`) as their content shifts over time and invalidates historical context.
- If multiple specifications apply, list each one separately with its corresponding release tag link.
- Write `N/A` only when the change is not implementing or modifying behavior defined by a specification.

-->

## Changes

<!--

List meaningful architectural, functional, or structural modifications based strictly on code analysis.
- Mandatory (AI): Inspect the visual diffs, modified functions, and AST/file structures directly instead of relying on commit messages or metadata.
- Start every bullet point with a capital letter.
- Structural organization:
  - If changes span multiple domains or are extensive, group related items using nested bullets or bold topic headings to improve readability.
  - If changes are few or straightforward, a simple flat list is preferred.
- FORBIDDEN (AI): Do NOT rely on commit log messages to construct this list.
- FORBIDDEN (AI): Do NOT output long, unorganized flat lists when items can be logically grouped by feature, layer, or component.
- FORBIDDEN (AI): Do NOT include Git lifecycle operations (e.g., "Rebase onto branch", "Merge main", "Resolve merge conflicts").
- FORBIDDEN (AI): Do NOT list automated formatting noise (e.g., "Fix trailing whitespace", "Add missing semicolon", "Update lockfile").
- FORBIDDEN (AI): Do NOT list temporary developer actions (e.g., "Add console.log for debugging", "Temporarily disable test").
- FORBIDDEN (AI): Do NOT list file renames, imports reordering, or mechanical code formatting as core changes.

-->

## Root Cause Analysis

<!--

Required for every pull request that fixes a bug, defect, or regression. For changes that do not fix a defect, write `N/A — this pull request does not fix a defect.`

For bug fixes, provide a detailed evidence-based analysis that covers all of the following:
- **Root cause:** Identify the precise technical or design condition that made the defect possible. Do not merely restate the observed symptom.
- **Failure mechanism:** Explain how the root cause produces the reported behavior and under which conditions or execution path it occurs.
- **Escape analysis:** Explain why the defect was introduced or remained unnoticed. Identify the concrete gap in tests, review assumptions, validation, static analysis, observability, specification, or other safeguards. `Human error` alone is not a sufficient explanation.
- **Detection gap:** Explain why the existing automated or manual checks did not catch the defect before it reached the reported state.
- **Solution and prevention:** Explain what the fix changes, why it addresses the root cause instead of only the symptom, and which regression test or guardrail prevents recurrence.

Mandatory (AI):
- Build the analysis from the actual code/diff, failing behavior, tests, logs, and issue or incident evidence.
- Keep the analysis current when later fixes change the diagnosis.
- State unknowns and missing evidence explicitly instead of speculating.
- Do not copy commit messages or repeat the `Summary` / `Changes` sections as a substitute for analysis.

-->

## Verification

<!--

Provide concrete validation evidence.
- IF COVERED BY CI: Write "Covered by automated CI suite."
- IF MANUAL TESTED: Describe exact manual scenarios executed, edge cases checked, or attach screenshots/logs.
- IF NOT APPLICABLE: Use `N/A`.
- FORBIDDEN (AI): Do NOT output single-word vague status like "None", "Nil", "OK", or "Done".
- FORBIDDEN (AI): Do NOT invent or hallucinate test results/metrics that were not explicitly provided in the diff or logs.
- FORBIDDEN (AI): Do NOT list Git metadata or diff statistics (e.g., "Tested 4 files changed with +120/-40 lines").

-->

## Breaking Changes & Risk Assessment

<!--

Explicitly highlight any potential side effects or breaking contracts.
- List DB schema migrations, API contract changes, security impact, or performance risks.
- Write "None" if there are no breaking changes.
- FORBIDDEN (AI): Do NOT guess or assume DB/API stability if new parameters/migrations are present.

-->

## Checklist

- [ ] The PR title follows the sentence format:
  - [ ] Starts with a capital letter (e.g., `"Add OAuth2 login flow for core authentication"`)
  - [ ] Summarizes only the main changes (omits minor fixes or side tasks)
- [ ] The PR title and description reflect the current changes and follow the PR template whenever this pull request is created or updated.
- [ ] The pull request is focused and contains no unrelated changes.
- [ ] The applicable Taskmigo specification is linked to a **specific release tag** (NOT a branch like `main` or `next`), or `N/A` is justified.
- [ ] Tests and documentation are updated where applicable.
- [ ] The change follows the contribution guidelines.
- [ ] All required pipeline checks pass.
