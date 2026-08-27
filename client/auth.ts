import "server-only";

import { DefaultAuthManager } from "@taskmigo/auth";
import { NextAuth } from "@taskmigo/auth/next";
import { OpenIdAuthorizationClient } from "@taskmigo/auth/openid-client";
import { getConfig } from "@taskmigo/config/server";

const CLIENT_ID = "taskmigo-client";

let auth: NextAuth | undefined;

export function getAuth(): NextAuth {
  if (auth) return auth;

  const config = getConfig();
  const authorizationClient = new OpenIdAuthorizationClient({
    issuer: config.issuer,
    clientId: CLIENT_ID,
    clientSecret: config.clientSecret,
    allowInsecureRequests: config.issuer.protocol === "http:",
  });
  const authManager = new DefaultAuthManager(authorizationClient, { appUrl: config.appUrl });

  auth = new NextAuth(authManager, config.sessionSecret);
  return auth;
}
