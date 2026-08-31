import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import type { Session } from "@taskmigo/auth";

const calls = vi.hoisted(() => ({
  config: vi.fn(),
  authorizationClient: vi.fn(),
  navigation: vi.fn(),
  manager: vi.fn(),
  headerCookies: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("next/headers", () => ({ cookies: calls.headerCookies }));
vi.mock("@taskmigo/config/server", () => ({ getConfig: calls.config }));
vi.mock("@taskmigo/auth/openid-client", () => ({
  OpenIdAuthorizationClient: class {
    constructor(options: unknown) {
      calls.authorizationClient(options);
    }
  },
}));
vi.mock("@taskmigo/auth", () => ({
  AuthNavigation: class {
    constructor(options: unknown) {
      calls.navigation(options);
    }
  },
  DefaultAuthManager: class {
    constructor(client: unknown, navigation: unknown, options: unknown) {
      calls.manager(client, navigation, options);
    }
  },
}));

const config = {
  appUrl: new URL("https://app.example"),
  auth: {
    issuer: new URL("https://auth.example"),
    clientId: "client",
    clientSecret: "client-secret",
    sessionSecret: "01234567890123456789012345678901",
    allowInsecureRequests: true,
    callbackPath: "/callback",
    postLogoutRedirectPath: "/signed-out",
    defaultReturnTo: "/account",
    returnToParameter: "next",
    scope: "openid profile api",
    oidcStateVersion: "oidc-v2",
    refreshSkewMilliseconds: 12_345,
    sessionCookie: { name: "session", additionalAuthenticatedData: "session-aad", maxAge: 7200 },
    transactionCookie: { name: "transaction", additionalAuthenticatedData: "transaction-aad", maxAge: 300 },
    cookie: {
      version: "cookie-v3",
      attributes: { httpOnly: true, sameSite: "strict" as const, secure: true, path: "/auth" },
    },
  },
};

const session: Session = {
  user: { id: "developer", name: "Developer" },
  expiresAt: 123_456,
  authorizationState: "opaque",
};

const runtimeKey = Symbol.for("taskmigo.auth.runtime");

beforeEach(() => {
  calls.config.mockReturnValue(config);
  for (const mock of Object.values(calls)) mock.mockClear();
  Reflect.deleteProperty(globalThis, runtimeKey);
});

afterEach(() => {
  Reflect.deleteProperty(globalThis, runtimeKey);
});

describe("auth runtime", () => {
  test("declaratively maps typed configuration into the auth context", async () => {
    const { createAuth } = await import("./runtime");
    const auth = createAuth(config);

    expect(auth.returnToParameter).toBe("next");
    expect(auth.sessions.name).toBe("session");
    expect(auth.sessions.attributes).toEqual({
      httpOnly: true,
      sameSite: "strict",
      secure: true,
      path: "/auth",
      maxAge: 7200,
    });
    expect(auth.transactions.name).toBe("transaction");
    expect(auth.transactions.attributes.maxAge).toBe(300);
    expect(Object.isFrozen(auth)).toBe(true);
    expect(calls.authorizationClient).toHaveBeenCalledWith({
      issuer: config.auth.issuer,
      clientId: "client",
      clientSecret: "client-secret",
      scope: "openid profile api",
      stateVersion: "oidc-v2",
      allowInsecureRequests: true,
    });
    expect(calls.navigation).toHaveBeenCalledWith({
      appUrl: config.appUrl,
      callbackUrl: new URL("https://app.example/callback"),
      postLogoutRedirectUrl: new URL("https://app.example/signed-out"),
      defaultReturnTo: "/account",
    });
    expect(calls.manager).toHaveBeenCalledWith(expect.anything(), expect.anything(), {
      refreshSkewMilliseconds: 12_345,
    });
  });

  test("reuses the same auth context across module reloads", async () => {
    const firstModule = await import("./runtime");
    const first = firstModule.getAuth();

    vi.resetModules();
    const secondModule = await import("./runtime");
    const second = secondModule.getAuth();

    expect(second).toBe(first);
    expect(calls.config).toHaveBeenCalledTimes(1);
    expect(calls.manager).toHaveBeenCalledTimes(1);
  });

  test("reads valid server-component sessions and rejects invalid typed payloads", async () => {
    const runtimeModule = await import("./runtime");
    const auth = runtimeModule.getAuth();
    let encoded = "";
    const writer = {
      set: (_name: string, value: string) => {
        encoded = value;
      },
      delete: vi.fn(),
    };

    auth.sessions.write(writer, session);
    calls.headerCookies.mockResolvedValueOnce({ get: () => ({ value: encoded }) });
    await expect(runtimeModule.getSession()).resolves.toEqual(session);

    const unnamedSession: Session = {
      user: { id: "developer" },
      expiresAt: 123_456,
      authorizationState: "opaque",
    };
    auth.sessions.write(writer, unnamedSession);
    calls.headerCookies.mockResolvedValueOnce({ get: () => ({ value: encoded }) });
    const decoded = await runtimeModule.getSession();
    expect(decoded).toEqual(unnamedSession);
    expect(decoded && "name" in decoded.user).toBe(false);

    auth.sessions.write(writer, { ...session, user: { id: "" } } as Session);
    calls.headerCookies.mockResolvedValueOnce({ get: () => ({ value: encoded }) });
    await expect(runtimeModule.getSession()).resolves.toBeUndefined();
  });
});
