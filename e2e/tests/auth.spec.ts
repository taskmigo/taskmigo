import { expect, test } from "@playwright/test";

const requiredEnvironment = (name: string): string => {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
};

test("system user can sign in through the deployed OAuth flow", async ({ page, context }) => {
  const baseUrl = new URL(requiredEnvironment("E2E_BASE_URL"));
  const authorizationOrigin = new URL(requiredEnvironment("E2E_AUTH_ORIGIN")).origin;
  const username = requiredEnvironment("E2E_USERNAME");
  const password = requiredEnvironment("E2E_PASSWORD");

  await page.goto("/account");

  const usernameInput = page.locator('input[name="username"]');
  const passwordInput = page.locator('input[name="password"]');
  await expect(usernameInput).toBeVisible();
  await expect(passwordInput).toBeVisible();
  expect(new URL(page.url()).origin).toBe(authorizationOrigin);

  await usernameInput.fill(username);
  await passwordInput.fill(password);
  await Promise.all([
    page.waitForURL(new URL("/account", baseUrl).href),
    page.locator('button[type="submit"]').click(),
  ]);

  await expect(page.getByRole("heading", { name: "Account" })).toBeVisible();
  await expect(page.getByText(`Signed in as ${username}`)).toBeVisible();

  const session = await page.evaluate(async () => {
    const response = await fetch("/api/auth/session", { headers: { Accept: "application/json" } });
    return (await response.json()) as { authenticated: boolean; user?: { id: string } };
  });
  expect(session.authenticated).toBe(true);
  expect(session.user?.id).toBe(username);

  const sessionCookie = (await context.cookies()).find(({ name }) => name === "taskmigo_session");
  expect(sessionCookie).toBeDefined();
  expect(sessionCookie?.httpOnly).toBe(true);

  await page.reload();
  await expect(page.getByRole("heading", { name: "Account" })).toBeVisible();
  await expect(page.getByText(`Signed in as ${username}`)).toBeVisible();
});
