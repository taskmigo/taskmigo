import { beforeEach, describe, expect, test, vi } from "vitest";
import { NextRequest } from "next/server";

const auth = vi.hoisted(() => {
  const manager = { getAccessToken: vi.fn() };
  const sessions = { read: vi.fn(), write: vi.fn(), clear: vi.fn() };
  return { manager, sessions, getAuth: vi.fn(() => ({ manager, sessions })) };
});

const configuration = vi.hoisted(() => ({
  appUrl: new URL("https://app.example"),
  backend: {
    url: new URL("http://taskmigo-web:8080"),
    timeoutMilliseconds: 30_000,
  },
}));

const upstreamFetch = vi.hoisted(() => vi.fn());

vi.mock("@/auth", () => ({ getAuth: auth.getAuth }));
vi.mock("@taskmigo/config/server", () => ({ getConfig: () => configuration }));
vi.stubGlobal("fetch", upstreamFetch);

import { GET, OPTIONS, POST } from "./route";

const session = {
  user: { id: "developer" },
  expiresAt: 123_456,
  authorizationState: "opaque",
};

function request(
  path: string,
  options: { method?: string; headers?: HeadersInit; body?: BodyInit } = {},
): NextRequest {
  return new NextRequest(new URL(path, "https://app.example"), options);
}

function context(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

beforeEach(() => {
  for (const mock of [auth.getAuth, auth.manager.getAccessToken, auth.sessions.read, auth.sessions.write, auth.sessions.clear, upstreamFetch]) {
    mock.mockReset();
  }
  configuration.appUrl = new URL("https://app.example");
  auth.getAuth.mockReturnValue({ manager: auth.manager, sessions: auth.sessions });
  auth.manager.getAccessToken.mockResolvedValue({ session, accessToken: "access-token" });
});

describe("backend BFF route", () => {
  test("rejects cross-site requests before reading authentication state", async () => {
    const response = await GET(
      request("/backend/v0/users", { headers: { "Sec-Fetch-Site": "cross-site" } }),
      context("users"),
    );

    expect(response.status).toBe(403);
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.headers.get("cross-origin-resource-policy")).toBe("same-origin");
    expect(auth.sessions.read).not.toHaveBeenCalled();
  });

  test("requires the configured origin for unsafe methods", async () => {
    const response = await POST(
      request("/backend/v0/users", {
        method: "POST",
        headers: { Origin: "https://attacker.example" },
        body: "{}",
      }),
      context("users"),
    );

    expect(response.status).toBe(403);
  });

  test("rejects unsafe path segments", async () => {
    const response = await GET(request("/backend/v0/users"), context(".."));

    expect(response.status).toBe(400);
    expect(auth.sessions.read).not.toHaveBeenCalled();
  });

  test("returns an uncached unauthorized response when the browser session is absent", async () => {
    const response = await GET(request("/backend/v0/users"), context("users"));

    expect(response.status).toBe(401);
    expect(response.headers.get("content-type")).toBe("application/problem+json");
    await expect(response.json()).resolves.toEqual({
      type: "about:blank",
      title: "Unauthorized",
      status: 401,
      detail: "Authentication is required",
    });
    expect(auth.manager.getAccessToken).not.toHaveBeenCalled();
  });

  test("clears a browser session whose access token cannot be obtained", async () => {
    auth.sessions.read.mockReturnValue(session);
    auth.manager.getAccessToken.mockRejectedValue(new Error("refresh failed"));

    const response = await GET(request("/backend/v0/users"), context("users"));

    expect(response.status).toBe(401);
    expect(auth.sessions.clear).toHaveBeenCalledWith(response.cookies);
    expect(upstreamFetch).not.toHaveBeenCalled();
  });

  test("acts as a reverse proxy with canonical forwarding headers and a server-owned bearer token", async () => {
    auth.sessions.read.mockReturnValue(session);
    upstreamFetch.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const upstream = input as Request;
      expect(upstream.url).toBe("http://taskmigo-web:8080/api/v0/users?include=groups");
      expect(upstream.method).toBe("POST");
      expect(upstream.headers.get("authorization")).toBe("Bearer access-token");
      expect(upstream.headers.get("accept")).toBe("application/json");
      expect(upstream.headers.get("content-type")).toBe("application/json");
      expect(upstream.headers.get("idempotency-key")).toBe("request-1");
      expect(upstream.headers.get("x-custom-end-to-end")).toBe("preserved");
      expect(upstream.headers.get("cookie")).toBeNull();
      expect(upstream.headers.get("x-forwarded-for")).toBeNull();
      expect(upstream.headers.get("forwarded")).toBe('by=_taskmigo-bff;host="app.example";proto=https');
      expect(upstream.headers.get("x-forwarded-host")).toBe("app.example");
      expect(upstream.headers.get("x-forwarded-port")).toBe("443");
      expect(upstream.headers.get("x-forwarded-prefix")).toBe("/backend/v0");
      expect(upstream.headers.get("x-forwarded-proto")).toBe("https");
      expect(upstream.headers.get("via")).toBe("1.1 taskmigo-bff");
      expect(upstream.headers.get("accept-encoding")).toBe("identity");
      expect(init?.redirect).toBe("manual");
      expect(await upstream.text()).toBe('{"name":"Developer"}');
      return new Response('{"ok":true}', { headers: { "Content-Type": "application/json" } });
    });

    const response = await POST(
      request("/backend/v0/users?include=groups", {
        method: "POST",
        headers: {
          Origin: "https://app.example",
          "Sec-Fetch-Site": "same-origin",
          Accept: "application/json",
          Authorization: "Bearer attacker-controlled",
          Cookie: "stolen=value",
          Forwarded: 'for=192.0.2.10;host="attacker.example";proto=http',
          Via: "1.0 attacker",
          "Content-Type": "application/json",
          "Idempotency-Key": "request-1",
          "X-Custom-End-To-End": "preserved",
          "X-Forwarded-For": "192.0.2.10",
          "X-Forwarded-Host": "attacker.example",
          "X-Forwarded-Port": "81",
          "X-Forwarded-Prefix": "/attacker",
          "X-Forwarded-Proto": "http",
        },
        body: '{"name":"Developer"}',
      }),
      context("users"),
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({ ok: true });
  });

  test("strips connection-specific request headers named by Connection", async () => {
    auth.sessions.read.mockReturnValue(session);
    upstreamFetch.mockImplementation(async (input: RequestInfo | URL) => {
      const upstream = input as Request;
      expect(upstream.headers.get("x-remove-me")).toBeNull();
      expect(upstream.headers.get("connection")).toBeNull();
      return new Response(null, { status: 204 });
    });

    const response = await POST(
      request("/backend/v0/users", {
        method: "POST",
        headers: {
          Origin: "https://app.example",
          Connection: "X-Remove-Me",
          "X-Remove-Me": "hop-by-hop",
        },
        body: "{}",
      }),
      context("users"),
    );

    expect(response.status).toBe(204);
  });

  test("uses the configured public port in forwarding metadata", async () => {
    configuration.appUrl = new URL("http://app.example:3000");
    auth.sessions.read.mockReturnValue(session);
    upstreamFetch.mockImplementation(async (input: RequestInfo | URL) => {
      const upstream = input as Request;
      expect(upstream.headers.get("forwarded")).toBe('by=_taskmigo-bff;host="app.example:3000";proto=http');
      expect(upstream.headers.get("x-forwarded-port")).toBe("3000");
      expect(upstream.headers.get("x-forwarded-proto")).toBe("http");
      return new Response(null, { status: 204 });
    });

    const response = await GET(request("/backend/v0/users"), context("users"));

    expect(response.status).toBe(204);
  });

  test("forwards OPTIONS instead of using an implicit route-handler response", async () => {
    auth.sessions.read.mockReturnValue(session);
    upstreamFetch.mockImplementation(async (input: RequestInfo | URL) => {
      expect((input as Request).method).toBe("OPTIONS");
      return new Response(null, { status: 204 });
    });

    const response = await OPTIONS(
      request("/backend/v0/users", {
        method: "OPTIONS",
        headers: { Origin: "https://app.example" },
      }),
      context("users"),
    );

    expect(response.status).toBe(204);
  });

  test("streams the upstream response, strips proxy-only headers, and rewrites API locations", async () => {
    auth.sessions.read.mockReturnValue(session);
    upstreamFetch.mockResolvedValue(
      new Response("created", {
        status: 201,
        headers: {
          "Access-Control-Allow-Origin": "*",
          Connection: "X-Remove-Me",
          "Content-Location": "http://taskmigo-web:8080/api/v0",
          Location: "/api/v0/users/42?view=full",
          "Set-Cookie": "backend=session",
          Via: "1.1 upstream-proxy",
          "X-Backend": "preserved",
          "X-Remove-Me": "hop-by-hop",
        },
      }),
    );

    const response = await GET(request("/backend/v0/users"), context("users"));

    expect(response.status).toBe(201);
    expect(response.headers.get("location")).toBe("/backend/v0/users/42?view=full");
    expect(response.headers.get("content-location")).toBe("/backend/v0");
    expect(response.headers.get("set-cookie")).toBeNull();
    expect(response.headers.get("access-control-allow-origin")).toBeNull();
    expect(response.headers.get("x-remove-me")).toBeNull();
    expect(response.headers.get("x-backend")).toBe("preserved");
    expect(response.headers.get("via")).toBe("1.1 upstream-proxy, 1.1 taskmigo-bff");
    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.text()).resolves.toBe("created");
  });

  test("adds its Via header when the upstream response has no intermediary metadata", async () => {
    auth.sessions.read.mockReturnValue(session);
    upstreamFetch.mockResolvedValue(new Response(null, { status: 204 }));

    const response = await GET(request("/backend/v0/users"), context("users"));

    expect(response.headers.get("via")).toBe("1.1 taskmigo-bff");
  });

  test("removes the internal origin from non-API backend locations and preserves external locations", async () => {
    auth.sessions.read.mockReturnValue(session);
    upstreamFetch
      .mockResolvedValueOnce(
        new Response(null, {
          status: 302,
          headers: { Location: "http://taskmigo-web:8080/login?continue=1" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(null, {
          status: 302,
          headers: { Location: "https://identity.example/login" },
        }),
      );

    const internal = await GET(request("/backend/v0/users"), context("users"));
    const external = await GET(request("/backend/v0/users"), context("users"));

    expect(internal.headers.get("location")).toBe("/login?continue=1");
    expect(external.headers.get("location")).toBe("https://identity.example/login");
  });

  test("preserves an invalid Location value instead of failing the response", async () => {
    auth.sessions.read.mockReturnValue(session);
    upstreamFetch.mockResolvedValue(
      new Response(null, {
        status: 201,
        headers: { Location: "http://[invalid" },
      }),
    );

    const response = await GET(request("/backend/v0/users"), context("users"));

    expect(response.headers.get("location")).toBe("http://[invalid");
  });

  test("persists a renewed session on successful and failed upstream requests", async () => {
    const renewed = { ...session, expiresAt: 234_567, authorizationState: "renewed" };
    auth.sessions.read.mockReturnValue(session);
    auth.manager.getAccessToken.mockResolvedValue({ session: renewed, accessToken: "renewed-token" });
    upstreamFetch
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockRejectedValueOnce(new Error("network unavailable"));

    const success = await GET(request("/backend/v0/users"), context("users"));
    const failure = await GET(request("/backend/v0/users"), context("users"));

    expect(success.status).toBe(204);
    expect(failure.status).toBe(502);
    expect(auth.sessions.write).toHaveBeenNthCalledWith(1, success.cookies, renewed);
    expect(auth.sessions.write).toHaveBeenNthCalledWith(2, failure.cookies, renewed);
    await expect(failure.json()).resolves.toEqual({
      type: "about:blank",
      title: "Bad Gateway",
      status: 502,
      detail: "Backend unavailable",
    });
  });

  test("returns gateway timeout when the upstream deadline expires", async () => {
    auth.sessions.read.mockReturnValue(session);
    const timeout = AbortSignal.abort(new DOMException("timed out", "TimeoutError"));
    const timeoutSpy = vi.spyOn(AbortSignal, "timeout").mockReturnValueOnce(timeout);
    upstreamFetch.mockRejectedValueOnce(new DOMException("timed out", "TimeoutError"));

    try {
      const response = await GET(request("/backend/v0/users"), context("users"));

      expect(response.status).toBe(504);
      await expect(response.json()).resolves.toEqual({
        type: "about:blank",
        title: "Gateway Timeout",
        status: 504,
        detail: "Backend timed out",
      });
    } finally {
      timeoutSpy.mockRestore();
    }
  });
});
