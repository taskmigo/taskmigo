import { expectFailure, uniqueName } from "./client.js";
import { expect, test } from "./fixtures.js";

test.describe("Project API @api @projects", () => {
  test("creates project members, assigns roles, and calculates effective permissions", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const groupUser = await api.createUser(organization.id);
    const roleId = await api.createRole(organization.id, ["project.read", "project.update"]);
    const groupId = await api.createGroup(organization.id);
    await api.addGroupMember(groupId, groupUser.id);
    const projectId = await api.createProject(organization.id);

    const memberId = await api.addProjectMember(projectId, "USER", user.id);
    await api.setProjectMemberRoles(projectId, memberId, [roleId]);

    const groupMemberId = await api.addProjectMember(projectId, "GROUP", groupId);
    await api.setProjectMemberRoles(projectId, groupMemberId, [roleId]);

    expect(new Set(await api.effectivePermissions(projectId, user.id))).toEqual(
      new Set(["project.read", "project.update"]),
    );
    expect(new Set(await api.effectivePermissions(projectId, groupUser.id))).toEqual(
      new Set(["project.read", "project.update"]),
    );

    const duplicate = await api.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType: "USER", principalId: user.id },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");
  });

  test("rejects invalid project creation and duplicate project keys", async ({ api }) => {
    const organization = await api.createOrganization();
    const key = uniqueName("project");
    await api.createProject(organization.id, key);

    const duplicate = await api.post(`/api/v0/organizations/${organization.id}/projects`, {
      data: { key, name: "Duplicate", description: null },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");

    const invalid = await api.post(`/api/v0/organizations/${organization.id}/projects`, {
      data: { key: "", name: " ", description: null },
    });
    await expectFailure(invalid, 422, "validation.failed", "VALIDATION_ERROR");

    const missingOrganization = await api.post(`/api/v0/organizations/${crypto.randomUUID()}/projects`, {
      data: { key: uniqueName("project"), name: "Missing", description: null },
    });
    await expectFailure(missingOrganization, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("rejects invalid, missing, and cross-organization project member references", async ({ api }) => {
    const projectOrganization = await api.createOrganization();
    const otherOrganization = await api.createOrganization();
    const projectId = await api.createProject(projectOrganization.id);
    const projectUser = await api.createUser(projectOrganization.id);
    const otherRole = await api.createRole(otherOrganization.id, ["project.read"]);

    const invalidType = await api.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType: "TEAM", principalId: projectUser.id },
    });
    await expectFailure(invalidType, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");

    const missingPrincipal = await api.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType: "USER", principalId: crypto.randomUUID() },
    });
    await expectFailure(missingPrincipal, 404, "domain.not_found", "DOMAIN_NOT_FOUND");

    const memberId = await api.addProjectMember(projectId, "USER", projectUser.id);
    const crossOrganizationRole = await api.put(`/api/v0/projects/${projectId}/members/${memberId}/roles`, {
      data: { roleIds: [otherRole] },
    });
    await expectFailure(crossOrganizationRole, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");

    const missingRole = await api.put(`/api/v0/projects/${projectId}/members/${memberId}/roles`, {
      data: { roleIds: [crypto.randomUUID()] },
    });
    await expectFailure(missingRole, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");
  });

  test("handles missing project members and makes removed memberships unavailable", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const projectId = await api.createProject(organization.id);
    const memberId = await api.addProjectMember(projectId, "USER", user.id);

    await api.removeProjectMember(projectId, memberId);

    const missingRoles = await api.put(`/api/v0/projects/${projectId}/members/${memberId}/roles`, {
      data: { roleIds: [] },
    });
    await expectFailure(missingRoles, 404, "domain.not_found", "DOMAIN_NOT_FOUND");

    const missingRemoval = await api.delete(`/api/v0/projects/${projectId}/members/${memberId}`);
    await expectFailure(missingRemoval, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("keeps archived projects readable but rejects mutations", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const projectId = await api.createProject(organization.id);
    await api.archiveProject(projectId);

    expect(await api.effectivePermissions(projectId, user.id)).toEqual([]);

    const addMember = await api.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType: "USER", principalId: user.id },
    });
    await expectFailure(addMember, 409, "domain.conflict", "DOMAIN_CONFLICT");

    const archiveAgain = await api.patch(`/api/v0/projects/${projectId}/archive`);
    await expectFailure(archiveAgain, 409, "domain.conflict", "DOMAIN_CONFLICT");
  });

  test("records actor context and paginates project history", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const projectId = await api.createProject(organization.id);
    const memberId = await api.addProjectMember(projectId, "USER", user.id);
    await api.removeProjectMember(projectId, memberId);
    await api.archiveProject(projectId);

    const first = await api.history(projectId, "?limit=2");
    expect(first.data).toHaveLength(2);
    expect(first.pagination.next_cursor).toEqual(expect.any(String));
    expect(first.data[0].actor).toMatchObject({ id: "taskmigo-helm-test" });

    const firstIds = first.data.map((event) => event.id);
    const second = await api.history(projectId, `?limit=2&cursor=${first.pagination.next_cursor}`);
    expect(second.data.length).toBeGreaterThan(0);
    expect(second.data.every((event) => !firstIds.includes(event.id))).toBe(true);

    const all = await api.history(projectId, "?limit=10");
    expect(new Set(all.data.map((event) => event.action))).toEqual(
      new Set(["PROJECT_CREATED", "MEMBER_ADDED", "MEMBER_REMOVED", "PROJECT_ARCHIVED"]),
    );
    expect(all.data.every((event) => event.occurredAt.length > 0)).toBe(true);
  });

  test("rejects invalid history pagination requests", async ({ api }) => {
    const organization = await api.createOrganization();
    const projectId = await api.createProject(organization.id);

    const invalidCursor = await api.get(`/api/v0/projects/${projectId}/history?cursor=not-a-cursor&limit=20`);
    await expectFailure(invalidCursor, 400, "query.invalid", "INVALID_QUERY_PARAMETER");

    const invalidLimit = await api.get(`/api/v0/projects/${projectId}/history?limit=1000`);
    await expectFailure(invalidLimit, 422, "validation.failed", "VALIDATION_ERROR");
  });
});
