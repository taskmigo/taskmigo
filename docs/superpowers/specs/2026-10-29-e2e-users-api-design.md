# E2E Users API Design

## Intent

Establish the first functional API-focused E2E suite under `e2e/`, starting with the Users API. The suite should exercise production HTTP behavior through the existing browser-authenticated BFF and public Taskmigo E2E SDK rather than duplicating server integration tests or bypassing the browser boundary.

## Goals

- Add a focused `@api` Users API suite.
- Verify create and list behavior through `taskmigo.api.v0.users`.
- Verify pagination and important request validation at deployment level.
- Keep API response contract validation in the SDK's strict Zod schemas.
- Leave a clear pattern for adding later API resources.

## Non-goals

- Broad coverage of every v0 resource in this first PR.
- Reimplementing server-side integration-test matrices such as exhaustive filter/sort syntax coverage.
- Replacing auth/BFF/performance suites.
- Changing production API behavior.

## Constraints

- Tests import only `#taskmigo-sdk`.
- API calls continue through `/api/bff/**` and reuse Playwright's authenticated BrowserContext.
- Production HTTP behavior is the source of truth; generated OpenAPI is not used to generate the SDK.
- Playwright remains the test lifecycle, assertion, tracing, and request owner.
- Tests run serially (`workers: 1`, `fullyParallel: false`) and use unique user data to avoid shared mutable assumptions.

## Approaches

1. **Users API first (selected).** Build functional coverage around the existing `UsersApi`, adding only SDK capabilities needed to express meaningful Users scenarios. Smallest reviewable slice and establishes the API-test convention before expanding resources.
2. **API smoke first.** Add one happy path across many resources. Rejected because most resources do not yet exist in the E2E SDK, making the first test PR mostly SDK scaffolding.
3. **Contract matrix.** Mirror all Users operations and edge cases immediately. Rejected for the first slice because it duplicates substantial server integration coverage and creates a large initial PR.

## Design

Create `e2e/tests/api/users.spec.ts` tagged `@api` and `@users`. Each test signs in through the real browser OAuth flow, then uses the public SDK. Cover observable deployment-level behavior: a valid user can be created and then discovered in a bounded list; pagination metadata and page-size bounds are honored; invalid creation input is rejected by the deployed API.

Extend `UsersApi` only where a functional scenario cannot be expressed through the current `create`/`list` surface. Error-path helpers should return parsed API errors rather than weakening the existing happy-path methods' `failOnStatusCode` guarantees. Do not add generic HTTP abstractions merely for this suite.

Add `test:api` to `e2e/package.json` and document it beside the existing tagged commands in `e2e/README.md`.

## Success criteria

The new API-tagged suite is independently runnable, uses the real auth+BFF path, validates successful Users responses through the existing strict schemas, exercises at least one server-side validation failure, and does not require production-code changes.