import { TestDataScope } from "./cleanup.js";
import { expectFailure, uniqueName } from "./client.js";
import { expect, test } from "./fixtures.js";

test.describe("API resources @api @resources", () => {
  test("lists the complete permission catalog", async ({ api }) => {
    expect(new Set(await api.permissions())).toEqual(
      new Set(["project.read", "project.update", "project.members.read", "project.members.manage"]),
    );
  });

  test("creates an organization and rejects duplicate keys", async ({ api }) => {
    const organization = await api.createOrganization();
    const duplicate = await api.post("/api/v0/organizations", {
      data: { key: organization.key, name: "Duplicate organization" },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");
  });

  test("validates organization requests and malformed JSON", async ({ api }) => {
    const validation = await api.post("/api/v0/organizations", { data: { key: " ", name: "" } });
    const error = await expectFailure(validation, 422, "validation.failed", "VALIDATION_ERROR");
    expect(error.form_errors).toMatchObject({ key: expect.any(String), name: expect.any(String) });

    const malformed = await api.post("/api/v0/organizations", {
      headers: { "Content-Type": "application/json" },
      data: Buffer.from('{"key":'),
    });
    await expectFailure(malformed, 400, "request.malformed", "MALFORMED_REQUEST");
  });

  test("creates users and rejects invalid user input", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);

    const invalidEmail = await api.post("/api/v0/users", {
      data: {
        organizationId: organization.id,
        username: uniqueName("user"),
        emails: ["not-an-email"],
        firstName: "E2E",
        lastName: "User",
      },
    });
    await expectFailure(invalidEmail, 422, "validation.failed", "VALIDATION_ERROR");

    const reserved = await api.post("/api/v0/users", {
      data: {
        organizationId: organization.id,
        username: "system",
        emails: [],
        firstName: "System",
        lastName: "Duplicate",
      },
    });
    await expectFailure(reserved, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");

    const duplicate = await api.post("/api/v0/users", {
      data: {
        organizationId: organization.id,
        username: user.username,
        emails: [`${uniqueName("other")}@example.test`],
        firstName: "Duplicate",
        lastName: "User",
      },
    });
    await expectFailure(duplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");
  });

  test("rejects a user whose organization does not exist", async ({ api }) => {
    const response = await api.post("/api/v0/users", {
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

  test("creates roles and rejects unknown permissions or missing organizations", async ({ api }) => {
    const organization = await api.createOrganization();
    await api.createRole(organization.id, ["project.read", "project.update"]);

    const unknownPermission = await api.post(`/api/v0/organizations/${organization.id}/roles`, {
      data: { name: uniqueName("role"), permissions: ["project.delete"] },
    });
    await expectFailure(unknownPermission, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");

    const missingOrganization = await api.post(`/api/v0/organizations/${crypto.randomUUID()}/roles`, {
      data: { name: uniqueName("role"), permissions: [] },
    });
    await expectFailure(missingOrganization, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("manages group membership and keeps removal idempotent", async ({ api }) => {
    const organization = await api.createOrganization();
    const user = await api.createUser(organization.id);
    const groupId = await api.createGroup(organization.id);

    await api.addGroupMember(groupId, user.id);
    await api.removeGroupMember(groupId, user.id);
    await api.removeGroupMember(groupId, user.id);
  });

  test("rejects cross-organization and missing group members", async ({ api }) => {
    const groupOrganization = await api.createOrganization();
    const userOrganization = await api.createOrganization();
    const groupId = await api.createGroup(groupOrganization.id);
    const user = await api.createUser(userOrganization.id);

    const crossOrganization = await api.put(`/api/v0/groups/${groupId}/members/${user.id}`);
    await expectFailure(crossOrganization, 400, "domain.bad_request", "DOMAIN_BAD_REQUEST");

    const missingUser = await api.put(`/api/v0/groups/${groupId}/members/${crypto.randomUUID()}`);
    await expectFailure(missingUser, 404, "domain.not_found", "DOMAIN_NOT_FOUND");

    const missingGroup = await api.put(`/api/v0/groups/${crypto.randomUUID()}/members/${user.id}`);
    await expectFailure(missingGroup, 404, "domain.not_found", "DOMAIN_NOT_FOUND");
  });

  test("cleans only resources owned by the current scope", async ({ api }) => {
    const sharedScope = new TestDataScope();
    const sharedOrganization = await api.createOrganization();
    api.cleanup.transfer("organizations", sharedOrganization.id, sharedScope);

    const ownedKey = uniqueName("cleanup");
    await api.createOrganization(ownedKey);
    await api.cleanupOwnedData();

    await api.createOrganization(ownedKey);
    const sharedDuplicate = await api.post("/api/v0/organizations", {
      data: { key: sharedOrganization.key, name: "Still shared" },
    });
    await expectFailure(sharedDuplicate, 409, "domain.conflict", "DOMAIN_CONFLICT");

    sharedScope.transfer("organizations", sharedOrganization.id, api.cleanup);
  });
});
