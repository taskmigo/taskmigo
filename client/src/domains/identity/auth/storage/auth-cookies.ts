import "server-only";

import { z } from "zod";

import type { Config } from "@/server/config";

import type { AuthorizationTransaction, Session, User } from "../contracts";
import { CookieState, type CookieAttributes } from "./cookie-state";
import { SealedValue } from "./sealed-value";

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

export interface AuthCookies {
  readonly sessions: CookieState<Session>;
  readonly transactions: CookieState<AuthorizationTransaction>;
}

export function createAuthCookies(auth: Config["auth"]): AuthCookies {
  const cookieAttributes = auth.cookie.attributes;

  return {
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
  };
}
