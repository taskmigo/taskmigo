# Taskmigo E2E

This folder owns the black-box Playwright suite for a deployed Taskmigo environment. The suite does not create the environment and does not depend on the repository Taskfile; it only requires reachable public endpoints and test credentials through environment variables.

Tests are organized by product feature under `tests/<feature>/`. Every feature group has a Playwright tag so it can be selected independently. Browser authentication uses `@auth`, with the narrower `@login`, `@session`, and `@smoke` tags. API coverage uses `@api`, with `@security`, `@resources`, and `@projects` groups.

The browser suite verifies the interactive OAuth Authorization Code + PKCE flow. The API suite obtains a real OAuth `client_credentials` token and exercises the deployed `/api/v0` HTTP surface, including happy paths, validation failures, malformed requests, authorization failures, missing resources, conflicts, organization-boundary checks, archived-project behavior, effective permissions, project history, and cursor pagination.

## Environment

The browser suite requires:

- `E2E_BASE_URL`: browser-visible Taskmigo client origin.
- `E2E_AUTH_ORIGIN`: browser-visible authorization-server origin.
- `E2E_USERNAME`: interactive username.
- `E2E_PASSWORD`: interactive password.

The API suite additionally requires:

- `E2E_API_CLIENT_ID`: OAuth client allowed to call `taskmigo.api`.
- `E2E_API_CLIENT_SECRET`: OAuth client secret for that client.

GitHub Actions deploys the Kubernetes environment first, resolves the Minikube Gateway hostnames and bootstrap credentials, reads the integration OAuth client secret from the deployment secret, and then invokes this suite directly from `e2e/`. That workflow integration is glue only; Playwright setup and execution are not Taskfile responsibilities.

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
npm run test:api
npm run test:auth
npm run test:smoke
```
