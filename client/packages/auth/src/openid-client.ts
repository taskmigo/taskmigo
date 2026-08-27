import "server-only";

import * as client from "openid-client";
import { z } from "zod";

import type { AuthorizationClient, Session, User } from "./core";

type AuthorizationState = { version: 1; state: string; nonce: string; codeVerifier: string };
type SessionState = { version: 1; accessToken: string; refreshToken: string; idToken: string };
type Tokens = client.TokenEndpointResponse & client.TokenEndpointResponseHelpers;

export interface OpenIdClientConfig {
  issuer: URL;
  clientId: string;
  clientSecret: string;
  allowInsecureRequests?: boolean;
}

export class OpenIdAuthorizationClient implements AuthorizationClient {
  private static readonly STATE_VERSION = 1 as const;
  private static readonly SCOPE = "openid profile taskmigo.api";
  private static readonly PKCE_METHOD = "S256";
  private static readonly AUTHORIZATION_STATE_SCHEMA: z.ZodType<AuthorizationState> = z.object({
    version: z.literal(OpenIdAuthorizationClient.STATE_VERSION),
    state: z.string(),
    nonce: z.string(),
    codeVerifier: z.string(),
  });
  private static readonly SESSION_STATE_SCHEMA: z.ZodType<SessionState> = z.object({
    version: z.literal(OpenIdAuthorizationClient.STATE_VERSION),
    accessToken: z.string(),
    refreshToken: z.string(),
    idToken: z.string(),
  });

  private configurationPromise?: Promise<client.Configuration>;

  constructor(private readonly config: OpenIdClientConfig) {}

  async begin(redirectUri: URL) {
    const codeVerifier = client.randomPKCECodeVerifier();
    const authorizationState: AuthorizationState = {
      version: OpenIdAuthorizationClient.STATE_VERSION,
      state: client.randomState(),
      nonce: client.randomNonce(),
      codeVerifier,
    };

    return {
      redirectTo: client.buildAuthorizationUrl(await this.configuration(), {
        redirect_uri: redirectUri.href,
        scope: OpenIdAuthorizationClient.SCOPE,
        state: authorizationState.state,
        nonce: authorizationState.nonce,
        code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
        code_challenge_method: OpenIdAuthorizationClient.PKCE_METHOD,
      }),
      state: OpenIdAuthorizationClient.encode(authorizationState),
    };
  }

  async complete(callbackUrl: URL, state: string): Promise<Session> {
    const authorizationState = OpenIdAuthorizationClient.decode(
      state,
      OpenIdAuthorizationClient.AUTHORIZATION_STATE_SCHEMA,
    );
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
      user: { id: claims.sub, name: OpenIdAuthorizationClient.claimName(claims) },
      refreshToken: tokens.refresh_token,
      idToken: tokens.id_token,
    });
  }

  async renew(session: Session): Promise<Session> {
    const previous = OpenIdAuthorizationClient.decode(
      session.authorizationState,
      OpenIdAuthorizationClient.SESSION_STATE_SCHEMA,
    );
    const tokens = await client.refreshTokenGrant(await this.configuration(), previous.refreshToken);
    const claims = tokens.claims();

    return this.createSession(tokens, {
      user: {
        id: claims?.sub ?? session.user.id,
        name: OpenIdAuthorizationClient.claimName(claims) ?? session.user.name,
      },
      refreshToken: tokens.refresh_token ?? previous.refreshToken,
      idToken: tokens.id_token ?? previous.idToken,
    });
  }

  async end(session: Session, postLogoutRedirectUri: URL): Promise<URL> {
    const { idToken } = OpenIdAuthorizationClient.decode(
      session.authorizationState,
      OpenIdAuthorizationClient.SESSION_STATE_SCHEMA,
    );
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
      expiresAt: OpenIdAuthorizationClient.tokenExpiresAt(tokens),
      authorizationState: OpenIdAuthorizationClient.encode({
        version: OpenIdAuthorizationClient.STATE_VERSION,
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
        this.config.allowInsecureRequests ? OpenIdAuthorizationClient.developmentDiscoveryOptions() : undefined,
      );
    } catch (error) {
      this.configurationPromise = undefined;
      throw error;
    }
  }

  private static encode(value: AuthorizationState | SessionState): string {
    return Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
  }

  private static decode<T>(value: string, schema: z.ZodType<T>): T {
    return schema.parse(JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as unknown);
  }

  private static tokenExpiresAt(tokens: Tokens): number {
    const expiresIn = tokens.expiresIn();
    if (!expiresIn || expiresIn < 0) throw new Error("Authorization server returned an invalid token lifetime");
    return Date.now() + expiresIn * 1000;
  }

  private static claimName(claims: unknown): string | undefined {
    if (typeof claims !== "object" || claims === null || !("name" in claims)) return;
    const name = claims.name;
    return typeof name === "string" && name.length > 0 ? name : undefined;
  }

  private static developmentDiscoveryOptions(): client.DiscoveryRequestOptions {
    // eslint-disable-next-line @typescript-eslint/no-deprecated -- loopback development only
    return { execute: [client.allowInsecureRequests] };
  }
}
