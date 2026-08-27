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
  constructor(private readonly overrides: Partial<AuthorizationClient> = {}) {}

  begin(redirectUri: URL) {
    return (
      this.overrides.begin?.(redirectUri) ??
      Promise.resolve({ redirectTo: new URL("https://auth.example/authorize"), state: "transaction" })
    );
  }

  complete(callbackUrl: URL, state: string) {
    return this.overrides.complete?.(callbackUrl, state) ?? Promise.resolve(session);
  }

  renew(current: Session) {
    return this.overrides.renew?.(current) ?? Promise.resolve(session);
  }

  end(current: Session, postLogoutRedirectUri: URL) {
    return (
      this.overrides.end?.(current, postLogoutRedirectUri) ?? Promise.resolve(new URL("https://auth.example/logout"))
    );
  }
}

function authManager(overrides: Partial<AuthorizationClient> = {}) {
  return new DefaultAuthManager(new StubAuthorizationClient(overrides), new AuthNavigation(appUrl));
}

describe("auth navigation", () => {
  test("normalizes only same-origin return targets", () => {
    const navigation = new AuthNavigation(appUrl);

    expect(navigation.resolveReturnTo("/projects?mine=true#active")).toBe("/projects?mine=true#active");
    expect(navigation.resolveReturnTo("//attacker.example/path")).toBe("/account");
    expect(navigation.resolveReturnTo(String.raw`/\attacker.example/path`)).toBe("/account");
  });

  test("rejects unsafe navigation configuration", () => {
    expect(() => new AuthNavigation(appUrl, { defaultReturnTo: "https://attacker.example" })).toThrow(
      "Default return target must resolve to the application origin",
    );
    expect(() => new AuthNavigation(appUrl, { callbackPath: "https://attacker.example/callback" })).toThrow(
      "Callback URL must resolve to the application origin",
    );
  });

  test("does not expose mutable URL state", () => {
    const navigation = new AuthNavigation(appUrl);
    const callback = navigation.callbackUrl;
    callback.pathname = "/tampered";

    expect(navigation.callbackUrl.pathname).toBe("/api/auth/callback");
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
    const sessionCookie = new SealedCookie({ name: "session", purpose: "session", schema, secret, maxAge: 60 });
    const transactionCookie = new SealedCookie({
      name: "transaction",
      purpose: "transaction",
      schema,
      secret,
      maxAge: 60,
    });
    const value = sessionCookie.encode({ subject: "developer" });

    expect(sessionCookie.decode(value)).toEqual({ subject: "developer" });
    expect(transactionCookie.decode(value)).toBeUndefined();
  });
});
