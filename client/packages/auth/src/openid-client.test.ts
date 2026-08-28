import { beforeEach, describe, expect, test, vi } from "vitest";

import type { Session } from "./core";
import type { OpenIdClientConfig } from "./openid-client";

vi.mock("server-only", () => ({}));

const oidc = vi.hoisted(() => ({
  discovery: vi.fn(),
  randomPKCECodeVerifier: vi.fn(() => "verifier"),
  randomState: vi.fn(() => "state"),
  randomNonce: vi.fn(() => "nonce"),
  calculatePKCECodeChallenge: vi.fn(async () => "challenge"),
  buildAuthorizationUrl: vi.fn(() => new URL("https://auth.example/authorize")),
  authorizationCodeGrant: vi.fn(),
  refreshTokenGrant: vi.fn(),
  buildEndSessionUrl: vi.fn(() => new URL("https://auth.example/logout")),
  ClientSecretBasic: vi.fn(() => ({ method: "client-secret-basic" })),
  allowInsecureRequests: vi.fn(),
}));

vi.mock("openid-client", () => oidc);

const configuration = { serverMetadata: () => ({}) };
const baseConfig: OpenIdClientConfig = {
  issuer: new URL("https://auth.example"),
  clientId: "client",
  clientSecret: "secret",
  scope: "openid profile api",
  stateVersion: "state-v2",
};

type TokenOverrides = Partial<{
  access_token: string;
  refresh_token: string | undefined;
  id_token: string | undefined;
  claims: unknown;
  expiresIn: number | undefined;
}>;

function tokens(overrides: TokenOverrides = {}) {
  const values = {
    access_token: "access",
    refresh_token: "refresh" as string | undefined,
    id_token: "id-token" as string | undefined,
    claims: { sub: "developer", name: "Developer" } as unknown,
    expiresIn: 60 as number | undefined,
    ...overrides,
  };
  return {
    access_token: values.access_token,
    refresh_token: values.refresh_token,
    id_token: values.id_token,
    claims: () => values.claims,
    expiresIn: () => values.expiresIn,
  };
}

async function createClient(config: OpenIdClientConfig = baseConfig) {
  const { OpenIdAuthorizationClient } = await import("./openid-client");
  return new OpenIdAuthorizationClient(config);
}

async function authorizedState(instance: Awaited<ReturnType<typeof createClient>>) {
  const authorization = await instance.begin(new URL("https://app.example/callback"));
  return authorization.state;
}

async function createSession(instance: Awaited<ReturnType<typeof createClient>>, tokenOverrides: TokenOverrides = {}) {
  oidc.authorizationCodeGrant.mockResolvedValueOnce(tokens(tokenOverrides));
  return instance.complete(
    new URL("https://app.example/callback?code=code&state=state"),
    await authorizedState(instance),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  oidc.discovery.mockResolvedValue(configuration);
  oidc.authorizationCodeGrant.mockResolvedValue(tokens());
  oidc.refreshTokenGrant.mockResolvedValue(tokens());
});

describe("OpenIdAuthorizationClient", () => {
  test("builds PKCE authorization requests and caches successful discovery", async () => {
    const instance = await createClient();
    const redirectUri = new URL("https://app.example/callback");

    const first = await instance.begin(redirectUri);
    const second = await instance.begin(redirectUri);

    expect(first.redirectTo).toEqual(new URL("https://auth.example/authorize"));
    expect(first.state).toBe(second.state);
    expect(oidc.discovery).toHaveBeenCalledTimes(1);
    expect(oidc.discovery).toHaveBeenCalledWith(
      baseConfig.issuer,
      "client",
      { client_secret: "secret" },
      { method: "client-secret-basic" },
      undefined,
    );
    expect(oidc.buildAuthorizationUrl).toHaveBeenCalledWith(configuration, {
      redirect_uri: redirectUri.href,
      scope: "openid profile api",
      state: "state",
      nonce: "nonce",
      code_challenge: "challenge",
      code_challenge_method: "S256",
    });
  });

  test("opts into insecure discovery only when explicitly configured", async () => {
    const instance = await createClient({ ...baseConfig, allowInsecureRequests: true });
    await instance.begin(new URL("http://app.example/callback"));

    expect(oidc.discovery.mock.calls[0]?.[4]).toEqual({ execute: [oidc.allowInsecureRequests] });
  });

  test("drops failed discovery promises so a later attempt can recover", async () => {
    oidc.discovery.mockRejectedValueOnce(new Error("metadata unavailable")).mockResolvedValueOnce(configuration);
    const instance = await createClient();

    await expect(instance.begin(new URL("https://app.example/callback"))).rejects.toThrow("metadata unavailable");
    await expect(instance.begin(new URL("https://app.example/callback"))).resolves.toBeDefined();
    expect(oidc.discovery).toHaveBeenCalledTimes(2);
  });

  test("exchanges authorization state into an opaque renewable session", async () => {
    const instance = await createClient();
    const state = await authorizedState(instance);
    const callback = new URL("https://app.example/callback?code=code&state=state");
    oidc.authorizationCodeGrant.mockResolvedValueOnce(tokens());

    const result = await instance.complete(callback, state);

    expect(result.user).toEqual({ id: "developer", name: "Developer" });
    expect(result.expiresAt).toBeGreaterThan(Date.now());
    expect(result.authorizationState).not.toContain("refresh");
    expect(oidc.authorizationCodeGrant).toHaveBeenCalledWith(configuration, callback, {
      pkceCodeVerifier: "verifier",
      expectedState: "state",
      expectedNonce: "nonce",
      idTokenExpected: true,
    });
  });

  test("validates authorization state version and encoding", async () => {
    const instance = await createClient();
    const wrongVersion = Buffer.from(
      JSON.stringify({ version: "old", state: "state", nonce: "nonce", codeVerifier: "verifier" }),
    ).toString("base64url");

    await expect(instance.complete(new URL("https://app.example/callback"), wrongVersion)).rejects.toThrow();
    await expect(instance.complete(new URL("https://app.example/callback"), "not-json")).rejects.toThrow();
    expect(oidc.authorizationCodeGrant).not.toHaveBeenCalled();
  });

  test("rejects incomplete authorization-server responses with stable messages", async () => {
    const instance = await createClient();
    const state = await authorizedState(instance);

    oidc.authorizationCodeGrant.mockResolvedValueOnce(tokens({ claims: undefined }));
    await expect(instance.complete(new URL("https://app.example/callback"), state)).rejects.toThrow(
      "Authorization server returned no ID token claims",
    );

    for (const overrides of [{ refresh_token: "" }, { id_token: "" }]) {
      oidc.authorizationCodeGrant.mockResolvedValueOnce(tokens(overrides));
      await expect(instance.complete(new URL("https://app.example/callback"), state)).rejects.toThrow(
        "Authorization server returned a non-renewable session",
      );
    }
  });

  test.each([{ claims: {} }, { claims: { sub: "" } }, { claims: { sub: 42 } }, { claims: "invalid" }])(
    "rejects an invalid subject claim with a stable message: $claims",
    async ({ claims }) => {
      const instance = await createClient();
      const state = await authorizedState(instance);
      oidc.authorizationCodeGrant.mockResolvedValueOnce(tokens({ claims }));

      await expect(instance.complete(new URL("https://app.example/callback"), state)).rejects.toThrow(
        "Authorization server returned no subject claim",
      );
    },
  );

  test.each([undefined, 0, -1])("rejects invalid token lifetime %s", async (expiresIn) => {
    const instance = await createClient();
    const state = await authorizedState(instance);
    oidc.authorizationCodeGrant.mockResolvedValueOnce(tokens({ expiresIn }));

    await expect(instance.complete(new URL("https://app.example/callback"), state)).rejects.toThrow(
      "Authorization server returned an invalid token lifetime",
    );
  });

  test("renews sessions while preserving omitted identity and token fields", async () => {
    const instance = await createClient();
    const current = await createSession(instance);
    oidc.refreshTokenGrant.mockResolvedValueOnce(
      tokens({ refresh_token: undefined, id_token: undefined, claims: undefined, access_token: "renewed-access" }),
    );

    const renewed = await instance.renew(current);

    expect(renewed.user).toEqual(current.user);
    expect(renewed.authorizationState).not.toBe(current.authorizationState);
    expect(oidc.refreshTokenGrant).toHaveBeenCalledWith(configuration, "refresh");

    await instance.end(renewed, new URL("https://app.example/signed-out"));
    expect(oidc.buildEndSessionUrl).toHaveBeenCalledWith(configuration, {
      id_token_hint: "id-token",
      post_logout_redirect_uri: "https://app.example/signed-out",
    });
  });

  test("adopts renewed identity and replacement refresh/id tokens", async () => {
    const instance = await createClient();
    const current = await createSession(instance);
    oidc.refreshTokenGrant.mockResolvedValueOnce(
      tokens({
        refresh_token: "refresh-2",
        id_token: "id-token-2",
        claims: { sub: "developer", name: "Developer Two" },
      }),
    );

    const renewed = await instance.renew(current);
    expect(renewed.user).toEqual({ id: "developer", name: "Developer Two" });

    await instance.end(renewed, new URL("https://app.example/signed-out"));
    expect(oidc.buildEndSessionUrl).toHaveBeenLastCalledWith(configuration, {
      id_token_hint: "id-token-2",
      post_logout_redirect_uri: "https://app.example/signed-out",
    });
  });

  test.each([
    { label: "empty name", claims: { sub: "developer", name: "" } },
    { label: "missing name", claims: { sub: "developer" } },
    { label: "non-string name", claims: { sub: "developer", name: 42 } },
    { label: "non-object claims", claims: "invalid-claims" },
    // eslint-disable-next-line unicorn/no-null -- OpenID Connect adapters may surface null claims at runtime.
    { label: "null claims", claims: null },
  ])("keeps the previous identity for $label", async ({ claims }) => {
    const instance = await createClient();
    const current = await createSession(instance);
    oidc.refreshTokenGrant.mockResolvedValueOnce(tokens({ claims }));

    await expect(instance.renew(current)).resolves.toMatchObject({ user: current.user });
  });

  test("rejects session state serialized by another version", async () => {
    const instance = await createClient();
    const current: Session = {
      user: { id: "developer" },
      expiresAt: Date.now(),
      authorizationState: Buffer.from(
        JSON.stringify({ version: "old", accessToken: "a", refreshToken: "r", idToken: "i" }),
      ).toString("base64url"),
    };

    await expect(instance.renew(current)).rejects.toThrow();
    await expect(instance.end(current, new URL("https://app.example/signed-out"))).rejects.toThrow();
    expect(oidc.refreshTokenGrant).not.toHaveBeenCalled();
  });
});
