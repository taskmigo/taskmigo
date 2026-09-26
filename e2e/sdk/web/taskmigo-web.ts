import { test, type BrowserContext, type Page } from "@playwright/test";

import type { TaskmigoConfig } from "../config.js";
import { AccountPage } from "./account-page.js";
import { AuthorizationPage } from "./authorization-page.js";

export class TaskmigoWeb {
  readonly account: AccountPage;
  readonly authorization: AuthorizationPage;

  constructor(
    readonly page: Page,
    readonly context: BrowserContext,
    private readonly config: TaskmigoConfig,
  ) {
    this.account = new AccountPage(page, context);
    this.authorization = new AuthorizationPage(page, config.authorizationOrigin);
  }

  async signIn(): Promise<string> {
    return test.step(`Sign in as ${this.config.credentials.username}`, async () => {
      await this.account.open();
      await this.authorization.signIn(
        this.config.credentials.username,
        this.config.credentials.password,
        new URL("/account", this.config.appUrl).href,
      );
      await this.account.expectSignedIn(this.config.credentials.username);
      return this.config.credentials.username;
    });
  }
}
