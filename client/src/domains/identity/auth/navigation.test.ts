import { describe, expect, test } from "vitest";

import { AuthNavigation } from "./navigation";

const appUrl = new URL("https://app.example/base/");

function navigation(options: Partial<ConstructorParameters<typeof AuthNavigation>[0]> = {}) {
  return new AuthNavigation({
    appUrl,
    callbackUrl: new URL("/oauth/callback", appUrl),
    postLogoutRedirectUrl: new URL("/signed-out", appUrl),
    defaultReturnTo: "/account",
    ...options,
  });
}

describe("AuthNavigation", () => {
  test("canonicalizes same-origin local targets and preserves query/hash", () => {
    const target = navigation();

    expect(target.resolveReturnTo("/projects?mine=true#active")).toBe("/projects?mine=true#active");
    expect(target.returnToUrl("/projects?mine=true#active").href).toBe("https://app.example/projects?mine=true#active");
  });

  test.each([undefined, "", "projects", "https://attacker.example/path", "//attacker.example/path"])(
    "falls back for unsafe return target %s",
    (candidate) => {
      expect(navigation().resolveReturnTo(candidate)).toBe("/account");
    },
  );

  test("falls back for a null return target", () => {
    const candidate = JSON.parse("null") as Parameters<AuthNavigation["resolveReturnTo"]>[0];
    expect(navigation().resolveReturnTo(candidate)).toBe("/account");
  });

  test("rejects backslash and malformed authority redirects", () => {
    const target = navigation();
    expect(target.resolveReturnTo(String.raw`/\\attacker.example/path`)).toBe("/account");
    expect(target.resolveReturnTo("//[invalid")).toBe("/account");
  });

  test.each([
    ["callback", { callbackUrl: new URL("https://attacker.example/callback") }, "Callback URL"],
    ["logout", { postLogoutRedirectUrl: new URL("https://attacker.example/logout") }, "Post-logout redirect URL"],
    ["default", { defaultReturnTo: "https://attacker.example" }, "Default return target"],
  ] as const)("rejects cross-origin %s configuration", (_name, options, message) => {
    expect(() => navigation(options)).toThrow(`${message} must resolve to the application origin`);
  });

  test("returns defensive URL copies", () => {
    const target = navigation();
    const callback = target.callbackUrl;
    const logout = target.postLogoutRedirectUrl;
    callback.pathname = "/tampered";
    logout.pathname = "/tampered";

    expect(target.callbackUrl.pathname).toBe("/oauth/callback");
    expect(target.postLogoutRedirectUrl.pathname).toBe("/signed-out");
  });
});
