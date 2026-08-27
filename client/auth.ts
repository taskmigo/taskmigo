import "server-only";

import { createOpenIdClientProvider } from "@taskmigo/auth/providers/openid-client";
import { createAuth, type Auth } from "@taskmigo/auth/server";
import { getConfig } from "@taskmigo/config/server";

const CLIENT_ID = "taskmigo-client";

let auth: Auth | undefined;

export function getAuth(): Auth {
  if (auth) return auth;

  const config = getConfig();
  auth = createAuth({
    appUrl: config.appUrl,
    sessionSecret: config.sessionSecret,
    provider: createOpenIdClientProvider({
      issuer: config.issuer,
      clientId: CLIENT_ID,
      clientSecret: config.clientSecret,
      allowInsecureRequests: config.issuer.protocol === "http:",
    }),
  });
  return auth;
}
