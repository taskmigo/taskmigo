import { expect, type Page } from "@playwright/test";

import { e2eEnvironment } from "../support/environment.js";

export const signIn = async (page: Page): Promise<string> => {
  const environment = e2eEnvironment();

  await page.goto("/account");

  const usernameInput = page.locator('input[name="username"]');
  const passwordInput = page.locator('input[name="password"]');
  await expect(usernameInput).toBeVisible();
  await expect(passwordInput).toBeVisible();
  expect(new URL(page.url()).origin).toBe(environment.authorizationOrigin);

  await usernameInput.fill(environment.username);
  await passwordInput.fill(environment.password);
  await Promise.all([
    page.waitForURL(new URL("/account", environment.baseUrl).href),
    page.locator('button[type="submit"]').click(),
  ]);

  await expect(page.getByRole("heading", { name: "Account" })).toBeVisible();
  await expect(page.getByText(`Signed in as ${environment.username}`)).toBeVisible();

  return environment.username;
};
