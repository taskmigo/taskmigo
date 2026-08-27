import { describe, expect, test } from "vitest";
import { z } from "zod";

import { AuthNavigation, DefaultAuthManager, type AuthorizationClient, type Session } from "./core";
import { SealedCookie } from "./sealed-cookie";

const secret = "01234567890123456789012345678901";
const appUrl = new URL("https://app.example");
const session: Session = {
  user: { id: "developer", name: "Developer" },
  expiresAt: Date.now() + 60_000,
  authorizationState: "opaque",
};

class StubAuthorizationClient implements AuthorizationClient {
  readonly #overrides: Partial<AuthorizationClient>;

  constructor(overrides: Partial<AuthorizationClient> = {}) {
    this.#overrides = overrides;
  }

  begin(redirectUri: URL) {
    return (
      this.#overrides.begin?.(redirectUri) ??
      Promise.resolve({ redirectTo: new URL("https://auth.example/authorize"), state: "transaction" })
    );
  }

  complete(callbackUrl: URL, state: string) {
    return this.#overrides.complete?.(callbackUrl, state) ?? Promise.resolve(session);
  }

  renew(current: Session) {
    return this.#overrides.renew?.(current) ?? Promise.resolve(session);
  }

  end(current: Session, postLogoutRedirectUri: URL) {
    return (
      this.#overrides.end?.(current, postLogoutRedirectUri) ?? Promise.resolve(new URL("https://auth.example/logout"))
    );
  }
}

function navigation() {
  return new AuthNavigation({
    appUrl,
    callbackUrl: new URL("/api/auth/callback", appUrl),
    postLogoutRedirectUrl: new URL("/", appUrl),
    defaultReturnTo: "/account",
  });
}

function authManager(overrides: Partial<AuthorizationClient> = {}) {
  return new DefaultAuthManager(new StubAuthorizationClient(overrides), navigation(), {
    refreshSkewMilliseconds: 30_000,
  });
}

describe("auth navigation", () => {
  test("normalizes only same-origin return targets", () => {
    const authNavigation = navigation();

    expect(authNavigation.resolveReturnTo("/projects?mine=true#active")).toBe("/projects?mine=true#active");
    expect(authNavigation.resolveReturnTo("//attacker.example/path")).toBe("/account");
    expect(authNavigation.resolveReturnTo(String.raw`/\attacker.example/path`)).toBe("/account");
  });

  test("rejects unsafe navigation configuration", () => {
    expect(
      () =>
        new AuthNavigation({
          appUrl,
          callbackUrl: new URL("/api/auth/callback", appUrl),
          postLogoutRedirectUrl: new URL("/", appUrl),
          defaultReturnTo: "https://attacker.example",
        }),
    ).toThrow("Default return target must resolve to the application origin");
    expect(
      () =>
        new AuthNavigation({
          appUrl,
          callbackUrl: new URL("https://attacker.example/callback"),
          postLogoutRedirectUrl: new URL("/", appUrl),
          defaultReturnTo: "/account",
        }),
    ).toThrow("Callback URL must resolve to the application origin");
  });

  test("does not expose mutable URL state", () => {
    const authNavigation = navigation();
    const callback = authNavigation.callbackUrl;
    callback.pathname = "/tampered";

    expect(authNavigation.callbackUrl.pathname).toBe("/api/auth/callback");
  });
});

describe("auth core", () => {
  test("keeps authorization details behind the client boundary", async () => {
    const auth = authManager();
    const signIn = await auth.beginSignIn("/projects?mine=true");

    expect(signIn.redirectTo.href).toBe("https://auth.example/authorize");
    expect(signIn.transaction).toEqual({ state: "transaction", returnTo: "/projects?mine=true" });
    await expect(
      auth.completeSignIn(new URL("https://app.example/api/auth/callback"), signIn.transaction),
    ).resolves.toEqual({
      redirectTo: new URL("https://app.example/projects?mine=true"),
      session,
    });
  });

  test("separates and validates encrypted cookie state", () => {
    const schema = z.object({ subject: z.string() });
    const attributes = { httpOnly: true, sameSite: "lax" as const, secure: true, path: "/", maxAge: 60 };
    const sessionCookie = new SealedCookie({
      name: "session",
      schema,
      secret,
      version: "v1",
      additionalAuthenticatedData: "session",
      attributes,
    });
    const transactionCookie = new SealedCookie({
      name: "transaction",
      schema,
      secret,
      version: "v1",
      additionalAuthenticatedData: "transaction",
      attributes,
    });
    const value = sessionCookie.encode({ subject: "developer" });

    expect(sessionCookie.decode(value)).toEqual({ subject: "developer" });
    expect(transactionCookie.decode(value)).toBeUndefined();
  });
});
