import "server-only";

import { z } from "zod";

const LOOPBACK_HOSTNAMES = new Set(["localhost", "127.0.0.1", "[::1]"]);
const urlSchema = z.url().transform((value) => new URL(value));

const configSchema = z
  .object({
    TASKMIGO_AUTH_CLIENT_SECRET: z.string().min(1),
    TASKMIGO_AUTH_SESSION_SECRET: z.string().min(32),
    TASKMIGO_AUTH_ISSUER: urlSchema.default(new URL("http://localhost:8080")),
    TASKMIGO_CLIENT_URL: urlSchema.default(new URL("http://localhost:3000")),
  })
  .refine(
    ({ TASKMIGO_AUTH_ISSUER: issuer }) =>
      issuer.protocol === "https:" || (issuer.protocol === "http:" && LOOPBACK_HOSTNAMES.has(issuer.hostname)),
    { message: "OAuth issuer must use HTTPS except for loopback development hosts", path: ["TASKMIGO_AUTH_ISSUER"] },
  )
  .transform(
    ({
      TASKMIGO_AUTH_ISSUER: issuer,
      TASKMIGO_AUTH_CLIENT_SECRET: clientSecret,
      TASKMIGO_AUTH_SESSION_SECRET: sessionSecret,
      TASKMIGO_CLIENT_URL: appUrl,
    }) => Object.freeze({ issuer, clientSecret, sessionSecret, appUrl }),
  );

export type Config = z.output<typeof configSchema>;

let config: Config | undefined;

export const parseConfig = (environment: unknown): Config => configSchema.parse(environment);

/** Lazily validates server-only environment variables so builds do not require deployment secrets. */
export const getConfig = (): Config => (config ??= parseConfig(process.env));
