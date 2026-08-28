import { describe, expect, test, vi } from "vitest";
import { z } from "zod";

import { AuthNavigation, DefaultAuthManager, type AuthorizationClient, type Session } from "./core";
import { SealedCookie } from "./sealed-cookie";

const secret = "01234567890123456789012345678901";
const appUrl = new URL("https://app.example/base/");
const activeSession: Session = {
  user: { id: "developer", name: "Developer" },
  expiresAt: Date.now() + 60_000,
  authorizationState: "opaque",
};

async function beginAuthorization() {
  return { redirectTo: new URL("https://auth.example/authorize"), state: "transaction" };
}

async function completeAuthorization() {
  return activeSession;
}

async function renewAuthorization() {
  return activeSession;
}

async function endAuthorization() {
  return new URL("https://auth.example/logout");
}

class StubAuthorizationClient implements AuthorizationClient {
  readonly begin = vi.fn(beginAuthorization);
  readonly complete = vi.fn(completeAuthorization);
  readonly renew = vi.fn(renewAuthorization);
  readonly end = vi.fn(endAuthorization);
}

function navigation(options: Partial<ConstructorParameters<typeof AuthNavigation>[0]> = {}) {
  return new AuthNavigation({
    appUrl,
    callbackUrl: new URL("/oauth/callback", appUrl),
    postLogoutRedirectUrl: new URL("/signed-out", appUrl),
    defaultReturnTo: "/account",
    ...options,
  });
}

function manager(client = new StubAuthorizationClient(), refreshSkewMilliseconds = 30_000) {
  return { client, manager: new DefaultAuthManager(client, navigation(), { refreshSkewMilliseconds }) };
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

describe("DefaultAuthManager", () => {
  test("begins sign-in with the configured callback and normalized return target", async () => {
    const { client, manager: auth } = manager();

    await expect(auth.beginSignIn("/projects?mine=true")).resolves.toEqual({
      redirectTo: new URL("https://auth.example/authorize"),
      transaction: { state: "transaction", returnTo: "/projects?mine=true" },
    });
    expect(client.begin).toHaveBeenCalledWith(new URL("https://app.example/oauth/callback"));
  });

  test("completes sign-in without exposing authorization implementation details", async () => {
    const { client, manager: auth } = manager();
    const callback = new URL("https://app.example/oauth/callback?code=code&state=state");

    await expect(auth.completeSignIn(callback, { state: "opaque", returnTo: "/projects" })).resolves.toEqual({
      session: activeSession,
      redirectTo: new URL("https://app.example/projects"),
    });
    expect(client.complete).toHaveBeenCalledWith(callback, "opaque");
  });

  test("does not renew a session outside the refresh window", async () => {
    const { client, manager: auth } = manager();
    const session = { ...activeSession, expiresAt: Date.now() + 60_000 };

    await expect(auth.renew(session)).resolves.toBe(session);
    expect(client.renew).not.toHaveBeenCalled();
  });

  test("renews a session inside the refresh window", async () => {
    const { client, manager: auth } = manager();
    const current = { ...activeSession, expiresAt: Date.now() + 1000 };
    const renewed = { ...activeSession, expiresAt: Date.now() + 2 * 60_000, authorizationState: "renewed" };
    client.renew.mockResolvedValueOnce(renewed);

    await expect(auth.renew(current)).resolves.toBe(renewed);
    expect(client.renew).toHaveBeenCalledWith(current);
  });

  test("rejects subject changes during renewal with a stable error message", async () => {
    const { client, manager: auth } = manager();
    const current = { ...activeSession, expiresAt: Date.now() };
    client.renew.mockResolvedValueOnce({ ...activeSession, user: { id: "attacker" } });

    await expect(auth.renew(current)).rejects.toThrow("Authorization subject changed during renewal");
  });

  test("signs out locally when no session exists", async () => {
    const { client, manager: auth } = manager();

    await expect(auth.signOut()).resolves.toEqual(new URL("https://app.example/signed-out"));
    expect(client.end).not.toHaveBeenCalled();
  });

  test("delegates provider logout and falls back to local logout on provider failure", async () => {
    const { client, manager: auth } = manager();

    await expect(auth.signOut(activeSession)).resolves.toEqual(new URL("https://auth.example/logout"));
    expect(client.end).toHaveBeenCalledWith(activeSession, new URL("https://app.example/signed-out"));

    client.end.mockRejectedValueOnce(new Error("provider unavailable"));
    await expect(auth.signOut(activeSession)).resolves.toEqual(new URL("https://app.example/signed-out"));
  });
});

describe("SealedCookie", () => {
  const schema = z.object({ subject: z.string() });
  const attributes = { httpOnly: true, sameSite: "lax" as const, secure: true, path: "/", maxAge: 60 };

  function cookie(overrides: Partial<ConstructorParameters<typeof SealedCookie<{ subject: string }>>[0]> = {}) {
    return new SealedCookie({
      name: "session",
      schema,
      secret,
      version: "v1",
      additionalAuthenticatedData: "session-aad",
      attributes,
      ...overrides,
    });
  }

  test("round-trips valid state and exposes immutable cookie attributes", () => {
    const sealed = cookie();
    const value = sealed.encode({ subject: "developer" });

    expect(sealed.decode(value)).toEqual({ subject: "developer" });
    expect(sealed.name).toBe("session");
    expect(sealed.options).toEqual(attributes);
    expect(Object.isFrozen(sealed.options)).toBe(true);
    expect(() => Object.assign(sealed.options, { maxAge: 0 })).toThrow();
  });

  test.each([undefined, "", "v2.payload", "v1.", "v1.AA"])("rejects invalid envelope %s", (value) => {
    expect(cookie().decode(value)).toBeUndefined();
  });

  test("isolates ciphertext by secret, authenticated data, and schema", () => {
    const value = cookie().encode({ subject: "developer" });

    expect(cookie({ secret: "different-secret" }).decode(value)).toBeUndefined();
    expect(cookie({ additionalAuthenticatedData: "different-aad" }).decode(value)).toBeUndefined();
    expect(
      new SealedCookie({
        name: "session",
        schema: z.object({ subject: z.number() }),
        secret,
        version: "v1",
        additionalAuthenticatedData: "session-aad",
        attributes,
      }).decode(value),
    ).toBeUndefined();
  });

  test("rejects tampered ciphertext without leaking crypto errors", () => {
    const sealed = cookie();
    const value = sealed.encode({ subject: "developer" });
    const tampered = `${value.slice(0, -1)}${value.endsWith("A") ? "B" : "A"}`;

    expect(sealed.decode(tampered)).toBeUndefined();
  });

  test("validates decoded state even when callers bypass the TypeScript type", () => {
    const sealed = cookie();
    const invalid = sealed.encode({ subject: 42 } as unknown as { subject: string });
    expect(sealed.decode(invalid)).toBeUndefined();
  });
});
