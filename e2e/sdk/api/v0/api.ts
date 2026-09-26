import type { APIRequestContext } from "@playwright/test";

import { UsersApiExtensions } from "./extensions/users.js";
import { UsersApi } from "./users.js";

export class TaskmigoV0Api {
  readonly users: UsersApi;
  readonly extensions: {
    readonly users: UsersApiExtensions;
  };

  constructor(request: APIRequestContext, browserApiBaseUrl: string) {
    this.users = new UsersApi(request, browserApiBaseUrl);
    this.extensions = {
      users: new UsersApiExtensions(this.users),
    };
  }
}
