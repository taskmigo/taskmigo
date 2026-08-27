import "server-only";

import { z } from "zod";

const urlSchema = z.url().transform((value) => new URL(value));

const configSchema = z
  .object({
    TASKMIGO_AUTH_CLIENT_SECRET: z.string().min(1),
    TASKMIGO_AUTH_SESSION_SECRET: z.string().min(32),
    TASKMIGO_AUTH_ISSUER: urlSchema,
    TASKMIGO_AUTH_ALLOW_INSECURE_REQUESTS: z.stringbool(),
    TASKMIGO_CLIENT_URL: urlSchema,
  })
  .transform(
    ({
      TASKMIGO_AUTH_ISSUER: issuer,
      TASKMIGO_AUTH_CLIENT_SECRET: clientSecret,
      TASKMIGO_AUTH_SESSION_SECRET: sessionSecret,
      TASKMIGO_AUTH_ALLOW_INSECURE_REQUESTS: allowInsecureRequests,
      TASKMIGO_CLIENT_URL: appUrl,
    }) => Object.freeze({ issuer, clientSecret, sessionSecret, allowInsecureRequests, appUrl }),
  );

export type Config = z.output<typeof configSchema>;

let config: Config | undefined;

export const parseConfig = (environment: unknown): Config => configSchema.parse(environment);

/** Lazily validates server-only environment variables so builds do not require deployment secrets. */
export const getConfig = (): Config => (config ??= parseConfig(process.env));
