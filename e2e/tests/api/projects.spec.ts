import { expect, test, type APIRequestContext } from "@playwright/test";

import {
  apiHeaders,
  expectFailure,
  expectSuccess,
  uniqueName,
} from "./client.js";

const createOrganization = async (
  request: APIRequestContext,
): Promise<string> => {
  const key = uniqueName("org");
  const response = await request.post("/api/v0/organizations", {
    headers: await apiHeaders(request),
    data: { key, name: `Organization ${key}` },
  });
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.organization.created",
  );
  return body.data!.id;
};

const createUser = async (
  request: APIRequestContext,
  organizationId: string,
): Promise<string> => {
  const username = uniqueName("user");
  const response = await request.post("/api/v0/users", {
    headers: await apiHeaders(request),
    data: {
      organizationId,
      username,
      emails: [`${username}@example.test`],
      firstName: "Project",
      lastName: "Member",
    },
  });
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.user.created",
  );
  return body.data!.id;
};

const createRole = async (
  request: APIRequestContext,
  organizationId: string,
  permissions: string[],
): Promise<string> => {
  const response = await request.post(
    `/api/v0/organizations/${organizationId}/roles`,
    {
      headers: await apiHeaders(request),
      data: { name: uniqueName("role"), permissions },
    },
  );
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.role.created",
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
      data: { name: uniqueName("group") },
    },
  );
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.group.created",
  );
  return body.data!.id;
};

const createProject = async (
  request: APIRequestContext,
  organizationId: string,
  key = uniqueName("project"),
): Promise<string> => {
  const response = await request.post(
    `/api/v0/organizations/${organizationId}/projects`,
    {
      headers: await apiHeaders(request),
      data: { key, name: `Project ${key}`, description: "E2E project" },
    },
  );
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.project.created",
  );
  return body.data!.id;
};

const addProjectMember = async (
  request: APIRequestContext,
  projectId: string,
  principalType: "USER" | "GROUP",
  principalId: string,
): Promise<string> => {
  const response = await request.post(`/api/v0/projects/${projectId}/members`, {
    headers: await apiHeaders(request),
    data: { principalType, principalId },
  });
  const body = await expectSuccess<{ id: string }>(
    response,
    201,
    "resource.project.member_added",
  );
  return body.data!.id;
};

test.describe("Project API @api @projects", () => {
  test("creates projects and rejects duplicate keys or missing organizations", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const key = uniqueName("project");
    await createProject(request, organizationId, key);

    const duplicate = await request.post(
      `/api/v0/organizations/${organizationId}/projects`,
      {
        headers: await apiHeaders(request),
        data: { key, name: "Duplicate project" },
      },
    );
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");

    const missingOrganization = await request.post(
      `/api/v0/organizations/${crypto.randomUUID()}/projects`,
      {
        headers: await apiHeaders(request),
        data: { key: uniqueName("project"), name: "Missing organization" },
      },
    );
    await expectFailure(
      missingOrganization,
      404,
      "domain.not_found",
      "DOMAIN_NOT_FOUND",
    );
  });

  test("validates project payloads and missing projects", async ({ request }) => {
    const organizationId = await createOrganization(request);
    const invalid = await request.post(
      `/api/v0/organizations/${organizationId}/projects`,
      {
        headers: await apiHeaders(request),
        data: { key: " ", name: "" },
      },
    );
    await expectFailure(invalid, 422, "validation.failed", "VALIDATION_ERROR");

    const missing = await request.patch(
      `/api/v0/projects/${crypto.randomUUID()}/archive`,
      { headers: await apiHeaders(request) },
    );
    await expectFailure(missing, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("manages project members and rejects invalid principals", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const userId = await createUser(request, organizationId);
    const projectId = await createProject(request, organizationId);
    const memberId = await addProjectMember(request, projectId, "USER", userId);

    const duplicate = await request.post(`/api/v0/projects/${projectId}/members`, {
      headers: await apiHeaders(request),
      data: { principalType: "USER", principalId: userId },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");

    const invalidType = await request.post(
      `/api/v0/projects/${projectId}/members`,
      {
        headers: await apiHeaders(request),
        data: { principalType: "ROBOT", principalId: userId },
      },
    );
    await expectFailure(
      invalidType,
      400,
      "domain.bad_request",
      "DOMAIN_BAD_REQUEST",
    );

    const missingUser = await request.post(
      `/api/v0/projects/${projectId}/members`,
      {
        headers: await apiHeaders(request),
        data: { principalType: "USER", principalId: crypto.randomUUID() },
      },
    );
    await expectFailure(
      missingUser,
      404,
      "domain.not_found",
      "DOMAIN_NOT_FOUND",
    );

    const removed = await request.delete(
      `/api/v0/projects/${projectId}/members/${memberId}`,
      { headers: await apiHeaders(request) },
    );
    await expectSuccess<null>(removed, 200, "resource.project.member_removed");

    const removedAgain = await request.delete(
      `/api/v0/projects/${projectId}/members/${memberId}`,
      { headers: await apiHeaders(request) },
    );
    await expectFailure(
      removedAgain,
      404,
      "domain.not_found",
      "DOMAIN_NOT_FOUND",
    );
  });

  test("rejects a project member id that belongs to another project", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const userId = await createUser(request, organizationId);
    const firstProjectId = await createProject(request, organizationId);
    const secondProjectId = await createProject(request, organizationId);
    const memberId = await addProjectMember(
      request,
      firstProjectId,
      "USER",
      userId,
    );

    const response = await request.delete(
      `/api/v0/projects/${secondProjectId}/members/${memberId}`,
      { headers: await apiHeaders(request) },
    );
    await expectFailure(response, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("assigns roles and rejects invalid role assignments", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const otherOrganizationId = await createOrganization(request);
    const userId = await createUser(request, organizationId);
    const projectId = await createProject(request, organizationId);
    const memberId = await addProjectMember(request, projectId, "USER", userId);
    const roleId = await createRole(request, organizationId, ["project.read"]);
    const foreignRoleId = await createRole(request, otherOrganizationId, [
      "project.update",
    ]);

    const assigned = await request.put(
      `/api/v0/projects/${projectId}/members/${memberId}/roles`,
      {
        headers: await apiHeaders(request),
        data: { roleIds: [roleId] },
      },
    );
    await expectSuccess<null>(
      assigned,
      200,
      "resource.project.member_roles_updated",
    );

    const missingRole = await request.put(
      `/api/v0/projects/${projectId}/members/${memberId}/roles`,
      {
        headers: await apiHeaders(request),
        data: { roleIds: [crypto.randomUUID()] },
      },
    );
    await expectFailure(
      missingRole,
      400,
      "domain.bad_request",
      "DOMAIN_BAD_REQUEST",
    );

    const foreignRole = await request.put(
      `/api/v0/projects/${projectId}/members/${memberId}/roles`,
      {
        headers: await apiHeaders(request),
        data: { roleIds: [foreignRoleId] },
      },
    );
    await expectFailure(
      foreignRole,
      400,
      "domain.bad_request",
      "DOMAIN_BAD_REQUEST",
    );
  });

  test("calculates direct and group-derived effective permissions", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const userId = await createUser(request, organizationId);
    const projectId = await createProject(request, organizationId);
    const userRoleId = await createRole(request, organizationId, [
      "project.read",
    ]);
    const groupRoleId = await createRole(request, organizationId, [
      "project.update",
    ]);

    const userMemberId = await addProjectMember(request, projectId, "USER", userId);
    const userRoleAssignment = await request.put(
      `/api/v0/projects/${projectId}/members/${userMemberId}/roles`,
      {
        headers: await apiHeaders(request),
        data: { roleIds: [userRoleId] },
      },
    );
    await expectSuccess<null>(
      userRoleAssignment,
      200,
      "resource.project.member_roles_updated",
    );

    const groupId = await createGroup(request, organizationId);
    const groupMembership = await request.put(
      `/api/v0/groups/${groupId}/members/${userId}`,
      { headers: await apiHeaders(request) },
    );
    await expectSuccess<null>(
      groupMembership,
      200,
      "resource.group.member_added",
    );

    const groupProjectMemberId = await addProjectMember(
      request,
      projectId,
      "GROUP",
      groupId,
    );
    const groupRoleAssignment = await request.put(
      `/api/v0/projects/${projectId}/members/${groupProjectMemberId}/roles`,
      {
        headers: await apiHeaders(request),
        data: { roleIds: [groupRoleId] },
      },
    );
    await expectSuccess<null>(
      groupRoleAssignment,
      200,
      "resource.project.member_roles_updated",
    );

    const response = await request.get(
      `/api/v0/projects/${projectId}/users/${userId}/effective-permissions`,
      { headers: await apiHeaders(request) },
    );
    const body = await expectSuccess<string[]>(
      response,
      200,
      "resource.project.effective_permissions_retrieved",
    );
    expect(new Set(body.data)).toEqual(
      new Set(["project.read", "project.update"]),
    );
  });

  test("returns not found for effective-permission lookups with missing resources", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const userId = await createUser(request, organizationId);
    const projectId = await createProject(request, organizationId);

    const missingUser = await request.get(
      `/api/v0/projects/${projectId}/users/${crypto.randomUUID()}/effective-permissions`,
      { headers: await apiHeaders(request) },
    );
    await expectFailure(
      missingUser,
      404,
      "domain.not_found",
      "DOMAIN_NOT_FOUND",
    );

    const missingProject = await request.get(
      `/api/v0/projects/${crypto.randomUUID()}/users/${userId}/effective-permissions`,
      { headers: await apiHeaders(request) },
    );
    await expectFailure(
      missingProject,
      404,
      "domain.not_found",
      "DOMAIN_NOT_FOUND",
    );
  });

  test("records project history and paginates without overlap", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const userId = await createUser(request, organizationId);
    const roleId = await createRole(request, organizationId, ["project.read"]);
    const projectId = await createProject(request, organizationId);
    const memberId = await addProjectMember(request, projectId, "USER", userId);

    const roleUpdate = await request.put(
      `/api/v0/projects/${projectId}/members/${memberId}/roles`,
      {
        headers: await apiHeaders(request),
        data: { roleIds: [roleId] },
      },
    );
    await expectSuccess<null>(
      roleUpdate,
      200,
      "resource.project.member_roles_updated",
    );

    const archived = await request.patch(
      `/api/v0/projects/${projectId}/archive`,
      { headers: await apiHeaders(request) },
    );
    await expectSuccess<null>(archived, 200, "resource.project.archived");

    const firstPageResponse = await request.get(
      `/api/v0/projects/${projectId}/history?limit=2`,
      { headers: await apiHeaders(request) },
    );
    const firstPage = await expectSuccess<
      Array<{
        id: string;
        action: string;
        actor: { type: string; id: string; displayName: string };
      }>
    >(firstPageResponse, 200, "resource.project.history_retrieved");
    expect(firstPage.meta.pagination).toMatchObject({
      type: "cursor",
      cursor: {
        next_cursor: expect.any(String),
        prev_cursor: null,
        has_more: true,
      },
    });
    expect(firstPage.data?.map((entry) => entry.action)).toEqual([
      "PROJECT_ARCHIVED",
      "MEMBER_ROLES_CHANGED",
    ]);
    for (const entry of firstPage.data ?? []) {
      expect(entry.actor.id).toBe("taskmigo-helm-test");
      expect(entry.actor.type).toBe("SERVICE");
    }

    const nextCursor = firstPage.meta.pagination?.cursor?.next_cursor;
    expect(nextCursor).toEqual(expect.any(String));
    const secondPageResponse = await request.get(
      `/api/v0/projects/${projectId}/history?limit=2&cursor=${encodeURIComponent(nextCursor!)}`,
      { headers: await apiHeaders(request) },
    );
    const secondPage = await expectSuccess<Array<{ id: string; action: string }>>(
      secondPageResponse,
      200,
      "resource.project.history_retrieved",
    );
    expect(secondPage.data?.map((entry) => entry.action)).toEqual([
      "MEMBER_ADDED",
      "PROJECT_CREATED",
    ]);

    const firstIds = new Set(firstPage.data?.map((entry) => entry.id));
    expect(secondPage.data?.every((entry) => !firstIds.has(entry.id))).toBe(true);

    const blankCursor = await request.get(
      `/api/v0/projects/${projectId}/history?limit=2&cursor=`,
      { headers: await apiHeaders(request) },
    );
    const blankCursorPage = await expectSuccess<Array<{ id: string }>>(
      blankCursor,
      200,
      "resource.project.history_retrieved",
    );
    expect(blankCursorPage.data?.map((entry) => entry.id)).toEqual(
      firstPage.data?.map((entry) => entry.id),
    );
  });

  test("makes archived projects read-only for member changes", async ({
    request,
  }) => {
    const organizationId = await createOrganization(request);
    const userId = await createUser(request, organizationId);
    const projectId = await createProject(request, organizationId);
    const archived = await request.patch(
      `/api/v0/projects/${projectId}/archive`,
      { headers: await apiHeaders(request) },
    );
    await expectSuccess<null>(archived, 200, "resource.project.archived");

    const response = await request.post(`/api/v0/projects/${projectId}/members`, {
      headers: await apiHeaders(request),
      data: { principalType: "USER", principalId: userId },
    });
    await expectFailure(response, 409, "domain.conflict", "DOMAIN_CONFLICT");
  });
});
