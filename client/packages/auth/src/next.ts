import "server-only";

import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import { z } from "zod";

import type { AuthManager, AuthorizationTransaction, Session } from "./core";
import { SealedCookie } from "./sealed-cookie";

class CookieState<T> {
  constructor(private readonly cookie: SealedCookie<T>) {}

  read(request: NextRequest): T | undefined {
    return this.cookie.decode(request.cookies.get(this.cookie.name)?.value);
  }

  write(response: NextResponse, value: T): void {
    response.cookies.set(this.cookie.name, this.cookie.encode(value), this.cookie.options);
  }

  clear(response: NextResponse): void {
    response.cookies.delete(this.cookie.name);
  }
}

export class NextAuth {
  private static readonly SESSION_COOKIE = "taskmigo_session";
  private static readonly TRANSACTION_COOKIE = "taskmigo_auth_transaction";
  private static readonly SESSION_MAX_AGE_SECONDS = 8 * 60 * 60;
  private static readonly TRANSACTION_MAX_AGE_SECONDS = 10 * 60;
  private static readonly TRANSACTION_SCHEMA = z.object({ state: z.string().min(1), returnTo: z.string().min(1) });
  private static readonly SESSION_SCHEMA = z.object({
    user: z.object({ id: z.string().min(1), name: z.string().min(1).optional() }),
    expiresAt: z.number(),
    authorizationState: z.string().min(1),
  });

  private readonly sessionCookie: SealedCookie<Session>;
  private readonly sessions: CookieState<Session>;
  private readonly transactions: CookieState<AuthorizationTransaction>;

  constructor(
    private readonly manager: AuthManager,
    sessionSecret: string,
  ) {
    this.sessionCookie = new SealedCookie({
      name: NextAuth.SESSION_COOKIE,
      purpose: "session",
      schema: NextAuth.SESSION_SCHEMA,
      secret: sessionSecret,
      maxAge: NextAuth.SESSION_MAX_AGE_SECONDS,
    });
    this.sessions = new CookieState(this.sessionCookie);
    this.transactions = new CookieState(
      new SealedCookie({
        name: NextAuth.TRANSACTION_COOKIE,
        purpose: "transaction",
        schema: NextAuth.TRANSACTION_SCHEMA,
        secret: sessionSecret,
        maxAge: NextAuth.TRANSACTION_MAX_AGE_SECONDS,
      }),
    );
  }

  readonly login = async (request: NextRequest): Promise<NextResponse> => {
    try {
      const { redirectTo, transaction } = await this.manager.beginSignIn(request.nextUrl.searchParams.get("returnTo"));
      const response = NextResponse.redirect(redirectTo);
      this.transactions.write(response, transaction);
      return response;
    } catch {
      return NextResponse.json({ error: "Browser authentication is not configured correctly" }, { status: 500 });
    }
  };

  readonly callback = async (request: NextRequest): Promise<NextResponse> => {
    const transaction = this.transactions.read(request);
    if (!transaction) {
      const response = NextResponse.json({ error: "Login transaction is missing or expired" }, { status: 400 });
      this.transactions.clear(response);
      return response;
    }

    try {
      const { redirectTo, session } = await this.manager.completeSignIn(new URL(request.url), transaction);
      const response = NextResponse.redirect(redirectTo);
      this.sessions.write(response, session);
      this.transactions.clear(response);
      return response;
    } catch {
      const response = NextResponse.json({ error: "Login callback could not be validated" }, { status: 400 });
      this.transactions.clear(response);
      return response;
    }
  };

  readonly logout = async (request: NextRequest): Promise<NextResponse> => {
    const response = NextResponse.redirect(await this.manager.signOut(this.sessions.read(request)), 303);
    this.sessions.clear(response);
    this.transactions.clear(response);
    return response;
  };

  readonly session = async (request: NextRequest): Promise<NextResponse> => {
    const session = this.sessions.read(request);
    if (!session) return NextAuth.noStore(NextResponse.json({ authenticated: false }));

    try {
      const current = await this.manager.renew(session);
      const response = NextAuth.noStore(NextResponse.json({ authenticated: true, user: current.user }));
      if (current !== session) this.sessions.write(response, current);
      return response;
    } catch {
      const response = NextAuth.noStore(NextResponse.json({ authenticated: false }));
      this.sessions.clear(response);
      return response;
    }
  };

  async getSession(): Promise<Session | undefined> {
    const store = await cookies();
    return this.sessionCookie.decode(store.get(this.sessionCookie.name)?.value);
  }

  private static noStore(response: NextResponse): NextResponse {
    response.headers.set("Cache-Control", "no-store");
    return response;
  }
}
