import { describe, expect, test } from "vitest";
import { z } from "zod";

import { DefaultAuthManager, type AuthorizationClient, type Session } from "./core";
import { createSealedCookie } from "./sealed-cookie";

const secret = "01234567890123456789012345678901";
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

  complete(input: { callbackUrl: URL; state: string }) {
    return this.overrides.complete?.(input) ?? Promise.resolve(session);
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
  return new DefaultAuthManager(new StubAuthorizationClient(overrides), { appUrl: new URL("https://app.example") });
}

describe("auth core", () => {
  test("keeps authorization details behind the client boundary", async () => {
    const auth = authManager();
    const signIn = await auth.beginSignIn("/projects?mine=true");

    expect(signIn.redirectTo.href).toBe("https://auth.example/authorize");
    expect(signIn.transaction).toEqual({ state: "transaction", returnTo: "/projects?mine=true" });
    expect(auth.publicSession(session)).toEqual({ authenticated: true, user: session.user });
  });

  test("rejects external return targets", async () => {
    const auth = authManager();

    await expect(auth.beginSignIn("//attacker.example/path")).resolves.toMatchObject({
      transaction: { returnTo: "/account" },
    });
    await expect(auth.beginSignIn(String.raw`/\attacker.example/path`)).resolves.toMatchObject({
      transaction: { returnTo: "/account" },
    });
  });

  test("separates and validates encrypted cookie state", () => {
    const schema = z.object({ subject: z.string() });
    const sessionCookie = createSealedCookie({ name: "session", purpose: "session", schema, secret, maxAge: 60 });
    const transactionCookie = createSealedCookie({
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
