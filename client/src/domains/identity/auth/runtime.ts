import "server-only";

import { cookies } from "next/headers";

import { getConfig, type Config } from "@/server/config";

import type { AuthManager, Session } from "./contracts";
import { DefaultAuthManager } from "./manager";
import { AuthNavigation } from "./navigation";
import { OpenIdAuthorizationClient } from "./openid-client";
import { createAuthCookies, type AuthCookies } from "./storage/auth-cookies";

const AUTH_RUNTIME = Symbol.for("taskmigo.auth.runtime");

export interface AuthContext extends AuthCookies {
  readonly manager: AuthManager;
  readonly returnToParameter: string;
}

export function createAuth(config: Config): AuthContext {
  const { appUrl, auth } = config;
  const authorizationClient = new OpenIdAuthorizationClient({
    issuer: auth.issuer,
    clientId: auth.clientId,
    clientSecret: auth.clientSecret,
    scope: auth.scope,
    stateVersion: auth.oidcStateVersion,
    allowInsecureRequests: auth.allowInsecureRequests,
  });
  const manager = new DefaultAuthManager(
    authorizationClient,
    new AuthNavigation({
      appUrl,
      callbackUrl: new URL(auth.callbackPath, appUrl),
      postLogoutRedirectUrl: new URL(auth.postLogoutRedirectPath, appUrl),
      defaultReturnTo: auth.defaultReturnTo,
    }),
    { refreshSkewMilliseconds: auth.refreshSkewMilliseconds },
  );
  return Object.freeze({
    manager,
    returnToParameter: auth.returnToParameter,
    ...createAuthCookies(auth),
  });
}

export function getAuth(): AuthContext {
  if (Object.hasOwn(globalThis, AUTH_RUNTIME)) {
    return Reflect.get(globalThis, AUTH_RUNTIME) as AuthContext;
  }

  const context = createAuth(getConfig());
  Reflect.set(globalThis, AUTH_RUNTIME, context);
  return context;
}

export async function getSession(): Promise<Session | undefined> {
  return getAuth().sessions.read(await cookies());
}
