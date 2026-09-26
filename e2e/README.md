# Taskmigo E2E

`e2e/` is one Node/TypeScript package. Reusable automation lives under `sdk/`; Playwright specs and scenario support live under `tests/`.

Tests import only the public SDK boundary:

```ts
import { expect, test } from "#taskmigo-sdk";
```

The suite covers browser authentication, browser API BFF behavior, and deployment-level performance regressions. Feature tags include `@auth`, `@bff`, `@performance`, and `@smoke`.

## Source of truth

Taskmigo production code is the source of truth for HTTP behavior. The SDK does not depend on generated `openapi.yaml`; OpenAPI remains a derived documentation and review artifact.

Endpoint resources are versioned by the production HTTP API namespace. The current contract lives under `sdk/api/v0/`, is exposed at `taskmigo.api.v0`, and validates responses with strict Zod schemas before returning them to tests. TypeScript response types are inferred from those same schemas.

Every API method under `taskmigo.api.*` is explicitly classified. Methods that map one-to-one to OpenAPI operations use `@openApi` and the exact `operationId`; additional convenience methods use `@extension`. Both decorators are marker-only and do not change runtime behavior.

For example, `users.create()` maps directly to the OpenAPI `create` operation, while the bulk helper is an SDK extension:

```ts
@openApi
async create(body: CreateUserRequest) {
  // Maps one-to-one to operationId: create.
}

@extension
async createMany(bodies: readonly CreateUserRequest[]) {
  // Composes the official create() operation.
}
```

Scenario-specific behavior that is not generally useful as an API convenience still belongs under `tests/support/`.

## Playwright owns transport and lifecycle

- `test` extends Playwright with one `taskmigo` fixture.
- `Taskmigo` receives Playwright's existing `Page` and `BrowserContext`.
- Browser automation uses Page Objects over native Playwright primitives.
- API automation reuses `BrowserContext.request` so it shares the browser cookie jar.
- Browser-authenticated API calls go through the production `/api/bff/**` route. The BFF validates the browser session and forwards a Bearer token to the protected Spring API.
- Mutating SDK requests send the application origin required by the BFF same-origin policy.
- Playwright continues to own cookies, tracing, screenshots, video, assertions, fixture lifecycle, retries, and reports.

The SDK does not implement its own HTTP engine, cookie store, locator abstraction, assertion library, or test lifecycle.

## Authentication

`taskmigo.signIn()` performs the real browser OAuth Authorization Code + PKCE flow. After login, SDK API calls reuse the authenticated BrowserContext and traverse the same BFF boundary used by browser code.

## Environment

- `E2E_BASE_URL`: browser-visible Taskmigo client origin.
- `E2E_AUTH_ORIGIN`: browser-visible authorization-server origin.
- `E2E_USERNAME`: interactive username.
- `E2E_PASSWORD`: interactive password.

GitHub Actions deploys the Kubernetes environment first, resolves the Minikube Gateway hostnames and migration credential, and then invokes this suite from `e2e/`.

## Run

Against an already deployed and reachable environment:

```bash
npm install --no-audit --no-fund --package-lock=false
npm run typecheck
npx playwright install chromium
E2E_BASE_URL=http://taskmigo.example.test \
E2E_AUTH_ORIGIN=http://api.taskmigo.example.test \
E2E_USERNAME=system \
E2E_PASSWORD='replace-me' \
npm test
```

Run a tagged subset with:

```bash
npm run test:auth
npm run test:bff
npm run test:performance
npm run test:smoke
```
