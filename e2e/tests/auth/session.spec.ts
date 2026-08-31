import { expect, test } from "@playwright/test";

import { signIn } from "./sign-in.js";

test.describe("Browser session", { annotation: { type: "ui" } }, () => {
  test("authenticated session survives a page reload", async ({ page }) => {
    const username = await signIn(page);

    await page.reload();

    await expect(page.getByRole("heading", { name: "Account" })).toBeVisible();
    await expect(page.getByText(`Signed in as ${username}`)).toBeVisible();
  });
});
