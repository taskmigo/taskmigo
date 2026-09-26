import { test } from "@playwright/test";

import type { CreateUserRequest, CreateUserResponse, UsersApi } from "../users.js";

export class UsersApiExtensions {
  constructor(private readonly users: Pick<UsersApi, "create">>) {}

  async createMany(bodies: readonly CreateUserRequest[]): Promise<CreateUserResponse[]> {
    return test.step(`Create ${bodies.length} users`, async () => {
      const responses: CreateUserResponse[] = [];
      for (const body of bodies) {
        responses.push(await this.users.create(body));
      }
      return responses;
    });
  }
}
