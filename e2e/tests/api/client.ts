import { expect, type APIRequestContext, type APIResponse } from "@playwright/test";

import { e2eApiEnvironment } from "../support/environment.js";

export interface ApiEnvelope<T = unknown> {
  success: boolean;
  status_code: number;
  message: { code: string; text: string };
  error: null | { code?: string; message?: string; form_errors?: Record<string, string> };
  meta: {
    execution: { started_at: string; duration_ms: number };
    pagination?: {
      type: string;
      cursor?: { next_cursor: string | null; prev_cursor: string | null; has_more: boolean };
    };
  };
  data: T | null;
}

interface TokenResponse {
  access_token?: string;
  token_type?: string;
  expires_in?: number;
  scope?: string;
  error?: string;
}

interface CreatedResource {
  id: string;
}

export interface CreatedOrganization extends CreatedResource {
  key: string;
}

export interface CreatedUser extends CreatedResource {
  username: string;
}

type GetOptions = NonNullable<Parameters<APIRequestContext["get"]>[1]>;
type PostOptions = NonNullable<Parameters<APIRequestContext["post"]>[1]>;
type PutOptions = NonNullable<Parameters<APIRequestContext["put"]>[1]>;
type PatchOptions = NonNullable<Parameters<APIRequestContext["patch"]>[1]>;
type DeleteOptions = NonNullable<Parameters<APIRequestContext["delete"]>[1]>;

const basicAuthorization = (clientId: string, clientSecret: string): string =>
  `Basic ${Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64")}`;

export const uniqueName = (prefix: string): string => `${prefix}-${crypto.randomUUID().slice(0, 12)}`;

export const expectSuccess = async <T>(
  response: APIResponse,
  status: number,
  messageCode: string,
): Promise<ApiEnvelope<T>> => {
  expect(response.status()).toBe(status);
  const body = (await response.json()) as ApiEnvelope<T>;
  expect(body).toMatchObject({
    success: true,
    status_code: status,
    message: { code: messageCode },
    error: null,
    meta: { execution: { started_at: expect.any(String), duration_ms: expect.any(Number) } },
  });
  expect(Number.isNaN(Date.parse(body.meta.execution.started_at))).toBe(false);
  expect(body.meta.execution.duration_ms).toBeGreaterThanOrEqual(0);
  return body;
};

export const expectFailure = async (
  response: APIResponse,
  status: number,
  messageCode: string,
  errorCode: string,
): Promise<ApiEnvelope<never>> => {
  expect(response.status()).toBe(status);
  const body = (await response.json()) as ApiEnvelope<never>;
  expect(body).toMatchObject({
    success: false,
    status_code: status,
    message: { code: messageCode },
    error: { code: errorCode },
    data: null,
    meta: { execution: { started_at: expect.any(String), duration_ms: expect.any(Number) } },
  });
  return body;
};

export class TaskmigoApi {
  private accessTokenPromise?: Promise<string>;

  constructor(readonly raw: APIRequestContext) {}

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
    const body = await expectSuccess<string[]>(response, 200, "resource.permissions.retrieved");
    return body.data ?? [];
  }

  async createOrganization(key = uniqueName("org")): Promise<CreatedOrganization> {
    const response = await this.post("/api/v0/organizations", {
      data: { key, name: `Organization ${key}` },
    });
    const body = await expectSuccess<CreatedResource>(response, 201, "resource.organization.created");
    const id = body.data!.id;
    expect(response.headers().location).toBe(`/api/v0/organizations/${id}`);
    return { id, key };
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
    const body = await expectSuccess<CreatedResource>(response, 201, "resource.user.created");
    return { id: body.data!.id, username };
  }

  async createRole(organizationId: string, permissions: string[] = []): Promise<string> {
    const response = await this.post(`/api/v0/organizations/${organizationId}/roles`, {
      data: { name: uniqueName("role"), description: "E2E role", permissions },
    });
    const body = await expectSuccess<CreatedResource>(response, 201, "resource.role.created");
    return body.data!.id;
  }

  async createGroup(organizationId: string): Promise<string> {
    const response = await this.post(`/api/v0/organizations/${organizationId}/groups`, {
      data: { name: uniqueName("group"), description: "E2E group" },
    });
    const body = await expectSuccess<CreatedResource>(response, 201, "resource.group.created");
    return body.data!.id;
  }

  async addGroupMember(groupId: string, userId: string): Promise<void> {
    const response = await this.put(`/api/v0/groups/${groupId}/members/${userId}`);
    await expectSuccess<null>(response, 200, "resource.group.member_added");
  }

  async removeGroupMember(groupId: string, userId: string): Promise<void> {
    const response = await this.delete(`/api/v0/groups/${groupId}/members/${userId}`);
    await expectSuccess<null>(response, 200, "resource.group.member_removed");
  }

  async createProject(organizationId: string, key = uniqueName("project")): Promise<string> {
    const response = await this.post(`/api/v0/organizations/${organizationId}/projects`, {
      data: { key, name: `Project ${key}`, description: "E2E project" },
    });
    const body = await expectSuccess<CreatedResource>(response, 201, "resource.project.created");
    return body.data!.id;
  }

  async addProjectMember(projectId: string, principalType: "USER" | "GROUP", principalId: string): Promise<string> {
    const response = await this.post(`/api/v0/projects/${projectId}/members`, {
      data: { principalType, principalId },
    });
    const body = await expectSuccess<CreatedResource>(response, 201, "resource.project.member_added");
    return body.data!.id;
  }

  async removeProjectMember(projectId: string, memberId: string): Promise<void> {
    const response = await this.delete(`/api/v0/projects/${projectId}/members/${memberId}`);
    await expectSuccess<null>(response, 200, "resource.project.member_removed");
  }

  async setProjectMemberRoles(projectId: string, memberId: string, roleIds: string[]): Promise<void> {
    const response = await this.put(`/api/v0/projects/${projectId}/members/${memberId}/roles`, {
      data: { roleIds },
    });
    await expectSuccess<null>(response, 200, "resource.project.member_roles_updated");
  }

  async archiveProject(projectId: string): Promise<void> {
    const response = await this.patch(`/api/v0/projects/${projectId}/archive`);
    await expectSuccess<null>(response, 200, "resource.project.archived");
  }

  async effectivePermissions(projectId: string, userId: string): Promise<string[]> {
    const response = await this.get(`/api/v0/projects/${projectId}/users/${userId}/effective-permissions`);
    const body = await expectSuccess<string[]>(response, 200, "resource.project.effective_permissions_retrieved");
    return body.data ?? [];
  }

  async history<T>(projectId: string, query = ""): Promise<ApiEnvelope<T>> {
    const response = await this.get(`/api/v0/projects/${projectId}/history${query}`);
    return expectSuccess<T>(response, 200, "resource.project.history_retrieved");
  }

  private async accessToken(): Promise<string> {
    this.accessTokenPromise ??= this.fetchAccessToken();
    return this.accessTokenPromise;
  }

  private async fetchAccessToken(): Promise<string> {
    const response = await this.requestToken();
    expect(response.status()).toBe(200);
    const body = (await response.json()) as TokenResponse;
    expect(body.token_type?.toLowerCase()).toBe("bearer");
    expect(body.access_token).toEqual(expect.any(String));
    if (!body.access_token) throw new Error("OAuth token response omitted access_token");
    return body.access_token;
  }

  private async headers(headers: Record<string, string> | undefined): Promise<Record<string, string>> {
    return { Authorization: `Bearer ${await this.accessToken()}`, ...headers };
  }
}
