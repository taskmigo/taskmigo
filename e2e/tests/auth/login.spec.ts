import { expect, test } from "@playwright/test";

import { signIn } from "./sign-in.js";

test.describe("OAuth login", { tag: ["@auth", "@login"] }, () => {
  test("system user completes the deployed Authorization Code flow", { tag: "@smoke" }, async ({ page, context }) => {
    const username = await signIn(page);

    const session = await page.evaluate(async () => {
      const response = await fetch("/api/auth/session", {
        headers: { Accept: "application/json" },
      });
      return (await response.json()) as {
        authenticated: boolean;
        user?: { id: string };
      };
    });
    expect(session.authenticated).toBe(true);
    expect(session.user?.id).toBe(username);

    const sessionCookie = (await context.cookies()).find(({ name }) => name === "taskmigo_session");
    expect(sessionCookie).toBeDefined();
    expect(sessionCookie?.httpOnly).toBe(true);

    const apiResponse = await page.evaluate(async () => {
      const response = await fetch("/api/bff/v0/users?page=1&pageSize=1", {
        headers: { Accept: "application/json" },
      });
      return { status: response.status, body: await response.text() };
    });
    expect(apiResponse.status, apiResponse.body).toBe(200);
  });
});
