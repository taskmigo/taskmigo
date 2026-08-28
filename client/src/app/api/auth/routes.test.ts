import { beforeEach, describe, expect, test, vi } from "vitest";
import { NextRequest } from "next/server";

const auth = vi.hoisted(() => {
  const manager = {
    beginSignIn: vi.fn(),
    completeSignIn: vi.fn(),
    renew: vi.fn(),
    signOut: vi.fn(),
  };
  const sessions = {
    read: vi.fn(),
    write: vi.fn(),
    clear: vi.fn(),
  };
  const transactions = {
    read: vi.fn(),
    write: vi.fn(),
    clear: vi.fn(),
  };
  const context = { manager, sessions, transactions, returnToParameter: "next" };

  return { manager, sessions, transactions, context, getAuth: vi.fn(() => context) };
});

vi.mock("@/auth", () => ({ getAuth: auth.getAuth }));

import { GET as callback } from "./callback/route";
import { GET as login } from "./login/route";
import { POST as logout } from "./logout/route";
import { GET as sessionEndpoint } from "./session/route";

const session = {
  user: { id: "developer", name: "Developer" },
  expiresAt: 123_456,
  authorizationState: "opaque",
};
const transaction = { state: "state", returnTo: "/account" };

function request(path: string): NextRequest {
  return new NextRequest(new URL(path, "https://app.example"));
}

beforeEach(() => {
  for (const mock of [
    auth.getAuth,
    ...Object.values(auth.manager),
    ...Object.values(auth.sessions),
    ...Object.values(auth.transactions),
  ]) {
    mock.mockReset();
  }

  auth.getAuth.mockReturnValue(auth.context);
  auth.manager.renew.mockImplementation(async (current) => current);
  auth.manager.signOut.mockResolvedValue(new URL("https://auth.example/logout"));
});

describe("login route", () => {
  test("redirects to authorization and persists the transaction", async () => {
    auth.manager.beginSignIn.mockResolvedValue({
      redirectTo: new URL("https://auth.example/authorize"),
      transaction,
    });

    const response = await login(request("/api/auth/login?next=/projects"));

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("https://auth.example/authorize");
    expect(auth.manager.beginSignIn).toHaveBeenCalledWith("/projects");
    expect(auth.transactions.write).toHaveBeenCalledWith(response.cookies, transaction);
  });

  test("returns the stable configuration error when authorization cannot start", async () => {
    auth.manager.beginSignIn.mockRejectedValue(new Error("misconfigured"));

    const response = await login(request("/api/auth/login"));

    expect(response.status).toBe(500);
    await expect(response.json()).resolves.toEqual({
      error: "Browser authentication is not configured correctly",
    });
  });
});

describe("callback route", () => {
  test("rejects missing or expired transactions and clears their cookie", async () => {
    const response = await callback(request("/api/auth/callback?code=code"));

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({
      error: "Login transaction is missing or expired",
    });
    expect(auth.transactions.clear).toHaveBeenCalledWith(response.cookies);
    expect(auth.manager.completeSignIn).not.toHaveBeenCalled();
  });

  test("completes sign-in, persists the session, and consumes the transaction", async () => {
    auth.transactions.read.mockReturnValue(transaction);
    auth.manager.completeSignIn.mockResolvedValue({
      redirectTo: new URL("https://app.example/account"),
      session,
    });
    const requestValue = request("/api/auth/callback?code=code&state=state");

    const response = await callback(requestValue);

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("https://app.example/account");
    expect(auth.manager.completeSignIn).toHaveBeenCalledWith(new URL(requestValue.url), transaction);
    expect(auth.sessions.write).toHaveBeenCalledWith(response.cookies, session);
    expect(auth.transactions.clear).toHaveBeenCalledWith(response.cookies);
  });

  test("returns the stable validation error and consumes a failed transaction", async () => {
    auth.transactions.read.mockReturnValue(transaction);
    auth.manager.completeSignIn.mockRejectedValue(new Error("invalid callback"));

    const response = await callback(request("/api/auth/callback?code=code"));

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({
      error: "Login callback could not be validated",
    });
    expect(auth.transactions.clear).toHaveBeenCalledWith(response.cookies);
    expect(auth.sessions.write).not.toHaveBeenCalled();
  });
});

describe("logout route", () => {
  test.each([undefined, session])("signs out with session %s and clears all auth state", async (current) => {
    auth.sessions.read.mockReturnValue(current);

    const response = await logout(request("/api/auth/logout"));

    expect(response.status).toBe(303);
    expect(response.headers.get("location")).toBe("https://auth.example/logout");
    expect(auth.manager.signOut).toHaveBeenCalledWith(current);
    expect(auth.sessions.clear).toHaveBeenCalledWith(response.cookies);
    expect(auth.transactions.clear).toHaveBeenCalledWith(response.cookies);
  });
});

describe("session route", () => {
  test("returns an uncached unauthenticated response when no session exists", async () => {
    const response = await sessionEndpoint(request("/api/auth/session"));

    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toEqual({ authenticated: false });
    expect(auth.manager.renew).not.toHaveBeenCalled();
  });

  test("returns an unchanged session without rewriting its cookie", async () => {
    auth.sessions.read.mockReturnValue(session);

    const response = await sessionEndpoint(request("/api/auth/session"));

    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toEqual({ authenticated: true, user: session.user });
    expect(auth.sessions.write).not.toHaveBeenCalled();
  });

  test("persists and returns a renewed session", async () => {
    const renewed = { ...session, expiresAt: 234_567, authorizationState: "renewed" };
    auth.sessions.read.mockReturnValue(session);
    auth.manager.renew.mockResolvedValue(renewed);

    const response = await sessionEndpoint(request("/api/auth/session"));

    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toEqual({ authenticated: true, user: renewed.user });
    expect(auth.sessions.write).toHaveBeenCalledWith(response.cookies, renewed);
  });

  test("clears a session that cannot be renewed", async () => {
    auth.sessions.read.mockReturnValue(session);
    auth.manager.renew.mockRejectedValue(new Error("refresh failed"));

    const response = await sessionEndpoint(request("/api/auth/session"));

    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toEqual({ authenticated: false });
    expect(auth.sessions.clear).toHaveBeenCalledWith(response.cookies);
  });
});
