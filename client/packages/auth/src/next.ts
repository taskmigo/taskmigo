import "server-only";

import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import { z } from "zod";

import type { AuthManager, AuthorizationTransaction, Session } from "./core";
import { createSealedCookie, type SealedCookie } from "./sealed-cookie";

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

export interface NextAuthHandlers {
  login(request: NextRequest): Promise<NextResponse>;
  callback(request: NextRequest): Promise<NextResponse>;
  logout(request: NextRequest): Promise<NextResponse>;
  session(request: NextRequest): Promise<NextResponse>;
}

export class NextAuth {
  readonly handlers: NextAuthHandlers;

  private readonly sessionCookie: SealedCookie<Session>;
  private readonly transactionCookie: SealedCookie<AuthorizationTransaction>;

  constructor(
    private readonly authManager: AuthManager,
    sessionSecret: string,
  ) {
    this.sessionCookie = createSealedCookie<Session>({
      name: SESSION_COOKIE,
      purpose: "session",
      schema: sessionSchema,
      secret: sessionSecret,
      maxAge: SESSION_MAX_AGE_SECONDS,
    });
    this.transactionCookie = createSealedCookie<AuthorizationTransaction>({
      name: TRANSACTION_COOKIE,
      purpose: "transaction",
      schema: transactionSchema,
      secret: sessionSecret,
      maxAge: TRANSACTION_MAX_AGE_SECONDS,
    });
    this.handlers = {
      login: (request) => this.login(request),
      callback: (request) => this.callback(request),
      logout: (request) => this.logout(request),
      session: (request) => this.session(request),
    };
  }

  async getSession(): Promise<Session | undefined> {
    const store = await cookies();
    const session = this.sessionCookie.decode(store.get(this.sessionCookie.name)?.value);
    if (!session) return;

    try {
      return await this.authManager.currentSession(session);
    } catch {
      return;
    }
  }

  private async login(request: NextRequest): Promise<NextResponse> {
    try {
      const { redirectTo, transaction } = await this.authManager.beginSignIn(
        request.nextUrl.searchParams.get("returnTo"),
      );
      const response = NextResponse.redirect(redirectTo);
      response.cookies.set(
        this.transactionCookie.name,
        this.transactionCookie.encode(transaction),
        this.transactionCookie.options,
      );
      return response;
    } catch {
      return NextResponse.json({ error: "Browser authentication is not configured correctly" }, { status: 500 });
    }
  }

  private async callback(request: NextRequest): Promise<NextResponse> {
    const transaction = this.transactionCookie.decode(request.cookies.get(this.transactionCookie.name)?.value);
    if (!transaction) {
      const response = NextResponse.json({ error: "Login transaction is missing or expired" }, { status: 400 });
      response.cookies.delete(this.transactionCookie.name);
      return response;
    }

    try {
      const { redirectTo, session } = await this.authManager.completeSignIn(new URL(request.url), transaction);
      const response = NextResponse.redirect(redirectTo);
      response.cookies.set(this.sessionCookie.name, this.sessionCookie.encode(session), this.sessionCookie.options);
      response.cookies.delete(this.transactionCookie.name);
      return response;
    } catch {
      const response = NextResponse.json({ error: "Login callback could not be validated" }, { status: 400 });
      response.cookies.delete(this.transactionCookie.name);
      return response;
    }
  }

  private async logout(request: NextRequest): Promise<NextResponse> {
    const session = this.sessionCookie.decode(request.cookies.get(this.sessionCookie.name)?.value);
    const response = NextResponse.redirect(await this.authManager.signOut(session), 303);
    response.cookies.delete(this.sessionCookie.name);
    response.cookies.delete(this.transactionCookie.name);
    return response;
  }

  private async session(request: NextRequest): Promise<NextResponse> {
    const session = this.sessionCookie.decode(request.cookies.get(this.sessionCookie.name)?.value);
    if (!session) return noStore(NextResponse.json({ authenticated: false }));

    try {
      const current = await this.authManager.currentSession(session);
      const response = noStore(NextResponse.json(this.authManager.publicSession(current)));
      if (current !== session) {
        response.cookies.set(this.sessionCookie.name, this.sessionCookie.encode(current), this.sessionCookie.options);
      }
      return response;
    } catch {
      const response = noStore(NextResponse.json({ authenticated: false }));
      response.cookies.delete(this.sessionCookie.name);
      return response;
    }
  }
}
