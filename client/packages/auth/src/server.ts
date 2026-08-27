import "server-only";

import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";

import { createSealedCookie } from "./sealed-cookie";
import { safeReturnTo } from "./return-to";
import { authTransactionSchema, sessionSchema, type AuthConfig, type OAuthTokens, type Session } from "./types";

const SESSION_COOKIE = "taskmigo_session";
const TRANSACTION_COOKIE = "taskmigo_auth_transaction";
const SESSION_MAX_AGE_SECONDS = 8 * 60 * 60;
const TRANSACTION_MAX_AGE_SECONDS = 10 * 60;
const REFRESH_SKEW_MILLISECONDS = 30_000;
const DEFAULT_RETURN_TO = "/account";
const CALLBACK_PATH = "/api/auth/callback";

export interface PublicSession {
  authenticated: true;
  user: {
    subject: string;
    name?: string;
  };
}

export interface Auth {
  login(request: NextRequest): Promise<NextResponse>;
  callback(request: NextRequest): Promise<NextResponse>;
  logout(request: NextRequest): Promise<NextResponse>;
  session(request: NextRequest): Promise<NextResponse>;
  getSession(): Promise<Session | undefined>;
}

function createSession(tokens: OAuthTokens): Session {
  if (!tokens.subject) throw new Error("Authorization provider did not return an authenticated subject");
  if (!tokens.refreshToken) throw new Error("Authorization provider did not return a refresh token");
  if (!tokens.idToken) throw new Error("Authorization provider did not return an ID token");

  return {
    subject: tokens.subject,
    name: tokens.name,
    accessToken: tokens.accessToken,
    accessTokenExpiresAt: Date.now() + tokens.expiresIn * 1000,
    refreshToken: tokens.refreshToken,
    idToken: tokens.idToken,
  };
}

function publicSession(session: Session): PublicSession {
  return {
    authenticated: true,
    user: {
      subject: session.subject,
      name: session.name,
    },
  };
}

function noStore(response: NextResponse): NextResponse {
  response.headers.set("Cache-Control", "no-store");
  return response;
}

export function createAuth(config: AuthConfig): Auth {
  const callbackUrl = new URL(CALLBACK_PATH, config.appUrl);
  const postLogoutRedirectUrl = new URL("/", config.appUrl);
  const defaultReturnTo = config.defaultReturnTo ?? DEFAULT_RETURN_TO;
  const sessionCookie = createSealedCookie({
    name: SESSION_COOKIE,
    purpose: "session",
    schema: sessionSchema,
    secret: config.sessionSecret,
    maxAge: SESSION_MAX_AGE_SECONDS,
  });
  const transactionCookie = createSealedCookie({
    name: TRANSACTION_COOKIE,
    purpose: "transaction",
    schema: authTransactionSchema,
    secret: config.sessionSecret,
    maxAge: TRANSACTION_MAX_AGE_SECONDS,
  });

  async function freshSession(session: Session): Promise<Session> {
    if (session.accessTokenExpiresAt > Date.now() + REFRESH_SKEW_MILLISECONDS) return session;

    const tokens = await config.provider.refresh(session.refreshToken);
    if (tokens.subject !== undefined && tokens.subject !== session.subject) {
      throw new Error("Refreshed identity changed the authenticated subject");
    }

    return {
      subject: session.subject,
      name: tokens.name ?? session.name,
      accessToken: tokens.accessToken,
      accessTokenExpiresAt: Date.now() + tokens.expiresIn * 1000,
      refreshToken: tokens.refreshToken ?? session.refreshToken,
      idToken: tokens.idToken ?? session.idToken,
    };
  }

  return {
    async login(request) {
      try {
        const authorization = await config.provider.beginLogin(callbackUrl);
        const response = NextResponse.redirect(authorization.url);
        response.cookies.set(
          transactionCookie.name,
          transactionCookie.encode({
            providerState: authorization.state,
            returnTo: safeReturnTo(request.nextUrl.searchParams.get("returnTo"), defaultReturnTo),
          }),
          transactionCookie.options,
        );
        return response;
      } catch {
        return NextResponse.json({ error: "Browser authentication is not configured correctly" }, { status: 500 });
      }
    },

    async callback(request) {
      const transaction = transactionCookie.decode(request.cookies.get(transactionCookie.name)?.value);
      if (!transaction) {
        const response = NextResponse.json({ error: "Login transaction is missing or expired" }, { status: 400 });
        response.cookies.delete(transactionCookie.name);
        return response;
      }

      try {
        const tokens = await config.provider.completeLogin({
          callbackUrl: new URL(request.url),
          state: transaction.providerState,
        });
        const response = NextResponse.redirect(new URL(transaction.returnTo, config.appUrl));
        response.cookies.set(sessionCookie.name, sessionCookie.encode(createSession(tokens)), sessionCookie.options);
        response.cookies.delete(transactionCookie.name);
        return response;
      } catch {
        const response = NextResponse.json({ error: "Login callback could not be validated" }, { status: 400 });
        response.cookies.delete(transactionCookie.name);
        return response;
      }
    },

    async logout(request) {
      const session = sessionCookie.decode(request.cookies.get(sessionCookie.name)?.value);
      let redirectTarget = postLogoutRedirectUrl;

      if (session) {
        try {
          redirectTarget = await config.provider.endSession({ idToken: session.idToken, postLogoutRedirectUrl });
        } catch {
          redirectTarget = postLogoutRedirectUrl;
        }
      }

      const response = NextResponse.redirect(redirectTarget, 303);
      response.cookies.delete(sessionCookie.name);
      response.cookies.delete(transactionCookie.name);
      return response;
    },

    async session(request) {
      const session = sessionCookie.decode(request.cookies.get(sessionCookie.name)?.value);
      if (!session) return noStore(NextResponse.json({ authenticated: false }));

      try {
        const current = await freshSession(session);
        const response = noStore(NextResponse.json(publicSession(current)));
        if (current !== session)
          response.cookies.set(sessionCookie.name, sessionCookie.encode(current), sessionCookie.options);
        return response;
      } catch {
        const response = noStore(NextResponse.json({ authenticated: false }));
        response.cookies.delete(sessionCookie.name);
        return response;
      }
    },

    async getSession() {
      const store = await cookies();
      return sessionCookie.decode(store.get(sessionCookie.name)?.value);
    },
  };
}

export type { AuthConfig, AuthorizationProvider, OAuthTokens, Session } from "./types";
