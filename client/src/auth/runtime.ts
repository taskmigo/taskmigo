import "server-only";

import {
  AuthNavigation,
  DefaultAuthManager,
  type AuthManager,
  type AuthorizationTransaction,
  type Session,
  type User,
} from "@taskmigo/auth";
import { OpenIdAuthorizationClient } from "@taskmigo/auth/openid-client";
import { getConfig, type Config } from "@taskmigo/config/server";
import { SealedValue } from "@taskmigo/foundation/node/sealed-value";
import { globalSingleton } from "@taskmigo/foundation/runtime";
import { CookieState, type CookieAttributes } from "@taskmigo/foundation/state";
import { cookies } from "next/headers";
import { z } from "zod";

const AUTH_RUNTIME = Symbol.for("taskmigo.auth.runtime");

const TRANSACTION_SCHEMA = z.object({ state: z.string().min(1), returnTo: z.string().min(1) });
const USER_SCHEMA = z
  .object({ id: z.string().min(1), name: z.string().min(1).optional() })
  .transform(({ id, name }): User => (name === undefined ? { id } : { id, name }));
const SESSION_SCHEMA = z.object({
  user: USER_SCHEMA,
  expiresAt: z.number(),
  authorizationState: z.string().min(1),
});

interface EncryptedCookieOptions {
  name: string;
  secret: string;
  version: string;
  additionalAuthenticatedData: string;
  attributes: CookieAttributes;
}

export interface AuthContext {
  readonly manager: AuthManager;
  readonly returnToParameter: string;
  readonly sessions: CookieState<Session>;
  readonly transactions: CookieState<AuthorizationTransaction>;
}

function encryptedCookie<T>(options: EncryptedCookieOptions, schema: z.ZodType<T>): CookieState<T> {
  return new CookieState({
    name: options.name,
    codec: new SealedValue({
      secret: options.secret,
      version: options.version,
      context: options.additionalAuthenticatedData,
      parse: (value) => {
        const parsed = schema.safeParse(value);
        return parsed.success ? parsed.data : undefined;
      },
    }),
    attributes: options.attributes,
  });
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
  const cookieAttributes = auth.cookie.attributes;

  return Object.freeze({
    manager,
    returnToParameter: auth.returnToParameter,
    sessions: encryptedCookie(
      {
        name: auth.sessionCookie.name,
        secret: auth.sessionSecret,
        version: auth.cookie.version,
        additionalAuthenticatedData: auth.sessionCookie.additionalAuthenticatedData,
        attributes: { ...cookieAttributes, maxAge: auth.sessionCookie.maxAge },
      },
      SESSION_SCHEMA,
    ),
    transactions: encryptedCookie(
      {
        name: auth.transactionCookie.name,
        secret: auth.sessionSecret,
        version: auth.cookie.version,
        additionalAuthenticatedData: auth.transactionCookie.additionalAuthenticatedData,
        attributes: { ...cookieAttributes, maxAge: auth.transactionCookie.maxAge },
      },
      TRANSACTION_SCHEMA,
    ),
  });
}

export function getAuth(): AuthContext {
  return globalSingleton(AUTH_RUNTIME, () => createAuth(getConfig()));
}

export async function getSession(): Promise<Session | undefined> {
  return getAuth().sessions.read(await cookies());
}
