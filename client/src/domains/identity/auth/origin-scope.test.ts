import { describe, expect, test } from "vitest";

import { OriginScope } from "./origin-scope";

describe("OriginScope", () => {
  const scope = new OriginScope(new URL("https://app.example/base/"));

  test("resolves only URLs inside its origin and returns defensive base copies", () => {
    expect(scope.resolve("/projects")?.href).toBe("https://app.example/projects");
    expect(scope.resolve(new URL("https://app.example/settings"))?.pathname).toBe("/settings");
    expect(scope.resolve("https://attacker.example/path")).toBeUndefined();
    expect(scope.resolve("http://[invalid")).toBeUndefined();

    const base = scope.baseUrl;
    base.pathname = "/tampered";
    expect(scope.baseUrl.pathname).toBe("/base/");
  });

  test("canonicalizes local paths while rejecting non-local and malformed targets", () => {
    expect(scope.path("/projects?mine=true#active")).toBe("/projects?mine=true#active");
    expect(scope.path("projects")).toBeUndefined();
    expect(scope.path("//attacker.example/path")).toBeUndefined();
    expect(scope.path(String.raw`/\\attacker.example/path`)).toBeUndefined();
    expect(scope.path("//[invalid")).toBeUndefined();
  });
});
