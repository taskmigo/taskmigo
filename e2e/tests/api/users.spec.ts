import { randomUUID } from "node:crypto";

import { expect, test } from "#taskmigo-sdk";

const uniqueUser = () => {
  const suffix = randomUUID();
  return {
    username: `e2e-api-${suffix}`,
    emails: [`e2e-api-${suffix}@example.com`],
    firstName: "API",
    lastName: "E2E",
  };
};

test.describe("Users API", { tag: ["@api", "@users"] }, () => {
  test.beforeEach(async ({ taskmigo }) => {
    await taskmigo.signIn();
  });

  test("creates a user that is discoverable through the list API", async ({ taskmigo }) => {
    const request = uniqueUser();

    const created = await taskmigo.api.v0.users.create(request);
    const listed = await taskmigo.api.v0.users.list({ page: 1, pageSize: 100 });

    const user = listed.data.find((candidate) => candidate.id === created.data.id);
    expect(user).toMatchObject({
      id: created.data.id,
      username: request.username,
      firstName: request.firstName,
      lastName: request.lastName,
      emails: request.emails,
      displayName: `${request.firstName} ${request.lastName}`,
      status: "ACTIVE",
      retainedAt: null,
    });
  });

  test("returns requested pagination metadata", async ({ taskmigo }) => {
    const response = await taskmigo.api.v0.users.list({ page: 1, pageSize: 1 });

    expect(response.meta.pagination).toMatchObject({
      type: "offset",
      offset: {
        currentPage: 1,
        pageSize: 1,
      },
    });
    expect(response.data).toHaveLength(1);
  });

  test("rejects invalid user creation input", async ({ taskmigo }) => {
    const response = await taskmigo.api.v0.users.createExpectingError({
      username: "",
      emails: ["not-an-email"],
      firstName: "",
      lastName: "",
    });

    expect(response.statusCode).toBe(400);
    expect(response.success).toBe(false);
    expect(response.error).not.toBeNull();
  });
});
