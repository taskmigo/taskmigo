import { expect, test, type APIRequestContext } from "@playwright/test";

import {
  apiHeaders,
  expectFailure,
  expectSuccess,
  uniqueName,
} from "./client.js";

const createOrganization = async (
  request: APIRequestContext,
  key = uniqueName("org"),
): Promise<string> => {
  const response = await request.post("/api/v0/organizations", {
    headers: await apiHeaders(request),
    data: { key, name: `Organization ${key}` },
  });
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.organization.created",
  );
  expect(response.headers().location).toBe(
    `/api/v0/organizations/${body.data?.id}`,
  );
  return body.data!.id;
};

const createUser = async (
  request: APIRequestContext,
  organizationId: string | null,
  username = uniqueName("user"),
): Promise<string> => {
  const response = await request.post("/api/v0/users", {
    headers: await apiHeaders(request),
    data: {
      organizationId,
      username,
      emails: [`${username}@example.test`],
      firstName: "E2E",
      lastName: "User",
    },
  });
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.user.created",
  );
  return body.data!.id;
};

const createGroup = async (
  request: APIRequestContext,
  organizationId: string,
): Promise<string> => {
  const response = await request.post(
    `/api/v0/organizations/${organizationId}/groups`,
    {
      headers: await apiHeaders(request),
      data: { name: uniqueName("group"), description: "E2E group" },
    },
  );
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.group.created",
  );
  return body.data!.id;
};

test.describe("API resources @api @resources", () => {
  test("lists the complete permission catalog", async ({ request }) => {
    const response = await request.get("/api/v0/permissions", {
      headers: await apiHeaders(request),
    });
    const body = await expectSuccess<string[]>(
      response,
      200,
      "resource.permissions.retrieved",
    );
    expect(new Set(body.data)).toEqual(
      new Set([
        "project.read",
        "project.update",
        "project.members.read",
        "project.members.manage",
      ]),
    );
  });

  test("creates an organization and rejects duplicate keys", async ({
    request,
  }) => {
    const key = uniqueName("org");
    await createOrganization(request, key);

    const duplicate = await request.post("/api/v0/organizations", {
      headers: await apiHeaders(request),
      data: { key, name: "Duplicate organization" },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");
  });

  test("validates organization requests and malformed JSON", async ({
    request,
  }) => {
    const validation = await request.post("/api/v0/organizations", {
      headers: await apiHeaders(request),
      data: { key: " ", name: "" },
    });
    const validationBody = await expectFailure(
      validation,
      422,
      "validation.failed",
      "VALIDATION_ERROR",
    );
    expect(validationBody.error?.form_errors).toMatchObject({
      key: expect.any(String),
      name: expect.any(String),
    });

    const malformed = await request.post("/api/v0/organizations", {
      headers: {
        ...(await apiHeaders(request)),
        "Content-Type": "application/json",
      },
      data: '{"key":',
    });
    await expectFailure(
      malformed,
      400,
      "request.malformed",
      "MALFORMED_REQUEST",
    );
  });

  test("creates users and rejects invalid user input", async ({ request }) => {
    const organizationId = await createOrganization(request);
    const username = uniqueName("user");
    await createUser(request, organizationId, username);

    const invalidEmail = await request.post("/api/v0/users", {
      headers: await apiHeaders(request),
      data: {
        organizationId,
        username: uniqueName("user"),
        emails: ["not-an-email"],
        firstName: "E2E",
        lastName: "User",
      },
    });
    await expectFailure(
      invalidEmail,
      422,
      "validation.failed",
      "VALIDATION_ERROR",
    );

    const reserved = await request.post("/api/v0/users", {
      headers: await apiHeaders(request),
      data: {
        organizationId,
        username: "system",
        emails: [],
        firstName: "System",
        lastName: "Duplicate",
      },
    });
    await expectFailure(
      reserved,
      400,
      "domain.bad_request",
      "DOMAIN_BAD_REQUEST",
    );

    const duplicate = await request.post("/api/v0/users", {
      headers: await apiHeaders(request),
      data: {
        organizationId,
        username,
        emails: [`${uniqueName("other")}@example.test`],
        firstName: "Duplicate",
        lastName: "User",
      },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");
  });

  test("rejects a user whose organization does not exist", async ({
    request,
  }) => {
    const response = await request.post("/api/v0/users", {
      headers: await apiHeaders(request),
      data: {
        organizationId: crypto.randomUUID(),
        username: uniqueName("user"),
        emails: [],
        firstName: "Missing",
        lastName: "Organization",
      },
    });
    await expectFailure(response, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("creates roles and rejects unknown permissions or missing organizations", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const created = await request.post(
      `/api/v0/organizations/${organizationId}/roles`,
      {
        headers: await apiHeaders(request),
        data: {
          name: uniqueName("role"),
          description: "E2E role",
          permissions: ["project.read", "project.update"],
        },
      },
    );
    await expectSuccess<{ id: string }>(created, 201, "resource.role.created");

    const unknownPermission = await request.post(
      `/api/v0/organizations/${organizationId}/roles`,
      {
        headers: await apiHeaders(request),
        data: {
          name: uniqueName("role"),
          permissions: ["project.delete"],
        },
      },
    );
    await expectFailure(
      unknownPermission,
      400,
      "domain.bad_request",
      "DOMAIN_BAD_REQUEST",
    );

    const missingOrganization = await request.post(
      `/api/v0/organizations/${crypto.randomUUID()}/roles`,
      {
        headers: await apiHeaders(request),
        data: { name: uniqueName("role"), permissions: [] },
      },
    );
    await expectFailure(
      missingOrganization,
      404,
      "domain.not_found",
      "DOMAIN_NOT_FOUND",
    );
  });

  test("manages group membership and keeps removal idempotent", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const userId = await createUser(request, organizationId);
    const groupId = await createGroup(request, organizationId);

    const added = await request.put(`/api/v0/groups/${groupId}/members/${userId}`, {
      headers: await apiHeaders(request),
    });
    await expectSuccess<null>(added, 200, "resource.group.member_added");

    const removed = await request.delete(
      `/api/v0/groups/${groupId}/members/${userId}`,
      { headers: await apiHeaders(request) },
    );
    await expectSuccess<null>(removed, 200, "resource.group.member_removed");

    const removedAgain = await request.delete(
      `/api/v0/groups/${groupId}/members/${userId}`,
      { headers: await apiHeaders(request) },
    );
    await expectSuccess<null>(
      removedAgain,
      200,
      "resource.group.member_removed",
    );
  });

  test("rejects cross-organization and missing group members", async ({
    request,
  }) => {
    const groupOrganizationId = await createOrganization(request);
    const userOrganizationId = await createOrganization(request);
    const groupId = await createGroup(request, groupOrganizationId);
    const userId = await createUser(request, userOrganizationId);

    const crossOrganization = await request.put(
      `/api/v0/groups/${groupId}/members/${userId}`,
      { headers: await apiHeaders(request) },
    );
    await expectFailure(
      crossOrganization,
      400,
      "domain.bad_request",
      "DOMAIN_BAD_REQUEST",
    );

    const missingUser = await request.put(
      `/api/v0/groups/${groupId}/members/${crypto.randomUUID()}`,
      { headers: await apiHeaders(request) },
    );
    await expectFailure(
      missingUser,
      404,
      "domain.not_found",
      "DOMAIN_NOT_FOUND",
    );

    const missingGroup = await request.put(
      `/api/v0/groups/${crypto.randomUUID()}/members/${userId}`,
      { headers: await apiHeaders(request) },
    );
    await expectFailure(
      missingGroup,
      404,
      "domain.not_found",
      "DOMAIN_NOT_FOUND",
    );
  });
});
