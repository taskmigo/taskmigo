import "server-only";

import * as client from "openid-client";

import type { AuthorizationProvider, OAuthTokens } from "../types";

const transactionSchemaVersion = 1;

interface ProviderState {
  version: number;
  state: string;
  nonce: string;
  codeVerifier: string;
}

function encodeState(state: ProviderState): string {
  return Buffer.from(JSON.stringify(state), "utf8").toString("base64url");
}

function decodeState(value: string): ProviderState {
  const parsed = JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as Partial<ProviderState>;
  if (
    parsed.version !== transactionSchemaVersion ||
    typeof parsed.state !== "string" ||
    typeof parsed.nonce !== "string" ||
    typeof parsed.codeVerifier !== "string"
  ) {
    throw new Error("Invalid authorization provider state");
  }
  return parsed as ProviderState;
}

function tokenLifetime(tokens: client.TokenEndpointResponse & client.TokenEndpointResponseHelpers): number {
  const expiresIn = tokens.expiresIn();
  if (expiresIn === undefined || expiresIn <= 0) throw new Error("OAuth access token lifetime is missing or expired");
  return expiresIn;
}

function localDevelopmentDiscoveryOptions(): client.DiscoveryRequestOptions {
  // openid-client intentionally marks this helper deprecated to discourage insecure non-local deployments.
  // eslint-disable-next-line @typescript-eslint/no-deprecated -- The caller explicitly opts into loopback HTTP discovery.
  return { execute: [client.allowInsecureRequests] };
}

export interface OpenIdClientProviderConfig {
  issuer: URL;
  clientId: string;
  clientSecret: string;
  allowInsecureRequests?: boolean;
}

export function createOpenIdClientProvider(config: OpenIdClientProviderConfig): AuthorizationProvider {
  let configurationPromise: Promise<client.Configuration> | undefined;

  async function configuration(): Promise<client.Configuration> {
    configurationPromise ??= client
      .discovery(
        config.issuer,
        config.clientId,
        { client_secret: config.clientSecret },
        client.ClientSecretBasic(config.clientSecret),
        config.allowInsecureRequests ? localDevelopmentDiscoveryOptions() : undefined,
      )
      .catch((error: unknown) => {
        configurationPromise = undefined;
        throw error;
      });
    return configurationPromise;
  }

  return {
    async beginLogin(callbackUrl) {
      const codeVerifier = client.randomPKCECodeVerifier();
      const state: ProviderState = {
        version: transactionSchemaVersion,
        state: client.randomState(),
        nonce: client.randomNonce(),
        codeVerifier,
      };
      const url = client.buildAuthorizationUrl(await configuration(), {
        redirect_uri: callbackUrl.href,
        scope: "openid profile taskmigo.api",
        state: state.state,
        nonce: state.nonce,
        code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
        code_challenge_method: "S256",
      });
      return { url, state: encodeState(state) };
    },

    async completeLogin({ callbackUrl, state: encodedState }): Promise<OAuthTokens> {
      const state = decodeState(encodedState);
      const tokens = await client.authorizationCodeGrant(await configuration(), callbackUrl, {
        pkceCodeVerifier: state.codeVerifier,
        expectedState: state.state,
        expectedNonce: state.nonce,
        idTokenExpected: true,
      });
      const claims = tokens.claims();
      if (!claims) throw new Error("Authorization server did not return validated ID token claims");

      return {
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
        idToken: tokens.id_token,
        expiresIn: tokenLifetime(tokens),
        subject: claims.sub,
        name: typeof claims.name === "string" && claims.name.length > 0 ? claims.name : undefined,
      };
    },

    async refresh(refreshToken) {
      const tokens = await client.refreshTokenGrant(await configuration(), refreshToken);
      const claims = tokens.claims();
      return {
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
        idToken: tokens.id_token,
        expiresIn: tokenLifetime(tokens),
        subject: claims?.sub,
        name: typeof claims?.name === "string" && claims.name.length > 0 ? claims.name : undefined,
      };
    },

    async endSession({ idToken, postLogoutRedirectUrl }) {
      return client.buildEndSessionUrl(await configuration(), {
        id_token_hint: idToken,
        post_logout_redirect_uri: postLogoutRedirectUrl.href,
      });
    },
  };
}
