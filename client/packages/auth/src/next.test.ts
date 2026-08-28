import { beforeEach, describe, expect, test, vi } from "vitest";
import { NextRequest } from "next/server";

import type { AuthManager, Session } from "./core";

vi.mock("server-only", () => ({}));

const headerCookies = vi.hoisted(() => vi.fn());
vi.mock("next/headers", () => ({ cookies: headerCookies }));

const session: Session = {
  user: { id: "developer", name: "Developer" },
  expiresAt: Date.now() + 60_000,
  authorizationState: "opaque",
};

async function keepSession(current: Session) {
  return current;
}

function manager(): AuthManager & {
  beginSignIn: ReturnType<typeof vi.fn>;
  completeSignIn: ReturnType<typeof vi.fn>;
  renew: ReturnType<typeof vi.fn>;
  signOut: ReturnType<typeof vi.fn>;
} {
  return {
    beginSignIn: vi.fn().mockResolvedValue({
      redirectTo: new URL("https://auth.example/authorize"),
      transaction: { state: "state", returnTo: "/account" },
    }),
    completeSignIn: vi.fn().mockResolvedValue({
      redirectTo: new URL("https://app.example/account"),
      session,
    }),
    renew: vi.fn(keepSession),
    signOut: vi.fn().mockResolvedValue(new URL("https://auth.example/logout")),
  };
}

const options = {
  returnToParameter: "next",
  sessionCookie: {
    name: "session",
    secret: "01234567890123456789012345678901",
    version: "v1",
    additionalAuthenticatedData: "session-aad",
    attributes: { httpOnly: true, sameSite: "lax" as const, secure: true, path: "/", maxAge: 3600 },
  },
  transactionCookie: {
    name: "transaction",
    secret: "01234567890123456789012345678901",
    version: "v1",
    additionalAuthenticatedData: "transaction-aad",
    attributes: { httpOnly: true, sameSite: "lax" as const, secure: true, path: "/", maxAge: 300 },
  },
};

function request(path: string, cookie?: string) {
  return new NextRequest(new URL(path, "https://app.example"), {
    headers: cookie ? { cookie } : undefined,
  });
}

async function createAuth() {
  const { NextAuth } = await import("./next");
  const authManager = manager();
  return { auth: new NextAuth(authManager, options), authManager };
}

async function transactionCookie(auth: Awaited<ReturnType<typeof createAuth>>["auth"]) {
  const response = await auth.login(request("/api/auth/login?next=/account"));
  const value = response.cookies.get("transaction")?.value;
  if (!value) throw new Error("Expected transaction cookie");
  return `transaction=${value}`;
}

beforeEach(() => {
  headerCookies.mockReset();
});

describe("NextAuth", () => {
  test("redirects login and persists the authorization transaction", async () => {
    const { auth, authManager } = await createAuth();
    const response = await auth.login(request("/api/auth/login?next=/projects"));

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("https://auth.example/authorize");
    expect(response.cookies.get("transaction")?.value).toBeTruthy();
    expect(authManager.beginSignIn).toHaveBeenCalledWith("/projects");
  });

  test("returns a stable login error when the manager fails", async () => {
    const { auth, authManager } = await createAuth();
    authManager.beginSignIn.mockRejectedValueOnce(new Error("misconfigured"));

    const response = await auth.login(request("/api/auth/login"));
    expect(response.status).toBe(500);
    await expect(response.json()).resolves.toEqual({ error: "Browser authentication is not configured correctly" });
  });

  test("rejects missing or invalid callback transactions and clears their cookie", async () => {
    const { auth } = await createAuth();

    for (const callback of [
      request("/api/auth/callback?code=code"),
      request("/api/auth/callback?code=code", "transaction=invalid"),
    ]) {
      const response = await auth.callback(callback);
      expect(response.status).toBe(400);
      await expect(response.json()).resolves.toEqual({ error: "Login transaction is missing or expired" });
      expect(response.cookies.get("transaction")?.value).toBe("");
    }
  });

  test("completes callback, persists session, and clears the transaction", async () => {
    const { auth, authManager } = await createAuth();
    const cookie = await transactionCookie(auth);
    const callback = request("/api/auth/callback?code=code&state=state", cookie);

    const response = await auth.callback(callback);
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("https://app.example/account");
    expect(response.cookies.get("session")?.value).toBeTruthy();
    expect(response.cookies.get("transaction")?.value).toBe("");
    expect(authManager.completeSignIn).toHaveBeenCalledWith(
      new URL("https://app.example/api/auth/callback?code=code&state=state"),
      { state: "state", returnTo: "/account" },
    );
  });

  test("clears the transaction and returns a stable callback error when validation fails", async () => {
    const { auth, authManager } = await createAuth();
    const cookie = await transactionCookie(auth);
    authManager.completeSignIn.mockRejectedValueOnce(new Error("invalid callback"));

    const response = await auth.callback(request("/api/auth/callback?code=code", cookie));
    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({ error: "Login callback could not be validated" });
    expect(response.cookies.get("transaction")?.value).toBe("");
  });

  test("logs out with and without a persisted session and clears all auth cookies", async () => {
    const { auth, authManager } = await createAuth();

    const withoutSession = await auth.logout(request("/api/auth/logout"));
    expect(withoutSession.status).toBe(303);
    expect(authManager.signOut.mock.calls[0]).toHaveLength(1);
    expect(authManager.signOut.mock.calls[0]?.[0]).toBeUndefined();

    const cookie = await transactionCookie(auth);
    const callback = await auth.callback(request("/api/auth/callback?code=code", cookie));
    const sessionValue = callback.cookies.get("session")?.value;
    if (!sessionValue) throw new Error("Expected session cookie");

    const withSession = await auth.logout(request("/api/auth/logout", `session=${sessionValue}`));
    expect(withSession.status).toBe(303);
    expect(authManager.signOut).toHaveBeenLastCalledWith(session);
    expect(withSession.cookies.get("session")?.value).toBe("");
    expect(withSession.cookies.get("transaction")?.value).toBe("");
  });

  test("returns unauthenticated session responses without cache", async () => {
    const { auth } = await createAuth();
    const response = await auth.session(request("/api/auth/session"));

    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toEqual({ authenticated: false });
  });

  test("returns and persists a renewed session", async () => {
    const { auth, authManager } = await createAuth();
    const transaction = await transactionCookie(auth);
    const callback = await auth.callback(request("/api/auth/callback?code=code", transaction));
    const value = callback.cookies.get("session")?.value;
    if (!value) throw new Error("Expected session cookie");

    const renewed = { ...session, expiresAt: session.expiresAt + 60_000, authorizationState: "renewed" };
    authManager.renew.mockResolvedValueOnce(renewed);
    const response = await auth.session(request("/api/auth/session", `session=${value}`));

    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toEqual({ authenticated: true, user: renewed.user });
    expect(response.cookies.get("session")?.value).toBeTruthy();
  });

  test("does not rewrite an unchanged session", async () => {
    const { auth } = await createAuth();
    const transaction = await transactionCookie(auth);
    const callback = await auth.callback(request("/api/auth/callback?code=code", transaction));
    const value = callback.cookies.get("session")?.value;
    if (!value) throw new Error("Expected session cookie");

    const response = await auth.session(request("/api/auth/session", `session=${value}`));
    expect(response.cookies.get("session")).toBeUndefined();
  });

  test("clears invalid sessions when renewal fails", async () => {
    const { auth, authManager } = await createAuth();
    const transaction = await transactionCookie(auth);
    const callback = await auth.callback(request("/api/auth/callback?code=code", transaction));
    const value = callback.cookies.get("session")?.value;
    if (!value) throw new Error("Expected session cookie");
    authManager.renew.mockRejectedValueOnce(new Error("refresh failed"));

    const response = await auth.session(request("/api/auth/session", `session=${value}`));
    await expect(response.json()).resolves.toEqual({ authenticated: false });
    expect(response.cookies.get("session")?.value).toBe("");
  });

  test("reads server-component sessions without renewing them", async () => {
    const { auth, authManager } = await createAuth();
    const transaction = await transactionCookie(auth);
    const callback = await auth.callback(request("/api/auth/callback?code=code", transaction));
    const value = callback.cookies.get("session")?.value;
    if (!value) throw new Error("Expected session cookie");

    headerCookies.mockResolvedValueOnce({ get: vi.fn(() => ({ value })) });
    await expect(auth.getSession()).resolves.toEqual(session);
    expect(authManager.renew).not.toHaveBeenCalled();

    headerCookies.mockResolvedValueOnce({ get: vi.fn() });
    await expect(auth.getSession()).resolves.toBeUndefined();
  });
});
