import { expect, test, type APIRequestContext } from "@playwright/test";
import * as z from "zod";

import { basicMetaSchema, offsetMetaSchema, successApiResponseSchema } from "./types.js";

export interface CreateUserRequest {
  username: string;
  emails?: string[];
  firstName: string;
  lastName: string;
  roleIds?: string[];
  groupIds?: string[];
}

export interface ListUsersRequest {
  page?: number;
  pageSize?: number;
}

export const userInfoSchema = z.strictObject({
  id: z.uuid(),
  username: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  emails: z.array(z.string()),
  displayName: z.string(),
});

export const createUserResponseSchema = successApiResponseSchema(
  201,
  z.strictObject({ id: z.uuid() }),
  basicMetaSchema,
);

export const listUsersResponseSchema = successApiResponseSchema(200, z.array(userInfoSchema), offsetMetaSchema);

export type UserInfo = z.infer<typeof userInfoSchema>;
export type CreateUserResponse = z.infer<typeof createUserResponseSchema>;
export type ListUsersResponse = z.infer<typeof listUsersResponseSchema>;

interface UsersApiTransport {
  readonly request: APIRequestContext;
  readonly usersUrl: string;
  readonly browserOrigin: string;
}

const createUsersApiTransport = (request: APIRequestContext, browserApiBaseUrl: string): UsersApiTransport => ({
  request,
  usersUrl: new URL("v0/users", browserApiBaseUrl).href,
  browserOrigin: new URL(browserApiBaseUrl).origin,
});

const executeCreateUser = async (
  transport: UsersApiTransport,
  body: CreateUserRequest,
): Promise<CreateUserResponse> =>
  test.step("POST /api/v0/users", async () => {
    const response = await transport.request.post(transport.usersUrl, {
      headers: { Origin: transport.browserOrigin },
      data: body,
      failOnStatusCode: true,
    });
    expect(response.status()).toBe(201);
    return createUserResponseSchema.parse(await response.json());
  });

export class UsersApi {
  readonly extensions: UsersApiExtensions;

  private readonly transport: UsersApiTransport;

  constructor(request: APIRequestContext, browserApiBaseUrl: string) {
    this.transport = createUsersApiTransport(request, browserApiBaseUrl);
    this.extensions = new UsersApiExtensions(this.transport);
  }

  async create(body: CreateUserRequest): Promise<CreateUserResponse> {
    return executeCreateUser(this.transport, body);
  }

  async list(query?: ListUsersRequest): Promise<ListUsersResponse> {
    const params = new URLSearchParams();
    if (query?.page !== undefined) params.set("page", String(query.page));
    if (query?.pageSize !== undefined) params.set("pageSize", String(query.pageSize));

    const suffix = params.size === 0 ? "" : `?${params.toString()}`;
    return test.step(`GET /api/v0/users${suffix}`, async () => {
      const response = await this.transport.request.get(this.transport.usersUrl, {
        params,
        failOnStatusCode: true,
      });
      expect(response.status()).toBe(200);
      return listUsersResponseSchema.parse(await response.json());
    });
  }
}

export class UsersApiExtensions {
  constructor(private readonly transport: UsersApiTransport) {}

  async createMany(bodies: readonly CreateUserRequest[]): Promise<CreateUserResponse[]> {
    return test.step(`Create ${bodies.length} users`, async () => {
      const responses: CreateUserResponse[] = [];
      for (const body of bodies) {
        responses.push(await executeCreateUser(this.transport, body));
      }
      return responses;
    });
  }
}
