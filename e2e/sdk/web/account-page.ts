import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

export interface BrowserSession {
  authenticated: boolean;
  user?: {
    id: string;
  };
}

export class AccountPage {
  readonly heading: Locator;

  constructor(
    readonly page: Page,
    private readonly context: BrowserContext,
  ) {
    this.heading = page.getByRole("heading", { name: "Account" });
  }

  async open(): Promise<void> {
    await test.step("Open account", async () => {
      await this.page.goto("/account");
    });
  }

  async reload(): Promise<void> {
    await test.step("Reload account", async () => {
      await this.page.reload();
    });
  }

  async expectSignedIn(username: string): Promise<void> {
    await test.step(`Verify signed in as ${username}`, async () => {
      await expect(this.heading).toBeVisible();
      await expect(this.page.getByText(`Signed in as ${username}`)).toBeVisible();
    });
  }

  async session(): Promise<BrowserSession> {
    return test.step("Read browser session", async () =>
      this.page.evaluate(async () => {
        const response = await fetch("/api/auth/session", {
          headers: { Accept: "application/json" },
        });
        return (await response.json()) as BrowserSession;
      }));
  }

  async sessionCookie() {
    return test.step("Read session cookie", async () => {
      const cookies = await this.context.cookies();
      return cookies.find(({ name }) => name === "taskmigo_session");
    });
  }
}
