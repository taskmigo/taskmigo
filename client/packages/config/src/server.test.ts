import { describe, expect, test } from "vitest";
import { ZodError } from "zod";

import { getConfig, parseConfig } from "./server";

const validEnvironment = {
  TASKMIGO_CLIENT_URL: "https://app.example",
  TASKMIGO_AUTH_ISSUER: "https://auth.example",
  TASKMIGO_AUTH_CLIENT_ID: "browser-client",
  TASKMIGO_AUTH_CLIENT_SECRET: "client-secret",
  TASKMIGO_AUTH_SESSION_SECRET: "01234567890123456789012345678901",
  TASKMIGO_AUTH_ALLOW_INSECURE_REQUESTS: "false",
  TASKMIGO_AUTH_CALLBACK_PATH: "/oauth/callback",
  TASKMIGO_AUTH_POST_LOGOUT_REDIRECT_PATH: "/signed-out",
  TASKMIGO_AUTH_DEFAULT_RETURN_TO: "/account",
  TASKMIGO_AUTH_RETURN_TO_PARAMETER: "returnTo",
  TASKMIGO_AUTH_SCOPE: "openid profile api",
  TASKMIGO_AUTH_OIDC_STATE_VERSION: "oidc-v2",
  TASKMIGO_AUTH_REFRESH_SKEW_MILLISECONDS: "15000",
  TASKMIGO_AUTH_COOKIE_VERSION: "cookie-v3",
  TASKMIGO_AUTH_COOKIE_HTTP_ONLY: "true",
  TASKMIGO_AUTH_COOKIE_SAME_SITE: "strict",
  TASKMIGO_AUTH_COOKIE_SECURE: "true",
  TASKMIGO_AUTH_COOKIE_PATH: "/auth",
  TASKMIGO_AUTH_SESSION_COOKIE_NAME: "session",
  TASKMIGO_AUTH_SESSION_COOKIE_AAD: "session-aad",
  TASKMIGO_AUTH_SESSION_MAX_AGE_SECONDS: "7200",
  TASKMIGO_AUTH_TRANSACTION_COOKIE_NAME: "transaction",
  TASKMIGO_AUTH_TRANSACTION_COOKIE_AAD: "transaction-aad",
  TASKMIGO_AUTH_TRANSACTION_MAX_AGE_SECONDS: "300",
} as const;

describe("server configuration", () => {
  test("maps every environment setting into immutable typed configuration", () => {
    const config = parseConfig(validEnvironment);

    expect(config).toEqual({
      appUrl: new URL("https://app.example"),
      auth: {
        issuer: new URL("https://auth.example"),
        clientId: "browser-client",
        clientSecret: "client-secret",
        sessionSecret: "01234567890123456789012345678901",
        allowInsecureRequests: false,
        callbackPath: "/oauth/callback",
        postLogoutRedirectPath: "/signed-out",
        defaultReturnTo: "/account",
        returnToParameter: "returnTo",
        scope: "openid profile api",
        oidcStateVersion: "oidc-v2",
        refreshSkewMilliseconds: 15_000,
        sessionCookie: { name: "session", additionalAuthenticatedData: "session-aad", maxAge: 7200 },
        transactionCookie: {
          name: "transaction",
          additionalAuthenticatedData: "transaction-aad",
          maxAge: 300,
        },
        cookie: {
          version: "cookie-v3",
          attributes: { httpOnly: true, sameSite: "strict", secure: true, path: "/auth" },
        },
      },
    });
    expect(Object.isFrozen(config)).toBe(true);
    expect(Object.isFrozen(config.auth)).toBe(true);
    expect(Object.isFrozen(config.auth.cookie)).toBe(true);
    expect(Object.isFrozen(config.auth.cookie.attributes)).toBe(true);
    expect(Object.isFrozen(config.auth.sessionCookie)).toBe(true);
    expect(Object.isFrozen(config.auth.transactionCookie)).toBe(true);
  });

  test.each(Object.keys(validEnvironment))("requires %s", (key) => {
    const environment = { ...validEnvironment } as Record<string, string>;
    delete environment[key];

    try {
      parseConfig(environment);
      throw new Error("Expected configuration parsing to fail");
    } catch (error) {
      expect(error).toBeInstanceOf(ZodError);
      expect((error as ZodError).issues.some((issue) => issue.path[0] === key)).toBe(true);
    }
  });

  test.each([
    ["TASKMIGO_CLIENT_URL", "not-a-url"],
    ["TASKMIGO_AUTH_ISSUER", "not-a-url"],
    ["TASKMIGO_AUTH_ALLOW_INSECURE_REQUESTS", "maybe"],
    ["TASKMIGO_AUTH_COOKIE_HTTP_ONLY", "maybe"],
    ["TASKMIGO_AUTH_COOKIE_SECURE", "maybe"],
    ["TASKMIGO_AUTH_COOKIE_SAME_SITE", "invalid"],
    ["TASKMIGO_AUTH_REFRESH_SKEW_MILLISECONDS", "-1"],
    ["TASKMIGO_AUTH_REFRESH_SKEW_MILLISECONDS", "1.5"],
    ["TASKMIGO_AUTH_SESSION_MAX_AGE_SECONDS", "0"],
    ["TASKMIGO_AUTH_TRANSACTION_MAX_AGE_SECONDS", "-1"],
  ])("rejects invalid %s=%s", (key, value) => {
    expect(() => parseConfig({ ...validEnvironment, [key]: value })).toThrow(ZodError);
  });

  test("reads the current process environment without a stale module cache", () => {
    const original = process.env;
    process.env = { ...original, ...validEnvironment };

    try {
      expect(getConfig()).toEqual(parseConfig(validEnvironment));
      process.env.TASKMIGO_AUTH_CLIENT_ID = "changed-client";
      expect(getConfig().auth.clientId).toBe("changed-client");
    } finally {
      process.env = original;
    }
  });
});
