import { expect, test, type APIRequestContext } from "@playwright/test";
import * as z from "zod";

import { apiErrorSchema, basicMetaSchema, messageSchema, offsetMetaSchema, successApiResponseSchema } from "./types.js";

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
  status: z.enum(["ACTIVE", "SUSPENDED", "DISABLED", "RETAINED"]),
  retainedAt: z.iso.datetime().nullable(),
});

export const createUserResponseSchema = successApiResponseSchema(
  201,
  z.strictObject({ id: z.uuid() }),
  basicMetaSchema,
);

export const createUserErrorResponseSchema = z.strictObject({
  success: z.literal(false),
  statusCode: z.number().int(),
  message: messageSchema.nullable(),
  error: apiErrorSchema,
  meta: basicMetaSchema,
  data: z.null(),
});

export const listUsersResponseSchema = successApiResponseSchema(200, z.array(userInfoSchema), offsetMetaSchema);

export type UserInfo = z.infer<typeof userInfoSchema>;
export type CreateUserResponse = z.infer<typeof createUserResponseSchema>;
export type CreateUserErrorResponse = z.infer<typeof createUserErrorResponseSchema>;
export type ListUsersResponse = z.infer<typeof listUsersResponseSchema>;

export class UsersApi {
  private readonly usersUrl: string;
  private readonly browserOrigin: string;

  constructor(
    private readonly request: APIRequestContext,
    browserApiBaseUrl: string,
  ) {
    this.usersUrl = new URL("v0/users", browserApiBaseUrl).href;
    this.browserOrigin = new URL(browserApiBaseUrl).origin;
  }

  async create(body: CreateUserRequest): Promise<CreateUserResponse> {
    return test.step("POST /api/v0/users", async () => {
      const response = await this.request.post(this.usersUrl, {
        headers: { Origin: this.browserOrigin },
        data: body,
        failOnStatusCode: true,
      });
      expect(response.status()).toBe(201);
      return createUserResponseSchema.parse(await response.json());
    });
  }

  async list(query?: ListUsersRequest): Promise<ListUsersResponse> {
    const params = new URLSearchParams();
    if (query?.page !== undefined) params.set("page", String(query.page));
    if (query?.pageSize !== undefined) params.set("pageSize", String(query.pageSize));

    const suffix = params.size === 0 ? "" : `?${params.toString()}`;
    return test.step(`GET /api/v0/users${suffix}`, async () => {
      const response = await this.request.get(this.usersUrl, {
        params,
        failOnStatusCode: true,
      });
      expect(response.status()).toBe(200);
      return listUsersResponseSchema.parse(await response.json());
    });
  }

  async createError(body: CreateUserRequest): Promise<CreateUserErrorResponse> {
    return test.step("POST /api/v0/users expecting an API error", async () => {
      const response = await this.request.post(this.usersUrl, {
        headers: { Origin: this.browserOrigin },
        data: body,
        failOnStatusCode: false,
      });
      expect(response.ok()).toBe(false);
      return createUserErrorResponseSchema.parse(await response.json());
    });
  }
}

export class UsersApiExtensions {
  constructor(private readonly users: UsersApi) {}

  async createMany(bodies: readonly CreateUserRequest[]): Promise<CreateUserResponse[]> {
    return test.step(`Create ${bodies.length} users`, async () => {
      const responses: CreateUserResponse[] = [];
      for (const body of bodies) {
        responses.push(await this.users.create(body));
      }
      return responses;
    });
  }

  async createExpectingError(body: CreateUserRequest): Promise<CreateUserErrorResponse> {
    return this.users.createError(body);
  }

  async findById(id: string): Promise<UserInfo> {
    return test.step(`Find user ${id} through the list API`, async () => {
      const pageSize = 100;
      let page = 1;

      while (true) {
        const response = await this.users.list({ page, pageSize });
        const user = response.data.find((candidate) => candidate.id === id);
        if (user !== undefined) return user;

        const { currentPage, totalPages } = response.meta.pagination.offset;
        if (currentPage >= totalPages) {
          throw new Error(`User ${id} was not discoverable through GET /api/v0/users`);
        }
        page = currentPage + 1;
      }
    });
  }
}
