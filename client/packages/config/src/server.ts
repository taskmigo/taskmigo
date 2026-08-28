import "server-only";

import { z } from "zod";

const url = z.url().transform((value) => new URL(value));
const nonNegativeInteger = z.coerce.number().int().nonnegative();
const positiveInteger = z.coerce.number().int().positive();

const configSchema = z
  .object({
    TASKMIGO_CLIENT_URL: url,
    TASKMIGO_AUTH_ISSUER: url,
    TASKMIGO_AUTH_CLIENT_ID: z.string().min(1),
    TASKMIGO_AUTH_CLIENT_SECRET: z.string().min(1),
    TASKMIGO_AUTH_SESSION_SECRET: z.string().min(32),
    TASKMIGO_AUTH_ALLOW_INSECURE_REQUESTS: z.stringbool(),
    TASKMIGO_AUTH_CALLBACK_PATH: z.string().min(1),
    TASKMIGO_AUTH_POST_LOGOUT_REDIRECT_PATH: z.string().min(1),
    TASKMIGO_AUTH_DEFAULT_RETURN_TO: z.string().min(1),
    TASKMIGO_AUTH_RETURN_TO_PARAMETER: z.string().min(1),
    TASKMIGO_AUTH_SCOPE: z.string().min(1),
    TASKMIGO_AUTH_OIDC_STATE_VERSION: z.string().min(1),
    TASKMIGO_AUTH_REFRESH_SKEW_MILLISECONDS: nonNegativeInteger,
    TASKMIGO_AUTH_COOKIE_VERSION: z.string().min(1),
    TASKMIGO_AUTH_COOKIE_HTTP_ONLY: z.stringbool(),
    TASKMIGO_AUTH_COOKIE_SAME_SITE: z.enum(["strict", "lax", "none"]),
    TASKMIGO_AUTH_COOKIE_SECURE: z.stringbool(),
    TASKMIGO_AUTH_COOKIE_PATH: z.string().min(1),
    TASKMIGO_AUTH_SESSION_COOKIE_NAME: z.string().min(1),
    TASKMIGO_AUTH_SESSION_COOKIE_AAD: z.string().min(1),
    TASKMIGO_AUTH_SESSION_MAX_AGE_SECONDS: positiveInteger,
    TASKMIGO_AUTH_TRANSACTION_COOKIE_NAME: z.string().min(1),
    TASKMIGO_AUTH_TRANSACTION_COOKIE_AAD: z.string().min(1),
    TASKMIGO_AUTH_TRANSACTION_MAX_AGE_SECONDS: positiveInteger,
  })
  .transform((environment) => {
    const cookieAttributes = Object.freeze({
      httpOnly: environment.TASKMIGO_AUTH_COOKIE_HTTP_ONLY,
      sameSite: environment.TASKMIGO_AUTH_COOKIE_SAME_SITE,
      secure: environment.TASKMIGO_AUTH_COOKIE_SECURE,
      path: environment.TASKMIGO_AUTH_COOKIE_PATH,
    });

    return Object.freeze({
      appUrl: environment.TASKMIGO_CLIENT_URL,
      auth: Object.freeze({
        issuer: environment.TASKMIGO_AUTH_ISSUER,
        clientId: environment.TASKMIGO_AUTH_CLIENT_ID,
        clientSecret: environment.TASKMIGO_AUTH_CLIENT_SECRET,
        sessionSecret: environment.TASKMIGO_AUTH_SESSION_SECRET,
        allowInsecureRequests: environment.TASKMIGO_AUTH_ALLOW_INSECURE_REQUESTS,
        callbackPath: environment.TASKMIGO_AUTH_CALLBACK_PATH,
        postLogoutRedirectPath: environment.TASKMIGO_AUTH_POST_LOGOUT_REDIRECT_PATH,
        defaultReturnTo: environment.TASKMIGO_AUTH_DEFAULT_RETURN_TO,
        returnToParameter: environment.TASKMIGO_AUTH_RETURN_TO_PARAMETER,
        scope: environment.TASKMIGO_AUTH_SCOPE,
        oidcStateVersion: environment.TASKMIGO_AUTH_OIDC_STATE_VERSION,
        refreshSkewMilliseconds: environment.TASKMIGO_AUTH_REFRESH_SKEW_MILLISECONDS,
        sessionCookie: Object.freeze({
          name: environment.TASKMIGO_AUTH_SESSION_COOKIE_NAME,
          additionalAuthenticatedData: environment.TASKMIGO_AUTH_SESSION_COOKIE_AAD,
          maxAge: environment.TASKMIGO_AUTH_SESSION_MAX_AGE_SECONDS,
        }),
        transactionCookie: Object.freeze({
          name: environment.TASKMIGO_AUTH_TRANSACTION_COOKIE_NAME,
          additionalAuthenticatedData: environment.TASKMIGO_AUTH_TRANSACTION_COOKIE_AAD,
          maxAge: environment.TASKMIGO_AUTH_TRANSACTION_MAX_AGE_SECONDS,
        }),
        cookie: Object.freeze({
          version: environment.TASKMIGO_AUTH_COOKIE_VERSION,
          attributes: cookieAttributes,
        }),
      }),
    });
  });

export type Config = z.output<typeof configSchema>;

export const parseConfig = (environment: unknown): Config => configSchema.parse(environment);

/** Reads and validates server-only configuration when the auth runtime is first created. */
export const getConfig = (): Config => parseConfig(process.env);
