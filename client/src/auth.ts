import "server-only";

import { AuthNavigation, DefaultAuthManager } from "@taskmigo/auth";
import { NextAuth } from "@taskmigo/auth/next";
import { OpenIdAuthorizationClient } from "@taskmigo/auth/openid-client";
import { getConfig } from "@taskmigo/config/server";

class AuthRuntime {
  static readonly #CLIENT_ID = "taskmigo-client";
  static readonly #CALLBACK_PATH = "/api/auth/callback";
  static readonly #POST_LOGOUT_REDIRECT_PATH = "/";
  static readonly #DEFAULT_RETURN_TO = "/account";
  static readonly #RETURN_TO_PARAMETER = "returnTo";
  static readonly #OIDC_SCOPE = "openid profile taskmigo.api";
  static readonly #SERIALIZATION_VERSION = "v1";
  static readonly #REFRESH_SKEW_MILLISECONDS = 30_000;
  static readonly #SESSION_COOKIE_NAME = "taskmigo_session";
  static readonly #TRANSACTION_COOKIE_NAME = "taskmigo_auth_transaction";
  static readonly #SESSION_MAX_AGE_SECONDS = 8 * 60 * 60;
  static readonly #TRANSACTION_MAX_AGE_SECONDS = 10 * 60;

  #auth?: NextAuth;

  get(): NextAuth {
    return (this.#auth ??= this.#create());
  }

  #create(): NextAuth {
    const config = getConfig();
    const authorizationClient = new OpenIdAuthorizationClient({
      issuer: config.issuer,
      clientId: AuthRuntime.#CLIENT_ID,
      clientSecret: config.clientSecret,
      scope: AuthRuntime.#OIDC_SCOPE,
      stateVersion: AuthRuntime.#SERIALIZATION_VERSION,
      allowInsecureRequests: config.allowInsecureRequests,
    });
    const navigation = new AuthNavigation({
      appUrl: config.appUrl,
      callbackUrl: new URL(AuthRuntime.#CALLBACK_PATH, config.appUrl),
      postLogoutRedirectUrl: new URL(AuthRuntime.#POST_LOGOUT_REDIRECT_PATH, config.appUrl),
      defaultReturnTo: AuthRuntime.#DEFAULT_RETURN_TO,
    });
    const authManager = new DefaultAuthManager(authorizationClient, navigation, {
      refreshSkewMilliseconds: AuthRuntime.#REFRESH_SKEW_MILLISECONDS,
    });

    return new NextAuth(authManager, {
      returnToParameter: AuthRuntime.#RETURN_TO_PARAMETER,
      sessionCookie: AuthRuntime.#cookieConfig(
        AuthRuntime.#SESSION_COOKIE_NAME,
        "session",
        AuthRuntime.#SESSION_MAX_AGE_SECONDS,
        config.sessionSecret,
      ),
      transactionCookie: AuthRuntime.#cookieConfig(
        AuthRuntime.#TRANSACTION_COOKIE_NAME,
        "transaction",
        AuthRuntime.#TRANSACTION_MAX_AGE_SECONDS,
        config.sessionSecret,
      ),
    });
  }

  static #cookieConfig(name: string, purpose: string, maxAge: number, secret: string) {
    const version = AuthRuntime.#SERIALIZATION_VERSION;
    return {
      name,
      secret,
      version,
      additionalAuthenticatedData: `${AuthRuntime.#CLIENT_ID}:${purpose}:${version}`,
      attributes: {
        httpOnly: true,
        sameSite: "lax" as const,
        secure: process.env.NODE_ENV === "production",
        path: "/",
        maxAge,
      },
    };
  }
}

const authRuntime = new AuthRuntime();

export function getAuth(): NextAuth {
  return authRuntime.get();
}
