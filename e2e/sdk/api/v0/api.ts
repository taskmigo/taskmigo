import type { APIRequestContext } from "@playwright/test";

import { UsersApi } from "./users.js";

export class TaskmigoV0Api {
  readonly users: UsersApi;

  constructor(request: APIRequestContext, browserApiBaseUrl: string) {
    this.users = new UsersApi(request, browserApiBaseUrl);
  }
}
