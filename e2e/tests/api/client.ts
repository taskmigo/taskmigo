import { expect, type APIRequestContext, type APIResponse } from "@playwright/test";

import { e2eApiEnvironment } from "../support/environment.js";

export interface ApiEnvelope<T = unknown> {
  success: boolean;
  status_code: number;
  message: {
    code: string;
    text: string;
  };
  error: null | {
    code?: string;
    message?: string;
    form_errors?: Record<string, string>;
  };
  meta: {
    execution: {
      started_at: string;
      duration_ms: number;
    };
    pagination?: {
      type: string;
      cursor?: {
        next_cursor: string | null;
        prev_cursor: string | null;
        has_more: boolean;
      };
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

let cachedAccessToken: string | undefined;

const basicAuthorization = (clientId: string, clientSecret: string): string =>
  `Basic ${Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64")}`;

export const requestClientCredentialsToken = async (
  request: APIRequestContext,
  options: { clientSecret?: string; scope?: string } = {},
): Promise<APIResponse> => {
  const environment = e2eApiEnvironment();
  return request.post(
    new URL("/oauth2/token", environment.authorizationOrigin).href,
    {
      headers: {
        Authorization: basicAuthorization(
          environment.clientId,
          options.clientSecret ?? environment.clientSecret,
        ),
      },
      form: {
        grant_type: "client_credentials",
        scope: options.scope ?? "taskmigo.api",
      },
    },
  );
};

export const accessToken = async (
  request: APIRequestContext,
): Promise<string> => {
  if (cachedAccessToken) return cachedAccessToken;

  const response = await requestClientCredentialsToken(request);
  expect(response.status()).toBe(200);
  const body = (await response.json()) as TokenResponse;
  expect(body.token_type?.toLowerCase()).toBe("bearer");
  expect(body.access_token).toEqual(expect.any(String));
  cachedAccessToken = body.access_token;
  if (!cachedAccessToken)
    throw new Error("OAuth token response omitted access_token");
  return cachedAccessToken;
};

export const apiHeaders = async (
  request: APIRequestContext,
): Promise<Record<string, string>> => ({
  Authorization: `Bearer ${await accessToken(request)}`,
});

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
    meta: {
      execution: {
        started_at: expect.any(String),
        duration_ms: expect.any(Number),
      },
    },
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
    meta: {
      execution: {
        started_at: expect.any(String),
        duration_ms: expect.any(Number),
      },
    },
  });
  return body;
};

export const uniqueName = (prefix: string): string =>
  `${prefix}-${crypto.randomUUID().slice(0, 12)}`;
