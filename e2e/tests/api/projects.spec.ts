import { expectFailure, uniqueName } from "./client.js";
import { expect, test } from "./fixtures.js";

interface HistoryEntry {
  id: string;
  action: string;
  actor: { type: string; id: string; displayName: string };
}

test.describe("Project API @api @projects", () => {
  test("creates projects and rejects duplicate keys or missing organizations", async ({ api }) => {
    const organization = await api.createOrganization();
    const key = uniqueName("project");
    await api.createProject(organization.id, key);

    const duplicate = await api.post(`/api/v0/organizations/${organization.id}/projects`, {
      data: { key, name: "Duplicate project" },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");

    const missingOrganization = await api.post(`/api/v0/organizations/${crypto.randomUUID()}/projects`, {
      data: { key: uniqueName("project"), name: "Missing organization" },
    });
    await expectFailure(missingOrganization, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("validates project payloads and missing projects", async ({ api }) => {
    const organization = await api.createOrganization();
    const invalid = await api.post(`/api/v0/organizations/${organization.id}/projects`, {
      data: { key: " ", name: "" },
    });
    await expectFailure(invalid, 422, "validation.failed", "VALIDATION_ERROR");

    const missing = await api.patch(`/api/v0/projects/${crypto.randomUUID()}/archive`);
    await expectFailure(missing, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("manages project members and rejects invalid principals", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const projectId = await api.createProject(organization.id);
    const memberId = await api.addProjectMember(projectId, "USER", user.id);

    const duplicate = await api.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType: "USER", principalId: user.id },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");

    const invalidType = await api.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType: "ROBOT", principalId: user.id },
    });
    await expectFailure(invalidType, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");

    const missingUser = await api.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType: "USER", principalId: crypto.randomUUID() },
    });
    await expectFailure(missingUser, 404, "domain.not_found", "DOMAIN_NOT_FOUND");

    await api.removeProjectMember(projectId, memberId);
    const removedAgain = await api.delete(`/api/v0/projects/${projectId}/members/${memberId}`);
    await expectFailure(removedAgain, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("rejects a project member id that belongs to another project", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const firstProjectId = await api.createProject(organization.id);
    const secondProjectId = await api.createProject(organization.id);
    const memberId = await api.addProjectMember(firstProjectId, "USER", user.id);

    const response = await api.delete(`/api/v0/projects/${secondProjectId}/members/${memberId}`);
    await expectFailure(response, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("assigns roles and rejects invalid role assignments", async ({ api }) => {
    const organization = await api.createOrganization();
    const otherOrganization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const projectId = await api.createProject(organization.id);
    const memberId = await api.addProjectMember(projectId, "USER", user.id);
    const roleId = await api.createRole(organization.id, ["project.read"]);
    const foreignRoleId = await api.createRole(otherOrganization.id, ["project.update"]);

    await api.setProjectMemberRoles(projectId, memberId, [roleId]);

    const missingRole = await api.put(`/api/v0/projects/${projectId}/members/${memberId}/roles`, {
      data: { roleIds: [crypto.randomUUID()] },
    });
    await expectFailure(missingRole, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");

    const foreignRole = await api.put(`/api/v0/projects/${projectId}/members/${memberId}/roles`, {
      data: { roleIds: [foreignRoleId] },
    });
    await expectFailure(foreignRole, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");
  });

  test("calculates direct and group-derived effective permissions", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const projectId = await api.createProject(organization.id);
    const userRoleId = await api.createRole(organization.id, ["project.read"]);
    const groupRoleId = await api.createRole(organization.id, ["project.update"]);

    const userMemberId = await api.addProjectMember(projectId, "USER", user.id);
    await api.setProjectMemberRoles(projectId, userMemberId, [userRoleId]);

    const groupId = await api.createGroup(organization.id);
    await api.addGroupMember(groupId, user.id);
    const groupMemberId = await api.addProjectMember(projectId, "GROUP", groupId);
    await api.setProjectMemberRoles(projectId, groupMemberId, [groupRoleId]);

    expect(new Set(await api.effectivePermissions(projectId, user.id))).toEqual(
      new Set(["project.read", "project.update"]),
    );
  });

  test("returns not found for effective-permission lookups with missing resources", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const projectId = await api.createProject(organization.id);

    const missingUser = await api.get(
      `/api/v0/projects/${projectId}/users/${crypto.randomUUID()}/effective-permissions`,
    );
    await expectFailure(missingUser, 404, "domain.not_found", "DOMAIN_NOT_FOUND");

    const missingProject = await api.get(
      `/api/v0/projects/${crypto.randomUUID()}/users/${user.id}/effective-permissions`,
    );
    await expectFailure(missingProject, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("records project history and paginates without overlap", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const roleId = await api.createRole(organization.id, ["project.read"]);
    const projectId = await api.createProject(organization.id);
    const memberId = await api.addProjectMember(projectId, "USER", user.id);
    await api.setProjectMemberRoles(projectId, memberId, [roleId]);
    await api.archiveProject(projectId);

    const firstPage = await api.history<HistoryEntry[]>(projectId, "?limit=2");
    expect(firstPage.meta.pagination).toMatchObject({
      type: "cursor",
      cursor: { next_cursor: expect.any(String), prev_cursor: null, has_more: true },
    });
    expect(firstPage.data?.map(({ action }) => action)).toEqual(["PROJECT_ARCHIVED", "MEMBER_ROLES_CHANGED"]);
    for (const entry of firstPage.data ?? []) {
      expect(entry.actor).toMatchObject({ id: "taskmigo-helm-test", type: "SERVICE" });
    }

    const cursor = firstPage.meta.pagination?.cursor?.next_cursor;
    expect(cursor).toEqual(expect.any(String));
    const secondPage = await api.history<HistoryEntry[]>(projectId, `?limit=2&cursor=${encodeURIComponent(cursor!)}`);
    expect(secondPage.data?.map(({ action }) => action)).toEqual(["MEMBER_ADDED", "PROJECT_CREATED"]);

    const firstIds = new Set(firstPage.data?.map(({ id }) => id));
    expect(secondPage.data?.every(({ id }) => !firstIds.has(id))).toBe(true);

    const blankCursorPage = await api.history<HistoryEntry[]>(projectId, "?limit=2&cursor=");
    expect(blankCursorPage.data?.map(({ id }) => id)).toEqual(firstPage.data?.map(({ id }) => id));
  });

  test("makes archived projects read-only for member changes", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const projectId = await api.createProject(organization.id);
    await api.archiveProject(projectId);

    const response = await api.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType: "USER", principalId: user.id },
    });
    await expectFailure(response, 409, "domain.conflict", "DOMAIN_CONFLICT");
  });
});
