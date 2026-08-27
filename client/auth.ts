import "server-only";

import { createAuth } from "@taskmigo/auth";
import { createNextAuth, type NextAuth } from "@taskmigo/auth/next";
import { createOpenIdAuthorizationClient } from "@taskmigo/auth/openid-client";
import { getConfig } from "@taskmigo/config/server";

const CLIENT_ID = "taskmigo-client";

let auth: NextAuth | undefined;

export function getAuth(): NextAuth {
  if (auth) return auth;

  const config = getConfig();
  const core = createAuth({
    appUrl: config.appUrl,
    authorizationClient: createOpenIdAuthorizationClient({
      issuer: config.issuer,
      clientId: CLIENT_ID,
      clientSecret: config.clientSecret,
      allowInsecureRequests: config.issuer.protocol === "http:",
    }),
  });

  auth = createNextAuth({ auth: core, sessionSecret: config.sessionSecret });
  return auth;
}
