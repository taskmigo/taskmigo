import { randomUUID } from "node:crypto";

import { expect, test, type CreateUserRequest, type Taskmigo } from "#taskmigo-sdk";

export const createUser = async (taskmigo: Taskmigo, body: CreateUserRequest): Promise<string> =>
  test.step("Create user", async () => {
    const response = await taskmigo.api.v0.users.create(body);
    const id = response.data?.id;
    expect(id).toBeTruthy();
    return id ?? "";
  });

export const createUsers = async (taskmigo: Taskmigo, count: number): Promise<string[]> =>
  test.step(`Create ${count} users`, async () => {
    const ids: string[] = [];
    for (let index = 0; index < count; index += 1) {
      const suffix = `${index}-${randomUUID()}`;
      ids.push(
        await createUser(taskmigo, {
          username: `e2e-user-${suffix}`,
          emails: [`e2e-user-${suffix}@example.com`],
          firstName: "E2E",
          lastName: "User",
        }),
      );
    }
    return ids;
  });
