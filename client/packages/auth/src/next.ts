import "server-only";

import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import { z } from "zod";

import type { Auth, AuthorizationTransaction, Session } from "./core";
import { createSealedCookie } from "./sealed-cookie";

const SESSION_COOKIE = "taskmigo_session";
const TRANSACTION_COOKIE = "taskmigo_auth_transaction";
const SESSION_MAX_AGE_SECONDS = 8 * 60 * 60;
const TRANSACTION_MAX_AGE_SECONDS = 10 * 60;

const transactionSchema = z.object({
  state: z.string().min(1),
  returnTo: z.string().min(1),
});

const sessionSchema = z.object({
  user: z.object({
    id: z.string().min(1),
    name: z.string().min(1).optional(),
  }),
  expiresAt: z.number(),
  authorizationState: z.string().min(1),
});

function noStore(response: NextResponse): NextResponse {
  response.headers.set("Cache-Control", "no-store");
  return response;
}

export interface NextAuth {
  readonly handlers: {
    login(request: NextRequest): Promise<NextResponse>;
    callback(request: NextRequest): Promise<NextResponse>;
    logout(request: NextRequest): Promise<NextResponse>;
    session(request: NextRequest): Promise<NextResponse>;
  };
  getSession(): Promise<Session | undefined>;
}

export function createNextAuth(input: { auth: Auth; sessionSecret: string }): NextAuth {
  const sessionCookie = createSealedCookie<Session>({
    name: SESSION_COOKIE,
    purpose: "session",
    schema: sessionSchema,
    secret: input.sessionSecret,
    maxAge: SESSION_MAX_AGE_SECONDS,
  });
  const transactionCookie = createSealedCookie<AuthorizationTransaction>({
    name: TRANSACTION_COOKIE,
    purpose: "transaction",
    schema: transactionSchema,
    secret: input.sessionSecret,
    maxAge: TRANSACTION_MAX_AGE_SECONDS,
  });

  return {
    handlers: {
      async login(request) {
        try {
          const { redirectTo, transaction } = await input.auth.beginSignIn(request.nextUrl.searchParams.get("returnTo"));
          const response = NextResponse.redirect(redirectTo);
          response.cookies.set(transactionCookie.name, transactionCookie.encode(transaction), transactionCookie.options);
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
          const { redirectTo, session } = await input.auth.completeSignIn(new URL(request.url), transaction);
          const response = NextResponse.redirect(redirectTo);
          response.cookies.set(sessionCookie.name, sessionCookie.encode(session), sessionCookie.options);
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
        const response = NextResponse.redirect(await input.auth.signOut(session), 303);
        response.cookies.delete(sessionCookie.name);
        response.cookies.delete(transactionCookie.name);
        return response;
      },

      async session(request) {
        const session = sessionCookie.decode(request.cookies.get(sessionCookie.name)?.value);
        if (!session) return noStore(NextResponse.json({ authenticated: false }));

        try {
          const current = await input.auth.currentSession(session);
          const response = noStore(NextResponse.json(input.auth.publicSession(current)));
          if (current !== session)
            response.cookies.set(sessionCookie.name, sessionCookie.encode(current), sessionCookie.options);
          return response;
        } catch {
          const response = noStore(NextResponse.json({ authenticated: false }));
          response.cookies.delete(sessionCookie.name);
          return response;
        }
      },
    },

    async getSession() {
      const store = await cookies();
      const session = sessionCookie.decode(store.get(sessionCookie.name)?.value);
      if (!session) return;

      try {
        return await input.auth.currentSession(session);
      } catch {
        return;
      }
    },
  };
}
