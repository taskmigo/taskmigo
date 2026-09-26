import type { APIRequestContext } from "@playwright/test";

import { UsersApi, UsersApiExtensions } from "./users.js";

class UsersResource extends UsersApi {
  readonly extensions: UsersApiExtensions;

  constructor(request: APIRequestContext, browserApiBaseUrl: string) {
    super(request, browserApiBaseUrl);
    this.extensions = new UsersApiExtensions(this);
  }
}

export class TaskmigoV0Api {
  readonly users: UsersResource;

  constructor(request: APIRequestContext, browserApiBaseUrl: string) {
    this.users = new UsersResource(request, browserApiBaseUrl);
  }
}
