import type { APIRequestContext } from "@playwright/test";

import { UsersApi, UsersApiExtensions } from "./users.js";

export class TaskmigoV0Api {
  readonly users: UsersApi & { readonly extensions: UsersApiExtensions };

  constructor(request: APIRequestContext, browserApiBaseUrl: string) {
    const users = new UsersApi(request, browserApiBaseUrl);
    this.users = Object.assign(users, {
      extensions: new UsersApiExtensions(users),
    });
  }
}
