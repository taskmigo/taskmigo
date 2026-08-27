import "server-only";

import * as client from "openid-client";

import type { AuthorizationClient, Session, User } from "./core";

const STATE_VERSION = 1;

type AuthorizationState = { version: 1; state: string; nonce: string; codeVerifier: string };
type SessionState = { version: 1; accessToken: string; refreshToken: string; idToken: string };

type Tokens = client.TokenEndpointResponse & client.TokenEndpointResponseHelpers;

function encode(value: AuthorizationState | SessionState): string {
  return Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function decodeAuthorizationState(value: string): AuthorizationState {
  const parsed: unknown = JSON.parse(Buffer.from(value, "base64url").toString("utf8"));
  if (
    !isRecord(parsed) ||
    parsed.version !== STATE_VERSION ||
    typeof parsed.state !== "string" ||
    typeof parsed.nonce !== "string" ||
    typeof parsed.codeVerifier !== "string"
  ) {
    throw new Error("Invalid authorization state");
  }
  return parsed as unknown as AuthorizationState;
}

function decodeSessionState(value: string): SessionState {
  const parsed: unknown = JSON.parse(Buffer.from(value, "base64url").toString("utf8"));
  if (
    !isRecord(parsed) ||
    parsed.version !== STATE_VERSION ||
    typeof parsed.accessToken !== "string" ||
    typeof parsed.refreshToken !== "string" ||
    typeof parsed.idToken !== "string"
  ) {
    throw new Error("Invalid authorization session state");
  }
  return parsed as unknown as SessionState;
}

function tokenExpiresAt(tokens: Tokens): number {
  const expiresIn = tokens.expiresIn();
  if (!expiresIn || expiresIn < 0) throw new Error("Authorization server returned an invalid token lifetime");
  return Date.now() + expiresIn * 1000;
}

function localDevelopmentDiscoveryOptions(): client.DiscoveryRequestOptions {
  // eslint-disable-next-line @typescript-eslint/no-deprecated -- loopback development only
  return { execute: [client.allowInsecureRequests] };
}

function claimName(claims: unknown): string | undefined {
  if (!isRecord(claims)) return;
  const { name } = claims;
  return typeof name === "string" && name.length > 0 ? name : undefined;
}

export interface OpenIdClientConfig {
  issuer: URL;
  clientId: string;
  clientSecret: string;
  allowInsecureRequests?: boolean;
}

export class OpenIdAuthorizationClient implements AuthorizationClient {
  private configurationPromise?: Promise<client.Configuration>;

  constructor(private readonly config: OpenIdClientConfig) {}

  async begin(redirectUri: URL) {
    const codeVerifier = client.randomPKCECodeVerifier();
    const authorizationState: AuthorizationState = {
      version: STATE_VERSION,
      state: client.randomState(),
      nonce: client.randomNonce(),
      codeVerifier,
    };

    return {
      redirectTo: client.buildAuthorizationUrl(await this.configuration(), {
        redirect_uri: redirectUri.href,
        scope: "openid profile taskmigo.api",
        state: authorizationState.state,
        nonce: authorizationState.nonce,
        code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
        code_challenge_method: "S256",
      }),
      state: encode(authorizationState),
    };
  }

  async complete(callbackUrl: URL, state: string): Promise<Session> {
    const authorizationState = decodeAuthorizationState(state);
    const tokens = await client.authorizationCodeGrant(await this.configuration(), callbackUrl, {
      pkceCodeVerifier: authorizationState.codeVerifier,
      expectedState: authorizationState.state,
      expectedNonce: authorizationState.nonce,
      idTokenExpected: true,
    });
    const claims = tokens.claims();
    if (!claims) throw new Error("Authorization server returned no ID token claims");
    if (!tokens.refresh_token || !tokens.id_token)
      throw new Error("Authorization server returned a non-renewable session");

    return this.createSession(tokens, {
      user: { id: claims.sub, name: claimName(claims) },
      refreshToken: tokens.refresh_token,
      idToken: tokens.id_token,
    });
  }

  async renew(session: Session): Promise<Session> {
    const previous = decodeSessionState(session.authorizationState);
    const tokens = await client.refreshTokenGrant(await this.configuration(), previous.refreshToken);
    const claims = tokens.claims();

    return this.createSession(tokens, {
      user: { id: claims?.sub ?? session.user.id, name: claimName(claims) ?? session.user.name },
      refreshToken: tokens.refresh_token ?? previous.refreshToken,
      idToken: tokens.id_token ?? previous.idToken,
    });
  }

  async end(session: Session, postLogoutRedirectUri: URL): Promise<URL> {
    const { idToken } = decodeSessionState(session.authorizationState);
    return client.buildEndSessionUrl(await this.configuration(), {
      id_token_hint: idToken,
      post_logout_redirect_uri: postLogoutRedirectUri.href,
    });
  }

  private createSession(
    tokens: Tokens,
    { user, refreshToken, idToken }: { user: User; refreshToken: string; idToken: string },
  ): Session {
    return {
      user,
      expiresAt: tokenExpiresAt(tokens),
      authorizationState: encode({
        version: STATE_VERSION,
        accessToken: tokens.access_token,
        refreshToken,
        idToken,
      }),
    };
  }

  private configuration(): Promise<client.Configuration> {
    return (this.configurationPromise ??= this.discover());
  }

  private async discover(): Promise<client.Configuration> {
    try {
      return await client.discovery(
        this.config.issuer,
        this.config.clientId,
        { client_secret: this.config.clientSecret },
        client.ClientSecretBasic(this.config.clientSecret),
        this.config.allowInsecureRequests ? localDevelopmentDiscoveryOptions() : undefined,
      );
    } catch (error) {
      this.configurationPromise = undefined;
      throw error;
    }
  }
}
