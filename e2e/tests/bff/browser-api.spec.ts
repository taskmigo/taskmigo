import { expect, test } from "#taskmigo-sdk";

const usersUrl = (browserApiBaseUrl: string): string => new URL("v0/users?page=1&pageSize=1", browserApiBaseUrl).href;

test.describe("Browser API BFF", { tag: "@bff" }, () => {
  test("requires an authenticated browser session", async ({ taskmigo }) => {
    const response = await taskmigo.api.request.get(usersUrl(taskmigo.api.browserApiBaseUrl), {
      headers: { Accept: "application/json" },
    });

    expect(response.status()).toBe(401);
    await expect(response.json()).resolves.toEqual({ error: "Authentication required" });
  });

  test("forwards an authenticated browser request to the protected API", async ({ taskmigo }) => {
    await taskmigo.signIn();

    const response = await taskmigo.api.v0.users.list({ page: 1, pageSize: 1 });

    expect(response.statusCode).toBe(200);
  });

  test("rejects cross-origin mutations with the authenticated browser cookie jar", async ({ taskmigo }) => {
    await taskmigo.signIn();

    const authenticated = await taskmigo.api.request.get(usersUrl(taskmigo.api.browserApiBaseUrl), {
      headers: { Accept: "application/json" },
    });
    expect(authenticated.status()).toBe(200);

    const response = await taskmigo.api.request.post(new URL("v0/users", taskmigo.api.browserApiBaseUrl).href, {
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
