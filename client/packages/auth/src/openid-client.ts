import "server-only";

import * as client from "openid-client";

import type { AuthorizationClient, Session } from "./core";

const STATE_VERSION = 1;

type AuthorizationState = {
  version: 1;
  state: string;
  nonce: string;
  codeVerifier: string;
};

type SessionState = {
  version: 1;
  accessToken: string;
  refreshToken: string;
  idToken: string;
};

function encode(value: AuthorizationState | SessionState): string {
  return Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
}

function decodeAuthorizationState(value: string): AuthorizationState {
  const parsed = JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as Partial<AuthorizationState>;
  if (
    parsed.version !== STATE_VERSION ||
    typeof parsed.state !== "string" ||
    typeof parsed.nonce !== "string" ||
    typeof parsed.codeVerifier !== "string"
  ) {
    throw new Error("Invalid authorization state");
  }
  return parsed as AuthorizationState;
}

function decodeSessionState(value: string): SessionState {
  const parsed = JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as Partial<SessionState>;
  if (
    parsed.version !== STATE_VERSION ||
    typeof parsed.accessToken !== "string" ||
    typeof parsed.refreshToken !== "string" ||
    typeof parsed.idToken !== "string"
  ) {
    throw new Error("Invalid authorization session state");
  }
  return parsed as SessionState;
}

function tokenExpiresAt(tokens: client.TokenEndpointResponse & client.TokenEndpointResponseHelpers): number {
  const expiresIn = tokens.expiresIn();
  if (expiresIn === undefined || expiresIn <= 0) throw new Error("OAuth access token lifetime is missing or expired");
  return Date.now() + expiresIn * 1000;
}

function localDevelopmentDiscoveryOptions(): client.DiscoveryRequestOptions {
  // openid-client intentionally marks this helper deprecated to discourage insecure non-local deployments.
  // eslint-disable-next-line @typescript-eslint/no-deprecated -- The caller explicitly opts into loopback HTTP discovery.
  return { execute: [client.allowInsecureRequests] };
}

function claimName(claims: unknown): string | undefined {
  if (typeof claims !== "object" || claims === null || !("name" in claims)) return;
  const name = claims.name;
  return typeof name === "string" && name.length > 0 ? name : undefined;
}

export interface OpenIdClientConfig {
  issuer: URL;
  clientId: string;
  clientSecret: string;
  allowInsecureRequests?: boolean;
}

export class OpenIdAuthorizationClient implements AuthorizationClient {
  private configurationPromise: Promise<client.Configuration> | undefined;

  constructor(private readonly config: OpenIdClientConfig) {}

  async begin(redirectUri: URL) {
    const codeVerifier = client.randomPKCECodeVerifier();
    const authorizationState: AuthorizationState = {
      version: STATE_VERSION,
      state: client.randomState(),
      nonce: client.randomNonce(),
      codeVerifier,
    };
    const redirectTo = client.buildAuthorizationUrl(await this.configuration(), {
      redirect_uri: redirectUri.href,
      scope: "openid profile taskmigo.api",
      state: authorizationState.state,
      nonce: authorizationState.nonce,
      code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
      code_challenge_method: "S256",
    });
    return { redirectTo, state: encode(authorizationState) };
  }

  async complete({ callbackUrl, state }: { callbackUrl: URL; state: string }): Promise<Session> {
    const authorizationState = decodeAuthorizationState(state);
    const tokens = await client.authorizationCodeGrant(await this.configuration(), callbackUrl, {
      pkceCodeVerifier: authorizationState.codeVerifier,
      expectedState: authorizationState.state,
      expectedNonce: authorizationState.nonce,
      idTokenExpected: true,
    });
    const claims = tokens.claims();
    if (!claims) throw new Error("Authorization server did not return validated ID token claims");
    if (!tokens.refresh_token || !tokens.id_token) {
      throw new Error("Authorization server did not return a renewable session");
    }

    return {
      user: { id: claims.sub, name: claimName(claims) },
      expiresAt: tokenExpiresAt(tokens),
      authorizationState: encode({
        version: STATE_VERSION,
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
        idToken: tokens.id_token,
      }),
    };
  }

  async renew(session: Session): Promise<Session> {
    const previous = decodeSessionState(session.authorizationState);
    const tokens = await client.refreshTokenGrant(await this.configuration(), previous.refreshToken);
    const claims = tokens.claims();

    return {
      user: {
        id: claims?.sub ?? session.user.id,
        name: claimName(claims) ?? session.user.name,
      },
      expiresAt: tokenExpiresAt(tokens),
      authorizationState: encode({
        version: STATE_VERSION,
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token ?? previous.refreshToken,
        idToken: tokens.id_token ?? previous.idToken,
      }),
    };
  }

  async end(session: Session, postLogoutRedirectUri: URL): Promise<URL> {
    const state = decodeSessionState(session.authorizationState);
    return client.buildEndSessionUrl(await this.configuration(), {
      id_token_hint: state.idToken,
      post_logout_redirect_uri: postLogoutRedirectUri.href,
    });
  }

  private async configuration(): Promise<client.Configuration> {
    this.configurationPromise ??= client
      .discovery(
        this.config.issuer,
        this.config.clientId,
        { client_secret: this.config.clientSecret },
        client.ClientSecretBasic(this.config.clientSecret),
        this.config.allowInsecureRequests ? localDevelopmentDiscoveryOptions() : undefined,
      )
      .catch((error: unknown) => {
        this.configurationPromise = undefined;
        throw error;
      });
    return this.configurationPromise;
  }
}
