import { describe, expect, test } from "vitest";
import { NextRequest } from "next/server";

import { SameOriginBrowserApiRequestPolicy } from "./request-policy";

const policy = new SameOriginBrowserApiRequestPolicy(new URL("https://app.example"), new URL("http://backend:8080"));

describe("SameOriginBrowserApiRequestPolicy", () => {
  test("allows safe methods without an Origin header", () => {
    expect(policy.allows(new NextRequest("https://app.example/api/bff/v0/users"))).toBe(true);
  });

  test("requires same-origin mutation requests", () => {
    const sameOrigin = new NextRequest("https://app.example/api/bff/v0/users", {
      method: "POST",
      headers: { Origin: "https://app.example" },
    });
    const crossOrigin = new NextRequest("https://app.example/api/bff/v0/users", {
      method: "POST",
      headers: { Origin: "https://attacker.example" },
    });

    expect(policy.allows(sameOrigin)).toBe(true);
    expect(policy.allows(crossOrigin)).toBe(false);
  });

  test("resolves only versioned API paths against the fixed backend", () => {
    const request = new NextRequest("https://app.example/api/bff/v0/users?page=2");

    expect(policy.resolveBackendUrl(request, ["v0", "users"])).toEqual(
      new URL("http://backend:8080/api/v0/users?page=2"),
    );
    expect(policy.resolveBackendUrl(request, ["oauth2", "token"])).toBeUndefined();
    expect(policy.resolveBackendUrl(request, ["v0", "..", "token"])).toBeUndefined();
  });
});
