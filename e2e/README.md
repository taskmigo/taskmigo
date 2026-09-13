# Taskmigo E2E

This folder owns the black-box Playwright suite for a deployed Taskmigo environment. The suite does not create the environment and does not depend on the repository Taskfile; it only requires reachable public endpoints and test credentials through environment variables.

Tests are organized by product feature under `tests/<feature>/`. Every feature group has a Playwright tag so it can be selected independently. The authentication suite uses `@auth`, with the narrower `@login`, `@session`, and `@smoke` tags. Authorization feature cases use `@authorization` and are data-driven from YAML files under `features/authorization/`.

## Authorization feature cases

Authorization coverage is intentionally black-box. The runner authenticates with the E2E OAuth client, uses only public HTTP APIs to create fixture data and Statements, sends the request described by the case, and asserts the public response. Object Authorization is verified through which resources are returned by the API rather than by inspecting JPA predicates or generated SQL.

QC can add a case without editing TypeScript:

1. Copy `features/authorization/_template.yaml` to a new `*.yaml` file in the same directory.
2. Fill in optional fixture users and authorization Statements under `setup`.
3. Fill in the HTTP `request`.
4. Fill in `expected.status` and optional response-body assertions.
5. Run `npm run test:authorization` against a deployed environment.

The runner supports placeholders such as `{{runId}}`, `{{principal.id}}`, `{{principal.username}}`, `{{users.<alias>.id}}`, `{{users.<alias>.username}}`, and `{{statements.<alias>.id}}`. Files beginning with `_` are templates/documentation and are not executed.

Each case starts by clearing direct Statements on the dedicated E2E principal, creates its fixtures, assigns the case Statements, executes the request, and clears direct Statements again in `finally`. Request-scope DENY cases must not target the cleanup endpoint `PATCH /api/v0/users/{principal.id}/statements`; the runner rejects such a case before assignment. The Kubernetes integration environment is disposable and dedicated to this suite, so fixture resources created by a case do not need a public delete endpoint.

## Browser authentication coverage

The browser authentication suite verifies the interactive authentication path end to end:

1. Open the protected `/account` page without a session.
2. Follow the redirect to the deployed Spring Authorization Server.
3. Sign in with the bootstrap `system` user.
4. Complete the OAuth Authorization Code + PKCE callback through the Next.js BFF.
5. Verify the authenticated account page, BFF session API, HttpOnly session cookie, and session persistence after reload.

## Environment

The suite requires:

- `E2E_BASE_URL`: browser-visible Taskmigo client/API origin.
- `E2E_AUTH_ORIGIN`: browser-visible authorization-server origin.
- `E2E_USERNAME`: interactive username and E2E principal username.
- `E2E_PASSWORD`: interactive password.
- `E2E_API_CLIENT_ID`: client-credentials OAuth client id used by API feature tests.
- `E2E_API_CLIENT_SECRET`: client-credentials OAuth client secret used by API feature tests.

GitHub Actions deploys the Kubernetes environment first, resolves the Minikube Gateway hostnames and bootstrap credentials, and then invokes this suite directly from `e2e/`. That workflow integration is glue only; Playwright setup and execution are not Taskfile responsibilities.

On CI, Playwright's built-in GitHub reporter adds failure annotations and its built-in HTML reporter captures the detailed results, traces, screenshots, and videos. The workflow uploads that report and updates one pull-request comment with the overall result and workflow link. Re-running the workflow updates the same bot comment instead of creating another one.

## Run

Against any already deployed and reachable environment:

```bash
npm install --no-audit --no-fund --package-lock=false
npm run typecheck
npx playwright install chromium
E2E_BASE_URL=http://taskmigo.example.test \
E2E_AUTH_ORIGIN=http://api.taskmigo.example.test \
E2E_USERNAME=system \
E2E_PASSWORD='replace-me' \
E2E_API_CLIENT_ID=taskmigo-helm-test \
E2E_API_CLIENT_SECRET='replace-me' \
npm test
```

Run a tagged subset with the provided scripts:

```bash
npm run test:auth
npm run test:authorization
npm run test:smoke
```
