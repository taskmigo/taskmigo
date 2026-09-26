import type { AuthManager, Session } from "@taskmigo/auth";
import type { CookieState } from "@taskmigo/foundation/state";
import type { NextRequest, NextResponse } from "next/server";

import type { BrowserApiAuthorization, BrowserApiAuthorizer } from "../proxy";

type SessionStore = Pick<CookieState<Session>, "read" | "write" | "clear">;

class SessionBrowserApiAuthorization implements BrowserApiAuthorization {
  readonly accessToken: string;
  readonly #previousSession: Session;
  readonly #currentSession: Session;
  readonly #sessions: SessionStore;

  constructor(accessToken: string, previousSession: Session, currentSession: Session, sessions: SessionStore) {
    this.accessToken = accessToken;
    this.#previousSession = previousSession;
    this.#currentSession = currentSession;
    this.#sessions = sessions;
  }

  persist(response: NextResponse): void {
    if (this.#currentSession !== this.#previousSession) {
      this.#sessions.write(response.cookies, this.#currentSession);
    }
  }
}

export class SessionBrowserApiAuthorizer implements BrowserApiAuthorizer {
  readonly #manager: Pick<AuthManager, "authorize">;
  readonly #sessions: SessionStore;

  constructor(manager: Pick<AuthManager, "authorize">, sessions: SessionStore) {
    this.#manager = manager;
    this.#sessions = sessions;
  }

  async authorize(request: NextRequest): Promise<BrowserApiAuthorization | undefined> {
    const previousSession = this.#sessions.read(request.cookies);
    if (!previousSession) {
      return;
    }

    try {
      const authorized = await this.#manager.authorize(previousSession);
      return new SessionBrowserApiAuthorization(
        authorized.accessToken,
        previousSession,
        authorized.session,
        this.#sessions,
      );
    } catch {
      return;
    }
  }

  clear(response: NextResponse): void {
    this.#sessions.clear(response.cookies);
  }
}
