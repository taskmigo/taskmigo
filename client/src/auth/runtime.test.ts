import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

const calls = vi.hoisted(() => ({
  config: vi.fn(),
  authorizationClient: vi.fn(),
  navigation: vi.fn(),
  manager: vi.fn(),
  nextAuth: vi.fn(),
}));

vi.mock("server-only", () => ({}));
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
vi.mock("@taskmigo/auth/next", () => ({
  NextAuth: class {
    constructor(manager: unknown, options: unknown) {
      calls.nextAuth(manager, options);
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

type AuthGlobal = typeof globalThis & { taskmigoAuthRuntime?: unknown };

beforeEach(() => {
  calls.config.mockReturnValue(config);
  for (const mock of Object.values(calls)) mock.mockClear();
  delete (globalThis as AuthGlobal).taskmigoAuthRuntime;
});

afterEach(() => {
  delete (globalThis as AuthGlobal).taskmigoAuthRuntime;
});

describe("AuthRuntime", () => {
  test("maps typed environment configuration into the auth object graph", async () => {
    const { AuthRuntime } = await import("./runtime");
    const runtime = new AuthRuntime(config);

    expect(runtime.auth).toBeDefined();
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
    expect(calls.nextAuth).toHaveBeenCalledWith(expect.anything(), {
      returnToParameter: "next",
      sessionCookie: {
        name: "session",
        secret: config.auth.sessionSecret,
        version: "cookie-v3",
        additionalAuthenticatedData: "session-aad",
        attributes: { httpOnly: true, sameSite: "strict", secure: true, path: "/auth", maxAge: 7200 },
      },
      transactionCookie: {
        name: "transaction",
        secret: config.auth.sessionSecret,
        version: "cookie-v3",
        additionalAuthenticatedData: "transaction-aad",
        attributes: { httpOnly: true, sameSite: "strict", secure: true, path: "/auth", maxAge: 300 },
      },
    });
  });

  test("stores the runtime on globalThis so module reloads reuse the same singleton", async () => {
    const firstModule = await import("./runtime");
    const first = firstModule.AuthRuntime.get();

    vi.resetModules();
    const secondModule = await import("./runtime");
    const second = secondModule.AuthRuntime.get();

    expect(second).toBe(first);
    expect(calls.config).toHaveBeenCalledTimes(1);
    expect(calls.nextAuth).toHaveBeenCalledTimes(1);
  });
});
