import type { APIRequestContext } from "@playwright/test";

import { TaskmigoV0Api } from "./v0/api.js";

export class TaskmigoApi {
  readonly browserApiBaseUrl: string;
  readonly v0: TaskmigoV0Api;

  constructor(
    readonly request: APIRequestContext,
    appUrl: URL,
  ) {
    this.browserApiBaseUrl = new URL("/api/bff/", appUrl).href;
    this.v0 = new TaskmigoV0Api(request, this.browserApiBaseUrl);
  }
}
