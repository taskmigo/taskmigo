import { beforeEach, describe, expect, test, vi } from "vitest";
import { NextRequest, NextResponse } from "next/server";

import {
  type BrowserApiAuthorization,
  type BrowserApiAuthorizer,
  type BrowserApiBackend,
  BrowserApiProxy,
  type BrowserApiRequestPolicy,
} from "./proxy";

const policy: BrowserApiRequestPolicy = {
  allows: vi.fn(),
  resolveBackendUrl: vi.fn(),
};
const authorization: BrowserApiAuthorization = {
  accessToken: "access-token",
  persist: vi.fn(),
};
const authorizer: BrowserApiAuthorizer = {
  authorize: vi.fn(),
  clear: vi.fn(),
};
const backend: BrowserApiBackend = {
  forward: vi.fn(),
};

function request(): NextRequest {
  return new NextRequest("https://app.example/api/bff/v0/users");
}

function context() {
  return { params: Promise.resolve({ path: ["v0", "users"] }) };
}

function createProxy(): BrowserApiProxy {
  return new BrowserApiProxy(policy, authorizer, backend);
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(policy.allows).mockReturnValue(true);
  vi.mocked(policy.resolveBackendUrl).mockReturnValue(new URL("http://backend/api/v0/users"));
  vi.mocked(authorizer.authorize).mockResolvedValue(authorization);
  vi.mocked(backend.forward).mockResolvedValue(new NextResponse(undefined, { status: 204 }));
});

describe("BrowserApiProxy", () => {
  test("orchestrates an authorized backend request", async () => {
    const response = await createProxy().handle(request(), context());

    expect(response.status).toBe(204);
    expect(authorizer.authorize).toHaveBeenCalledOnce();
    expect(backend.forward).toHaveBeenCalledWith(
      expect.any(NextRequest),
      new URL("http://backend/api/v0/users"),
      "access-token",
    );
    expect(authorization.persist).toHaveBeenCalledWith(response);
  });

  test("rejects disallowed browser requests before authorization", async () => {
    vi.mocked(policy.allows).mockReturnValue(false);

    const response = await createProxy().handle(request(), context());

    expect(response.status).toBe(403);
    expect(authorizer.authorize).not.toHaveBeenCalled();
    expect(backend.forward).not.toHaveBeenCalled();
  });

  test("rejects non-versioned backend routes before authorization", async () => {
    vi.mocked(policy.resolveBackendUrl).mockReset();

    const response = await createProxy().handle(request(), context());

    expect(response.status).toBe(404);
    expect(authorizer.authorize).not.toHaveBeenCalled();
  });

  test("clears authentication when the browser session cannot be authorized", async () => {
    vi.mocked(authorizer.authorize).mockReset();

    const response = await createProxy().handle(request(), context());

    expect(response.status).toBe(401);
    expect(authorizer.clear).toHaveBeenCalledWith(response);
    expect(backend.forward).not.toHaveBeenCalled();
  });

  test("returns bad gateway when the backend cannot be reached", async () => {
    vi.mocked(backend.forward).mockReset();

    const response = await createProxy().handle(request(), context());

    expect(response.status).toBe(502);
    expect(authorization.persist).not.toHaveBeenCalled();
  });
});
