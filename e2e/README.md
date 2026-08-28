# Taskmigo E2E

This folder contains black-box Playwright tests for a deployed Taskmigo environment. The tests do not start application processes themselves; the Kubernetes integration workflow deploys Taskmigo first and then exposes the in-cluster web and client Services to Playwright.

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

GitHub Actions maps the Kubernetes Service hostnames `taskmigo-client` and `taskmigo-web` to local port-forwards so browser redirects use the same hostnames configured inside the Helm release.

## Run

Against an already deployed and reachable environment:

```bash
npm install
npx playwright install chromium
E2E_BASE_URL=http://taskmigo-client:3000 \
E2E_AUTH_ORIGIN=http://taskmigo-web:8080 \
E2E_USERNAME=system \
E2E_PASSWORD='replace-me' \
npm test
```
