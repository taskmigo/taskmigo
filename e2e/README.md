# Taskmigo E2E

This folder owns the black-box Playwright suite for a deployed Taskmigo environment. The suite does not create the environment and does not depend on the repository Taskfile; it only requires reachable public endpoints and test credentials through environment variables.

Tests are organized by product feature under `tests/<feature>/`. Every feature group has a Playwright tag so it can be selected independently. The authentication suite currently uses `@auth`, with the narrower `@login`, `@session`, and `@smoke` tags.

The current suite verifies the browser authentication path end to end:

1. Open the protected `/account` page without a session.
2. Follow the redirect to the deployed Spring Authorization Server.
3. Sign in with the bootstrap `system` user.
4. Complete the OAuth Authorization Code + PKCE callback through the Next.js BFF.
5. Verify the authenticated account page, BFF session API, HttpOnly session cookie, and session persistence after reload.

## Environment

The suite requires:

- `E2E_BASE_URL`: browser-visible Taskmigo client origin.
- `E2E_AUTH_ORIGIN`: browser-visible authorization-server origin.
- `E2E_USERNAME`: interactive username.
- `E2E_PASSWORD`: interactive password.

GitHub Actions deploys the Kubernetes environment first, resolves the Minikube Gateway hostnames and bootstrap credential, and then invokes this suite directly from `e2e/`. That workflow integration is glue only; Playwright setup and execution are not Taskfile responsibilities.

On CI, Playwright writes line, HTML, and JSON reports. The HTML report, traces, screenshots, and videos are uploaded for investigation. The JSON report is reduced to stable test identities, tags, and final statuses, compared with the latest successful target-branch report, written to the workflow summary, and published as one continuously updated pull request comment. Timing and timestamps are deliberately excluded from the baseline diff.

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
npm test
```

Run a tagged subset with the provided scripts:

```bash
npm run test:auth
npm run test:smoke
```
