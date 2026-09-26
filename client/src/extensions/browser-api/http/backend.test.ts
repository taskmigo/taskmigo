import { beforeEach, describe, expect, test, vi } from "vitest";
import { NextRequest } from "next/server";

import { FetchBrowserApiBackend } from "./backend";

const fetchBackend = vi.fn();

beforeEach(() => {
  vi.resetAllMocks();
});

describe("FetchBrowserApiBackend", () => {
  test("replaces browser credentials and sanitizes the backend response", async () => {
    fetchBackend.mockImplementationOnce(async (upstream: Request, init: RequestInit) => {
      expect(upstream.headers.get("authorization")).toBe("Bearer access-token");
      expect(upstream.headers.get("cookie")).toBeNull();
      expect(upstream.headers.get("x-forwarded-host")).toBeNull();
      expect(upstream.headers.get("x-request-id")).toBe("request-id");
      expect(upstream.headers.get("accept-encoding")).toBe("identity");
      expect(init).toEqual({ cache: "no-store", redirect: "manual" });
      return new Response('{"data":[]}', {
        status: 200,
        headers: { "Content-Type": "application/json", "Set-Cookie": "backend=secret" },
      });
    });
    const backend = new FetchBrowserApiBackend(fetchBackend);
    const request = new NextRequest("https://app.example/api/bff/v0/users", {
      headers: {
        Authorization: "Bearer attacker-controlled",
        Cookie: "taskmigo_session=opaque",
        "X-Forwarded-Host": "attacker.example",
        "X-Request-Id": "request-id",
      },
    });

    const response = await backend.forward(request, new URL("http://backend/api/v0/users"), "access-token");

    expect(response?.status).toBe(200);
    expect(response?.headers.get("set-cookie")).toBeNull();
    expect(response?.headers.get("cache-control")).toBe("private, no-store");
  });

  test("forwards request bodies without buffering them", async () => {
    fetchBackend.mockImplementationOnce(async (upstream: Request) => {
      expect(upstream.method).toBe("POST");
      await expect(upstream.text()).resolves.toBe('{"name":"Developer"}');
      return new Response(undefined, { status: 201 });
    });
    const backend = new FetchBrowserApiBackend(fetchBackend);
    const request = new NextRequest("https://app.example/api/bff/v0/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: '{"name":"Developer"}',
    });

    const response = await backend.forward(request, new URL("http://backend/api/v0/users"), "access-token");

    expect(response?.status).toBe(201);
  });

  test("returns no response when the backend is unreachable", async () => {
    fetchBackend.mockRejectedValueOnce(new Error("network unavailable"));
    const backend = new FetchBrowserApiBackend(fetchBackend);

    await expect(
      backend.forward(
        new NextRequest("https://app.example/api/bff/v0/users"),
        new URL("http://backend/api/v0/users"),
        "access-token",
      ),
    ).resolves.toBeUndefined();
  });
});
