import { expect, test, type Locator, type Page } from "@playwright/test";

export class AuthorizationPage {
  readonly username: Locator;
  readonly password: Locator;
  readonly submit: Locator;

  constructor(
    readonly page: Page,
    private readonly origin: string,
  ) {
    this.username = page.locator('input[name="username"]');
    this.password = page.locator('input[name="password"]');
    this.submit = page.locator('button[type="submit"]');
  }

  async signIn(username: string, password: string, returnUrl: string): Promise<void> {
    await test.step(`Authorize as ${username}`, async () => {
      await expect(this.username).toBeVisible();
      await expect(this.password).toBeVisible();
      expect(new URL(this.page.url()).origin).toBe(this.origin);

      await this.username.fill(username);
      await this.password.fill(password);
      await Promise.all([this.page.waitForURL(returnUrl), this.submit.click()]);
    });
  }
}
