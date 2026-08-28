import "server-only";

import { AuthNavigation, DefaultAuthManager } from "@taskmigo/auth";
import { NextAuth } from "@taskmigo/auth/next";
import { OpenIdAuthorizationClient } from "@taskmigo/auth/openid-client";
import { getConfig, type Config } from "@taskmigo/config/server";

interface AuthGlobal {
  taskmigoAuthRuntime?: AuthRuntime;
}

export class AuthRuntime {
  readonly #auth: NextAuth;

  constructor(config: Config) {
    const { appUrl, auth } = config;
    const authorizationClient = new OpenIdAuthorizationClient({
      issuer: auth.issuer,
      clientId: auth.clientId,
      clientSecret: auth.clientSecret,
      scope: auth.scope,
      stateVersion: auth.oidcStateVersion,
      allowInsecureRequests: auth.allowInsecureRequests,
    });
    const navigation = new AuthNavigation({
      appUrl,
      callbackUrl: new URL(auth.callbackPath, appUrl),
      postLogoutRedirectUrl: new URL(auth.postLogoutRedirectPath, appUrl),
      defaultReturnTo: auth.defaultReturnTo,
    });
    const manager = new DefaultAuthManager(authorizationClient, navigation, {
      refreshSkewMilliseconds: auth.refreshSkewMilliseconds,
    });
    const cookieAttributes = auth.cookie.attributes;

    this.#auth = new NextAuth(manager, {
      returnToParameter: auth.returnToParameter,
      sessionCookie: {
        name: auth.sessionCookie.name,
        secret: auth.sessionSecret,
        version: auth.cookie.version,
        additionalAuthenticatedData: auth.sessionCookie.additionalAuthenticatedData,
        attributes: { ...cookieAttributes, maxAge: auth.sessionCookie.maxAge },
      },
      transactionCookie: {
        name: auth.transactionCookie.name,
        secret: auth.sessionSecret,
        version: auth.cookie.version,
        additionalAuthenticatedData: auth.transactionCookie.additionalAuthenticatedData,
        attributes: { ...cookieAttributes, maxAge: auth.transactionCookie.maxAge },
      },
    });
  }

  get auth(): NextAuth {
    return this.#auth;
  }

  static get(): AuthRuntime {
    const global = globalThis as typeof globalThis & AuthGlobal;
    return (global.taskmigoAuthRuntime ??= new AuthRuntime(getConfig()));
  }
}
