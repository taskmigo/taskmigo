import "server-only";

import { AuthNavigation, DefaultAuthManager } from "@taskmigo/auth";
import { NextAuth } from "@taskmigo/auth/next";
import { OpenIdAuthorizationClient } from "@taskmigo/auth/openid-client";
import { getConfig } from "@taskmigo/config/server";

class AuthRuntime {
  private static readonly CLIENT_ID = "taskmigo-client";

  private auth?: NextAuth;

  get(): NextAuth {
    return (this.auth ??= this.create());
  }

  private create(): NextAuth {
    const config = getConfig();
    const authorizationClient = new OpenIdAuthorizationClient({
      issuer: config.issuer,
      clientId: AuthRuntime.CLIENT_ID,
      clientSecret: config.clientSecret,
      allowInsecureRequests: config.issuer.protocol === "http:",
    });
    const navigation = new AuthNavigation(config.appUrl);
    const authManager = new DefaultAuthManager(authorizationClient, navigation);

    return new NextAuth(authManager, config.sessionSecret);
  }
}

const authRuntime = new AuthRuntime();

export function getAuth(): NextAuth {
  return authRuntime.get();
}
