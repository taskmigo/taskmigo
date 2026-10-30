# E2E Users API Design

## Intent

Establish a high-signal API-focused E2E suite under `e2e/`, starting with the Users API. The suite should catch subtle deployment-level regressions through the existing browser-authenticated BFF and public Taskmigo E2E SDK without becoming a noisy mirror of server unit/integration tests.

## Goals

- Cover every public Users API operation: create, list, delete, and replace direct statements.
- Prefer 6-10 high-value scenarios over one test per input value.
- Exercise complete observable lifecycles so a successful HTTP response is also checked for persisted behavior.
- Cover validation, conflicts, query semantics, destructive state transitions, and critical authorization invariants.
- Keep strict Zod contract validation in the SDK.
- Establish a reusable pattern for later API resources.

## Non-goals

- Exhaustively enumerating every invalid value or every filter/sort grammar production.
- Duplicating authentication mechanics already owned by the auth/BFF suites.
- Reimplementing policy-engine unit/integration coverage; API E2E covers only externally observable security invariants.
- Changing production API behavior to make tests pass.
- Broad coverage of every v0 resource in this PR.

## Constraints

- Tests import only `#taskmigo-sdk`.
- API calls continue through `/api/bff/**` and reuse Playwright's authenticated BrowserContext.
- Production HTTP behavior is the source of truth; generated OpenAPI is not used to generate the SDK.
- Playwright remains the test lifecycle, assertion, tracing, and request owner.
- Tests run serially (`workers: 1`, `fullyParallel: false`) and use unique data.
- Tests must not depend on database emptiness, fixed global ordering, or a created record appearing on the first page.
- SDK-only helpers belong behind the resource extension surface; OpenAPI operation methods remain one-to-one with server operations.

## Approaches

1. **One test per behavior.** Easy to diagnose, but rejected because validation/query combinations produce a large noisy suite with substantial duplicate setup and HTTP paths.
2. **Lifecycle scenarios only.** Compact and strong for integration defects, but weak at systematically covering negative boundaries.
3. **Lifecycle + compact negative matrix (selected).** Use a small number of lifecycle scenarios for stateful behavior and parameterized matrices only when inputs share the same failure path. This maximizes defect detection per test while keeping the suite reviewable.

## Coverage model

Each scenario must justify itself by reaching a distinct failure mode or integration boundary. Values that only exercise the same validation branch are rows in one compact matrix, not separate tests. Conversely, cases that cross a different parser, query, persistence, policy, or state-transition path remain separate even if their inputs look similar.

Target Users coverage:

1. **Create/list lifecycle.** Create a unique user, discover it without assuming page-one placement, and verify identity fields, defaults, status, response envelope, and persisted visibility.
2. **Query semantics.** In one controlled dataset, verify pagination metadata plus representative filter/sort behavior supported by the deployed Users query schema, including deterministic ordering/boundary behavior. Do not duplicate the entire query grammar.
3. **Create conflict.** Repeat a uniqueness-sensitive create and assert the conflict contract and that no duplicate observable user was created.
4. **Create validation matrix.** Parameterize representative null/blank/invalid-email/null-collection-element cases that exercise materially different Jakarta validation constraints. Assert status and standard error envelope for every row.
5. **Statement replacement lifecycle.** Create a user, assign valid direct statements, replace/clear them, and verify observable effects through public APIs where the current product exposes them. Include nonexistent-user behavior. Do not add assertions for state that cannot be observed publicly.
6. **Delete lifecycle.** Create, delete, then verify the externally specified retained/deleted visibility and repeat-operation behavior according to current retention configuration/specification. Include nonexistent-user behavior without assuming physical row deletion.
7. **Authorization invariant.** Verify at least one object-authorization boundary on Users list/mutation using identities already supported by the E2E environment. Do not reproduce the policy-engine syntax matrix here.
8. **Protocol/identifier boundaries.** Cover malformed/nonexistent UUID and unsupported media type only where they exercise framework/controller behavior not already proven by an existing BFF/common-error suite.

Before implementation, compare these scenarios with existing server and E2E tests and remove cases that provide no additional deployment-level signal.

## SDK design

Keep official operation methods aligned with server operations: `list`, `create`, `delete`, and `setStatements` (exact SDK naming should follow the repository's operation-id convention). Successful operation methods keep `failOnStatusCode: true` and strict Zod parsing.

Expected-error submission and multi-page discovery are SDK conveniences, so they must live under `UsersApiExtensions` rather than appearing as fake API operations on `UsersApi`. Error helpers parse the standard error envelope without weakening successful methods. A discovery helper may paginate until a matching id/unique username is found, bounded by response pagination metadata, so tests never rely on page-one placement.

Only add SDK surface required by an approved scenario; do not introduce a generic HTTP abstraction for test convenience.

## Test organization

Keep `e2e/tests/api/users.spec.ts` as the Users resource suite, tagged `@api` and `@users`. Prefer descriptive scenario names and local helpers for unique fixtures. Parameterized validation rows should report the row name in the assertion/test title so CI failures remain diagnosable.

Add/retain `test:api` in `e2e/package.json` and document it beside existing tagged commands.

## Success criteria

- Every current Users controller operation has meaningful deployment-level coverage or an explicit reason it cannot yet be observed safely from E2E.
- The suite stays within roughly 6-10 high-value scenarios rather than expanding into dozens of single-value cases.
- No scenario depends on database emptiness or incidental global ordering.
- Successful responses remain strict-schema parsed; negative responses validate the standard error contract.
- Stateful scenarios verify post-operation observable state, not only HTTP status.
- Critical object-authorization behavior is represented without duplicating the policy-engine suite.
- No production-code change is made merely to satisfy E2E tests.
