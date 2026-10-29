# E2E Users API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first functional API-focused E2E suite, covering core Users API behavior through Taskmigo's real browser-authenticated BFF.

**Architecture:** Keep Playwright as lifecycle/transport owner and exercise the public `#taskmigo-sdk` boundary. Extend `UsersApi` only for error behavior that the current fail-fast happy-path methods cannot expose, then add a tagged Users spec and an independently runnable `test:api` command.

**Tech Stack:** Node.js 26.7.0, TypeScript 5.9, Playwright 1.62, Zod 4.6

**Spec:** `docs/superpowers/specs/2026-10-29-e2e-users-api-design.md`

## Global Constraints

- Tests import only `#taskmigo-sdk`.
- API requests traverse `/api/bff/**` with the authenticated BrowserContext.
- Production HTTP behavior is the contract source of truth.
- Keep the PR scoped to Users API E2E coverage; do not change production behavior.
- Preserve serial Playwright execution and unique test data.

## Review Focus

- Duplicate or invalid create requests must surface the deployed API error rather than be hidden by SDK fail-fast handling; pin in Task 1.
- Page-size boundary behavior must be asserted from response pagination metadata; pin in Task 1.
- Created-user discovery must not assume global database emptiness or fixed ordering; pin in Task 1 with unique usernames and bounded pagination.
- Unauthenticated behavior remains owned by the existing BFF suite; ensure the API suite always authenticates before functional calls.
- Strict Zod response parsing must remain active on successful calls; use the existing SDK methods rather than raw requests for success scenarios.

---

### Task 1: Functional Users API E2E suite

**Files:**
- Create: `e2e/tests/api/users.spec.ts`
- Modify: `e2e/sdk/api/v0/users.ts`
- Modify: `e2e/sdk/api/v0/index.ts` only if a new public error response type/schema needs export

**Interfaces:**
- Consumes: `Taskmigo.signIn()`, `taskmigo.api.v0.users.create(CreateUserRequest)`, `taskmigo.api.v0.users.list(ListUsersRequest)`.
- Produces: Users API functional tests and the smallest SDK error-path method needed to assert server validation without raw request duplication in tests.

- [ ] **Step 1: Write the failing Users API spec**

Add tests named for observable behavior: valid creation can be found in the user list with matching identity fields/status; list honors requested `page`/`pageSize` metadata; invalid create input returns the deployed validation status/error contract. Use `randomUUID()` for unique usernames/emails.

- [ ] **Step 2: Execute the RED suite**

Run the new spec against the deployed E2E environment before implementing the missing SDK error-path capability. Expected: the invalid-create scenario cannot pass through the current `UsersApi.create()` fail-fast contract; capture this RED evidence in CI if local deployment execution is unavailable.

- [ ] **Step 3: Implement the minimal SDK error-path capability**

Keep `create()` unchanged for successful requests. Add a narrowly named method/type for submitting a create request when a non-2xx response is expected, parsing the standard API error envelope with Zod. Do not introduce a generic transport layer.

- [ ] **Step 4: Execute the GREEN spec and typecheck**

Run `npm run typecheck` and `npx playwright test tests/api/users.spec.ts` in `e2e/`. Expected: typecheck and all Users API tests pass against the deployed environment.

- [ ] **Step 5: Commit the coherent Users API test slice**

Keep the spec, minimal SDK support, and its tests in one reviewable commit.

### Task 2: API test entry point and documentation

**Files:**
- Modify: `e2e/package.json`
- Modify: `e2e/README.md`

**Interfaces:**
- Consumes: `@api` tag established by Task 1.
- Produces: `npm run test:api` as the stable command for API-focused E2E execution.

- [ ] **Step 1: Add `test:api`**

Set the script to `playwright test --grep @api`.

- [ ] **Step 2: Document API coverage and command**

Update the suite description/tag list and run section to include `@api` and `npm run test:api` without changing the existing auth/BFF/performance guidance.

- [ ] **Step 3: Verify package typecheck and formatting-sensitive files**

Run `npm run typecheck` from `e2e/` and repository `npm run format:check` when a local runner is available; otherwise rely on the corresponding PR CI and report that execution evidence accurately.

- [ ] **Step 4: Commit documentation/entry-point slice**

Keep command/documentation changes together.

### Task 3: PR verification and CI

**Files:**
- Inspect: `.github/pull_request_template.md`
- Inspect: final branch diff and direct SDK consumers

**Interfaces:**
- Consumes: completed Tasks 1–2.
- Produces: reviewer-ready PR with current metadata and required checks passing.

- [ ] **Step 1: Perform bounded direct-impact review**

Inspect every `UsersApi` consumer and confirm the new capability does not weaken existing create/list behavior or bypass strict success parsing.

- [ ] **Step 2: Create/update PR from the final diff**

Use a plain-language title and the repository PR template. Specification is `N/A` because this PR adds automation coverage without modifying specified product behavior.

- [ ] **Step 3: Monitor CI by current head SHA**

Inspect completed failures first; batch related fixes into their owning problem commit and do not busy-wait on in-progress jobs.

- [ ] **Step 4: Finish only when required checks pass or report an external blocker**

Do not claim local execution when only connector/static inspection was available.
