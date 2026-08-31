import { expect, type APIRequestContext, type APIResponse } from "@playwright/test";
import { z } from "zod";

import { e2eApiEnvironment } from "../support/environment.js";
import { TestDataScope } from "./cleanup.js";
import {
  basicSuccessEnvelopeSchema,
  createdResourceSchema,
  cursorSuccessEnvelopeSchema,
  failureEnvelopeSchema,
  oauthErrorSchema,
  oauthTokenSchema,
  permissionListSchema,
  projectHistoryEntrySchema,
  type CreatedResource,
  type CursorPagination,
} from "./schemas.js";

export type CreatedOrganization = CreatedResource & { key: string };
export type CreatedUser = CreatedResource & { username: string };

type GetOptions = NonNullable<Parameters<APIRequestContext["get"]>[1]>;
type PostOptions = NonNullable<Parameters<APIRequestContext["post"]>[1]>;
type PutOptions = NonNullable<Parameters<APIRequestContext["put"]>[1]>;
type PatchOptions = NonNullable<Parameters<APIRequestContext["patch"]>[1]>;
type DeleteOptions = NonNullable<Parameters<APIRequestContext["delete"]>[1]>;

const basicAuthorization = (clientId: string, clientSecret: string): string =>
  `Basic ${Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64")}`;

export const uniqueName = (prefix: string): string => `${prefix}-${crypto.randomUUID().slice(0, 12)}`;

export const expectSuccess = async <DataSchema extends z.ZodType>(
  response: APIResponse,
  status: number,
  messageCode: string,
  dataSchema: DataSchema,
): Promise<z.output<DataSchema>> => {
  expect(response.status()).toBe(status);
  const body = basicSuccessEnvelopeSchema(status, messageCode).parse(await response.json());
  return dataSchema.parse(body.data);
};

export const expectCursorSuccess = async <DataSchema extends z.ZodType>(
  response: APIResponse,
  status: number,
  messageCode: string,
  dataSchema: DataSchema,
): Promise<{ data: z.output<DataSchema>; pagination: CursorPagination }> => {
  expect(response.status()).toBe(status);
  const body = cursorSuccessEnvelopeSchema(status, messageCode).parse(await response.json());
  return { data: dataSchema.parse(body.data), pagination: body.meta.pagination };
};

export const expectFailure = async (response: APIResponse, status: number, messageCode: string, errorCode: string) => {
  expect(response.status()).toBe(status);
  const body = failureEnvelopeSchema(status, messageCode, errorCode).parse(await response.json());
  return body.error;
};

export const expectOAuthError = async (response: APIResponse, status: number, errorCode: string) => {
  expect(response.status()).toBe(status);
  const body = oauthErrorSchema.parse(await response.json());
  expect(body.error).toBe(errorCode);
  return body;
};

export class TaskmigoApi {
  private accessTokenPromise?: Promise<string>;

  constructor(
    readonly raw: APIRequestContext,
    readonly cleanup = new TestDataScope(),
  ) {}

  async requestToken(options: { clientSecret?: string; scope?: string } = {}): Promise<APIResponse> {
    const environment = e2eApiEnvironment();
    return this.raw.post(new URL("/oauth2/token", environment.authorizationOrigin).href, {
      headers: {
        Authorization: basicAuthorization(environment.clientId, options.clientSecret ?? environment.clientSecret),
      },
      form: { grant_type: "client_credentials", scope: options.scope ?? "taskmigo.api" },
    });
  }

  async get(path: string, options: GetOptions = {}): Promise<APIResponse> {
    return this.raw.get(path, { ...options, headers: await this.headers(options.headers) });
  }

  async post(path: string, options: PostOptions = {}): Promise<APIResponse> {
    return this.raw.post(path, { ...options, headers: await this.headers(options.headers) });
  }

  async put(path: string, options: PutOptions = {}): Promise<APIResponse> {
    return this.raw.put(path, { ...options, headers: await this.headers(options.headers) });
  }

  async patch(path: string, options: PatchOptions = {}): Promise<APIResponse> {
    return this.raw.patch(path, { ...options, headers: await this.headers(options.headers) });
  }

  async delete(path: string, options: DeleteOptions = {}): Promise<APIResponse> {
    return this.raw.delete(path, { ...options, headers: await this.headers(options.headers) });
  }

  async permissions(): Promise<string[]> {
    const response = await this.get("/api/v0/permissions");
    return expectSuccess(response, 200, "resource.permissions.retrieved", permissionListSchema);
  }

  async createOrganization(key = uniqueName("org")): Promise<CreatedOrganization> {
    const response = await this.post("/api/v0/organizations", {
      data: { key, name: `Organization ${key}` },
    });
    const data = await expectSuccess(response, 201, "resource.organization.created", createdResourceSchema);
    expect(response.headers().location).toBe(`/api/v0/organizations/${data.id}`);
    this.cleanup.track("organizations", data.id);
    return { id: data.id, key };
  }

  async createUser(organizationId: string | null, username = uniqueName("user")): Promise<CreatedUser> {
    const response = await this.post("/api/v0/users", {
      data: {
        organizationId,
        username,
        emails: [`${username}@example.test`],
        firstName: "E2E",
        lastName: "User",
      },
    });
    const data = await expectSuccess(response, 201, "resource.user.created", createdResourceSchema);
    this.cleanup.track("users", data.id);
    return { id: data.id, username };
  }

  async createRole(organizationId: string, permissions: string[] = []): Promise<string> {
    const response = await this.post(`/api/v0/organizations/${organizationId}/roles`, {
      data: { name: uniqueName("role"), description: "E2E role", permissions },
    });
    const data = await expectSuccess(response, 201, "resource.role.created", createdResourceSchema);
    this.cleanup.track("roles", data.id);
    return data.id;
  }

  async createGroup(organizationId: string): Promise<string> {
    const response = await this.post(`/api/v0/organizations/${organizationId}/groups`, {
      data: { name: uniqueName("group"), description: "E2E group" },
    });
    const data = await expectSuccess(response, 201, "resource.group.created", createdResourceSchema);
    this.cleanup.track("groups", data.id);
    return data.id;
  }

  async addGroupMember(groupId: string, userId: string): Promise<void> {
    const response = await this.put(`/api/v0/groups/${groupId}/members/${userId}`);
    await expectSuccess(response, 200, "resource.group.member_added", z.null());
    if (!this.cleanup.owns("groups", groupId) || !this.cleanup.owns("users", userId)) {
      this.cleanup.defer(() => this.removeGroupMember(groupId, userId));
    }
  }

  async removeGroupMember(groupId: string, userId: string): Promise<void> {
    const response = await this.delete(`/api/v0/groups/${groupId}/members/${userId}`);
    await expectSuccess(response, 200, "resource.group.member_removed", z.null());
  }

  async createProject(organizationId: string, key = uniqueName("project")): Promise<string> {
    const response = await this.post(`/api/v0/organizations/${organizationId}/projects`, {
      data: { key, name: `Project ${key}`, description: "E2E project" },
    });
    const data = await expectSuccess(response, 201, "resource.project.created", createdResourceSchema);
    this.cleanup.track("projects", data.id);
    return data.id;
  }

  async addProjectMember(projectId: string, principalType: "USER" | "GROUP", principalId: string): Promise<string> {
    const response = await this.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType, principalId },
    });
    const data = await expectSuccess(response, 201, "resource.project.member_added", createdResourceSchema);
    if (!this.cleanup.owns("projects", projectId)) {
      this.cleanup.defer(() => this.removeProjectMember(projectId, data.id));
    }
    return data.id;
  }

  async removeProjectMember(projectId: string, memberId: string): Promise<void> {
    const response = await this.delete(`/api/v0/projects/${projectId}/members/${memberId}`);
    await expectSuccess(response, 200, "resource.project.member_removed", z.null());
  }

  async setProjectMemberRoles(projectId: string, memberId: string, roleIds: string[]): Promise<void> {
    const response = await this.put(`/api/v0/projects/${projectId}/members/${memberId}/roles`, {
      data: { roleIds },
    });
    await expectSuccess(response, 200, "resource.project.member_roles_updated", z.null());
  }

  async archiveProject(projectId: string): Promise<void> {
    const response = await this.patch(`/api/v0/projects/${projectId}/archive`);
    await expectSuccess(response, 200, "resource.project.archived", z.null());
  }

  async effectivePermissions(projectId: string, userId: string): Promise<string[]> {
    const response = await this.get(`/api/v0/projects/${projectId}/users/${userId}/effective-permissions`);
    return expectSuccess(response, 200, "resource.project.effective_permissions_retrieved", permissionListSchema);
  }

  async history(projectId: string, query = "") {
    const response = await this.get(`/api/v0/projects/${projectId}/history${query}`);
    return expectCursorSuccess(response, 200, "resource.project.history_retrieved", z.array(projectHistoryEntrySchema));
  }

  async cleanupOwnedData(): Promise<void> {
    const failures = await this.cleanup.runDeferred();
    if (this.cleanup.hasResources()) {
      try {
        const response = await this.post("/api/v0/testing/cleanup", { data: this.cleanup.snapshot() });
        await expectSuccess(response, 200, "testing.data.cleaned", z.null());
        this.cleanup.clearResources();
      } catch (error) {
        failures.push(error);
      }
    }
    if (failures.length > 0) throw new AggregateError(failures, "Failed to clean up E2E test data");
  }

  private async accessToken(): Promise<string> {
    this.accessTokenPromise ??= this.fetchAccessToken();
    return this.accessTokenPromise;
  }

  private async fetchAccessToken(): Promise<string> {
    const response = await this.requestToken();
    expect(response.status()).toBe(200);
    const body = oauthTokenSchema.parse(await response.json());
    return body.access_token;
  }

  private async headers(headers: Record<string, string> | undefined): Promise<Record<string, string>> {
    return { Authorization: `Bearer ${await this.accessToken()}`, ...headers };
  }
}
