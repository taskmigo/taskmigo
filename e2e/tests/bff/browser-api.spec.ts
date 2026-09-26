import { expect, test } from "@playwright/test";

import { signIn } from "../auth/sign-in.js";
import { e2eEnvironment } from "../support/environment.js";

const browserApiUrl = (path: string): string => new URL(path, e2eEnvironment().baseUrl).href;

test.describe("Browser API BFF", { tag: "@bff" }, () => {
  test("requires an authenticated browser session", async ({ context }) => {
    const response = await context.request.get(browserApiUrl("/api/bff/v0/users?page=1&pageSize=1"), {
      headers: { Accept: "application/json" },
    });

    expect(response.status()).toBe(401);
    await expect(response.json()).resolves.toEqual({ error: "Authentication required" });
  });

  test("forwards an authenticated browser request to the protected API", async ({ page }) => {
    await signIn(page);

    const response = await page.evaluate(async () => {
      const result = await fetch("/api/bff/v0/users?page=1&pageSize=1", {
        headers: { Accept: "application/json" },
      });
      return { status: result.status, body: await result.text() };
    });

    expect(response.status, response.body).toBe(200);
  });

  test("rejects cross-origin mutations with the authenticated browser cookie jar", async ({ page, context }) => {
    await signIn(page);

    const authenticated = await context.request.get(browserApiUrl("/api/bff/v0/users?page=1&pageSize=1"), {
      headers: { Accept: "application/json" },
    });
    expect(authenticated.status()).toBe(200);

    const response = await context.request.post(browserApiUrl("/api/bff/v0/users"), {
      headers: {
        Origin: "https://attacker.example",
        "Content-Type": "application/json",
      },
      data: {},
    });

    expect(response.status()).toBe(403);
    await expect(response.json()).resolves.toEqual({ error: "Cross-origin browser API request rejected" });
  });
});
