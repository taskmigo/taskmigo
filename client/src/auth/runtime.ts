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
import { CookieState, type CookieAttributes, type StringCodec } from "@taskmigo/foundation/state";
import { cookies } from "next/headers";
import { z } from "zod";

import { RefreshCoordinator } from "./refresh-coordinator";

const AUTH_RUNTIME = Symbol.for("taskmigo.auth.runtime");

const TRANSACTION_SCHEMA = z.object({ state: z.string().min(1), returnTo: z.string().min(1) });
const USER_SCHEMA = z
  .object({ id: z.string().min(1), name: z.string().min(1).optional() })
  .transform(({ id, name }): User => (name === undefined ? { id } : { id, name }));
const SESSION_SCHEMA = z.object({
  id: z.uuid(),
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
  readonly refreshCoordinator: RefreshCoordinator;
  readonly transactions: CookieState<AuthorizationTransaction>;
}

function encryptedCodec<T>(options: Omit<EncryptedCookieOptions, "name" | "attributes">, schema: z.ZodType<T>): StringCodec<T> {
  return new SealedValue({
    secret: options.secret,
    version: options.version,
    context: options.additionalAuthenticatedData,
    parse: (value) => {
      const parsed = schema.safeParse(value);
      return parsed.success ? parsed.data : undefined;
    },
  });
}

function encryptedCookie<T>(options: EncryptedCookieOptions, schema: z.ZodType<T>): CookieState<T> {
  return new CookieState({
    name: options.name,
    codec: encryptedCodec(options, schema),
    attributes: options.attributes,
  });
}

export function createAuth(config: Config): AuthContext {
  const { appUrl, auth, backend } = config;
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
  const sessionCodec = encryptedCodec(
    {
      secret: auth.sessionSecret,
      version: auth.cookie.version,
      additionalAuthenticatedData: auth.sessionCookie.additionalAuthenticatedData,
    },
    SESSION_SCHEMA,
  );
  const sessions = new CookieState({
    name: auth.sessionCookie.name,
    codec: sessionCodec,
    attributes: { ...cookieAttributes, maxAge: auth.sessionCookie.maxAge },
  });

  return Object.freeze({
    manager,
    returnToParameter: auth.returnToParameter,
    sessions,
    refreshCoordinator: new RefreshCoordinator({
      backendUrl: backend.url,
      internalSecret: backend.internalSecret,
      sessionCodec,
      sessionMaxAgeSeconds: auth.sessionCookie.maxAge,
      refreshSkewMilliseconds: auth.refreshSkewMilliseconds,
      refreshWaitMilliseconds: backend.refreshWaitMilliseconds,
      requestTimeoutMilliseconds: backend.timeoutMilliseconds,
    }),
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
