import { beforeEach, describe, expect, test, vi } from "vitest";
import { NextRequest, NextResponse } from "next/server";

import { SessionBrowserApiAuthorizer } from "./session-authorizer";

const session = {
  user: { id: "developer", name: "Developer" },
  expiresAt: 123_456,
  authorizationState: "opaque",
};
const manager = { authorize: vi.fn() };
const sessions = { read: vi.fn(), write: vi.fn(), clear: vi.fn() };

function request(): NextRequest {
  return new NextRequest("https://app.example/api/bff/v0/users");
}

beforeEach(() => {
  vi.resetAllMocks();
});

describe("SessionBrowserApiAuthorizer", () => {
  test("returns no authorization when the browser session is missing", async () => {
    const authorizer = new SessionBrowserApiAuthorizer(manager, sessions);

    await expect(authorizer.authorize(request())).resolves.toBeUndefined();
    expect(manager.authorize).not.toHaveBeenCalled();
  });

  test("returns no authorization when renewal fails", async () => {
    sessions.read.mockReturnValue(session);
    manager.authorize.mockRejectedValueOnce(new Error("refresh failed"));
    const authorizer = new SessionBrowserApiAuthorizer(manager, sessions);

    await expect(authorizer.authorize(request())).resolves.toBeUndefined();
  });

  test("persists a renewed session through the authorization result", async () => {
    const renewed = { ...session, authorizationState: "renewed" };
    sessions.read.mockReturnValue(session);
    manager.authorize.mockResolvedValue({ session: renewed, accessToken: "access-token" });
    const authorizer = new SessionBrowserApiAuthorizer(manager, sessions);
    const authorization = await authorizer.authorize(request());
    const response = new NextResponse();

    authorization?.persist(response);

    expect(authorization?.accessToken).toBe("access-token");
    expect(sessions.write).toHaveBeenCalledWith(response.cookies, renewed);
  });

  test("clears the browser session", () => {
    const authorizer = new SessionBrowserApiAuthorizer(manager, sessions);
    const response = new NextResponse();

    authorizer.clear(response);

    expect(sessions.clear).toHaveBeenCalledWith(response.cookies);
  });
});
