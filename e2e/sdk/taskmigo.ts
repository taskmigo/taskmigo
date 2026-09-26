import type { BrowserContext, Page } from "@playwright/test";

import { TaskmigoApi } from "./api/taskmigo-api.js";
import type { TaskmigoConfig } from "./config.js";
import { TaskmigoWeb } from "./web/taskmigo-web.js";

export class Taskmigo {
  readonly api: TaskmigoApi;
  readonly web: TaskmigoWeb;

  constructor(
    readonly page: Page,
    readonly context: BrowserContext,
    config: TaskmigoConfig,
  ) {
    this.api = new TaskmigoApi(context.request, config.appUrl);
    this.web = new TaskmigoWeb(page, context, config);
  }

  async signIn(): Promise<string> {
    return this.web.signIn();
  }
}
