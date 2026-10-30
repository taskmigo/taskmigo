# E2E Users API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build compact, high-signal deployment-level coverage for every current Users API operation while avoiding a noisy one-test-per-input suite.

**Architecture:** Exercise the public `#taskmigo-sdk` through the authenticated BFF. Use lifecycle scenarios for stateful behavior and small parameterized matrices for negative inputs that share a failure path. Keep official resource methods one-to-one with server operations and put test conveniences under `UsersApiExtensions`.

**Tech Stack:** Node.js 26.7.0, TypeScript 5.9, Playwright 1.62, Zod 4.6

**Spec:** `docs/superpowers/specs/2026-10-29-e2e-users-api-design.md`

## Global Constraints

- Tests import only `#taskmigo-sdk`.
- API requests traverse `/api/bff/**` with the authenticated BrowserContext.
- Production HTTP behavior is the contract source of truth.
- Keep the PR scoped to Users API E2E coverage; do not change production behavior.
- Preserve serial Playwright execution and unique test data.
- Do not assume database emptiness, fixed ordering, or page-one placement.
- Prefer 6-10 high-value scenarios; parameterize equivalent validation failures.
- API E2E checks critical authorization invariants, not the full policy-engine matrix.

## Review Focus

- Created-user discovery must remain correct with more than one page of pre-existing users; pin in Task 1.
- Negative helpers must not masquerade as OpenAPI operations or weaken fail-fast success methods; pin in Task 1.
- Query tests must prove representative pagination/filter/sort integration without duplicating query-parser grammar tests; pin in Task 2.
- Delete semantics must respect tombstone/retention behavior rather than assuming physical deletion; pin in Task 4.
- Authorization tests must detect object-visibility/mutation leaks while avoiding duplicate auth/BFF mechanics; pin in Task 5.

---

### Task 1: Correct SDK boundaries and stable fixture discovery

**Files:**

- Modify: `e2e/sdk/api/v0/users.ts`
- Modify: `e2e/sdk/api/v0/index.ts` if public types change
- Modify: `e2e/tests/api/users.spec.ts`

**Interfaces:**

- Consumes: existing `UsersApi.create()` and `UsersApi.list()`.
- Produces: official operation methods plus `UsersApiExtensions` conveniences for expected errors and bounded discovery.

- [ ] **Step 1: Write/adjust the failing spec for stable discovery and extension-only error behavior**

Make the create/list scenario discover its created id without assuming it appears in the first 100 records. Change negative test calls so SDK-only expected-error behavior is reached through `users.extensions`.

- [ ] **Step 2: Execute RED**

Run `npx playwright test tests/api/users.spec.ts` and `npm run typecheck` from `e2e/`. Expected before SDK correction: missing extension/discovery interface or type failure.

- [ ] **Step 3: Correct `UsersApi`/`UsersApiExtensions`**

Keep only real server operations on `UsersApi`. Add the smallest extension methods needed to submit an expected-error create and to find a user across bounded pagination. Bound traversal using pagination metadata rather than an arbitrary infinite loop.

- [ ] **Step 4: Execute GREEN**

Run `npm run typecheck` and the Users spec. Expected: PASS.

- [ ] **Step 5: Commit the SDK-boundary correction**

Commit SDK and directly dependent test changes together.

### Task 2: Create/list/query high-signal scenarios

**Files:**

- Modify: `e2e/tests/api/users.spec.ts`
- Modify: `e2e/sdk/api/v0/users.ts` only for actual list query parameters supported by the production operation

**Interfaces:**

- Consumes: stable create/list/discovery surface from Task 1 and current `ListQueryOptions` behavior.
- Produces: lifecycle, query, conflict, and compact validation coverage.

- [ ] **Step 1: Inspect current Users query schema and existing server tests**

Identify the supported Users filter/sort fields/operators and existing query boundary coverage. Select only representative combinations that cross the deployed controller/query/persistence path.

- [ ] **Step 2: Add failing create/list lifecycle and representative query assertions**

Use a controlled set of uniquely named users. Assert persisted identity/default fields, pagination totals/bounds relevant to the controlled query, representative filter behavior, and deterministic sort behavior without depending on unrelated global records.

- [ ] **Step 3: Add failing uniqueness conflict scenario**

Create once, repeat the uniqueness-sensitive request, assert the deployed conflict status/error envelope, then query to prove no second observable record was created.

- [ ] **Step 4: Add compact validation matrix**

Use named rows for materially distinct constraints: null/blank required scalar, invalid email, blank email element, and null role/group element where the wire request can express it. Keep rows sharing the same validation path in one parameterized scenario and assert status plus standard error envelope.

- [ ] **Step 5: Implement only required SDK query/error surface and execute GREEN**

Run `npm run typecheck` and `npx playwright test tests/api/users.spec.ts`. Expected: PASS.

- [ ] **Step 6: Commit create/list/query coverage**

Keep query SDK additions with the scenarios that require them.

### Task 3: Statement replacement lifecycle

**Files:**

- Modify: `e2e/sdk/api/v0/users.ts`
- Modify: `e2e/tests/api/users.spec.ts`

**Interfaces:**

- Consumes: authenticated Users resource and any existing public statement resource needed to create valid statement ids.
- Produces: `setStatements` operation coverage and observable replace/clear/not-found behavior.

- [ ] **Step 1: Determine public observability and fixture path**

Inspect the statement APIs and current E2E SDK. If direct statement assignment has no public read-side effect that can be observed safely, document that limitation and restrict assertions to the strongest public behavior available rather than adding a test-only production endpoint.

- [ ] **Step 2: Add the real `setStatements` SDK operation with strict success schema**

Match `PATCH /users/{userId}/statements`, including nullable/empty `statementIds` semantics, while retaining `failOnStatusCode: true` for success calls.

- [ ] **Step 3: Add lifecycle scenario**

Create a user, assign valid direct statements, replace or clear them, and assert the strongest public observable effect. Include nonexistent-user behavior in the same resource area only if it reaches a distinct controller/service path.

- [ ] **Step 4: Execute GREEN and commit**

Run typecheck and Users spec; commit operation plus scenario together.

### Task 4: Delete/tombstone lifecycle

**Files:**

- Modify: `e2e/sdk/api/v0/users.ts`
- Modify: `e2e/tests/api/users.spec.ts`

**Interfaces:**

- Consumes: create/discovery helpers and current User retention specification/configuration behavior.
- Produces: `delete` operation coverage for observable state transition, repeat operation, and not-found behavior.

- [ ] **Step 1: Re-read current retention/delete specification and deployed configuration contract**

Pin the test to current semantics for retention `0` versus positive retention. Do not assert physical row deletion unless the specification requires it for the active configuration.

- [ ] **Step 2: Add the real `delete` SDK operation with strict success parsing**

Match `DELETE /users/{userId}` and keep success/error behavior separated according to the SDK convention established in Task 1.

- [ ] **Step 3: Add delete lifecycle scenario**

Create a disposable user, delete it, verify externally observable retained/hidden/purged behavior, then exercise repeat delete only where the current specification defines the result.

- [ ] **Step 4: Add nonexistent-id boundary if not already covered by the lifecycle**

Use a valid random UUID so the case reaches the resource-not-found path rather than URI conversion failure.

- [ ] **Step 5: Execute GREEN and commit**

Run typecheck and Users spec; commit operation plus scenario together.

### Task 5: Critical authorization and protocol boundaries

**Files:**

- Modify: `e2e/tests/api/users.spec.ts`
- Modify SDK only if an already-supported E2E identity/session needs a public helper

**Interfaces:**

- Consumes: Users list/delete/setStatements operations and E2E identities already supported by repository fixtures/environment.
- Produces: a minimal externally observable security boundary without recreating policy-engine tests.

- [ ] **Step 1: Inspect existing auth/BFF/policy tests and available E2E identities**

Choose one Users visibility invariant and one mutation invariant only when the environment can express them deterministically. Do not invent credentials or policies in the test.

- [ ] **Step 2: Add authorization scenario(s)**

Assert that an unauthorized object is absent from list results or a forbidden mutation is rejected, whichever current fixtures/specification make deterministic. Avoid retesting login/session mechanics.

- [ ] **Step 3: Check protocol boundary ownership**

Add malformed UUID or unsupported media-type coverage only if existing common-error/BFF tests do not already prove the same deployed behavior. Otherwise document the deliberate omission in the design/PR.

- [ ] **Step 4: Execute GREEN and commit**

Run typecheck and Users spec; keep only cases with distinct deployment-level signal.

### Task 6: API entry point, documentation, and final verification

**Files:**

- Modify: `e2e/package.json`
- Modify: `e2e/README.md`
- Inspect: `.github/pull_request_template.md`
- Inspect: final branch diff and direct SDK consumers

**Interfaces:**

- Consumes: completed Users suite.
- Produces: stable `npm run test:api`, reviewer-ready PR, and verified CI evidence.

- [ ] **Step 1: Keep `test:api` and README aligned with actual suite**

Document the `@api` tag and command without overstating unsupported coverage.

- [ ] **Step 2: Run package/repository verification**

Run `npm run typecheck`, `npm run test:api` against the deployed E2E environment, and repository `npm run format:check` where execution is available. If only CI can execute the deployment tests, report that distinction explicitly.

- [ ] **Step 3: Review for signal-to-noise**

For every scenario, identify its distinct failure mode/integration boundary. Merge parameterizable duplicates and remove tests whose behavior is already fully owned by an existing deployment-level suite.

- [ ] **Step 4: Review direct SDK consumers and final diff**

Confirm official Users methods still map one-to-one to operations, extension helpers remain conveniences, strict parsing is active, and no production behavior changed.

- [ ] **Step 5: Update PR description and monitor required CI by current head SHA**

Keep verification/checklist truthful. Inspect completed failures before fixes and do not busy-wait on in-progress jobs.

- [ ] **Step 6: Finish only with passing required checks or an explicit external blocker**

Do not claim execution evidence that was not observed.
