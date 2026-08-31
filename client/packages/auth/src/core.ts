import { systemClock, type Clock } from "@taskmigo/foundation/runtime";

import { AuthNavigation } from "./navigation";

export { AuthNavigation, type AuthNavigationOptions } from "./navigation";

export interface User {
  id: string;
  name?: string;
}

export interface Session {
  user: User;
  expiresAt: number;
  authorizationState: string;
}

export interface AuthorizationTransaction {
  state: string;
  returnTo: string;
}

export interface AuthorizationClient {
  begin(redirectUri: URL): Promise<{ redirectTo: URL; state: string }>;
  complete(callbackUrl: URL, state: string): Promise<Session>;
  renew(session: Session): Promise<Session>;
  end(session: Session, postLogoutRedirectUri: URL): Promise<URL>;
}

export interface AuthManager {
  beginSignIn(returnTo?: string | null): Promise<{ redirectTo: URL; transaction: AuthorizationTransaction }>;
  completeSignIn(
    callbackUrl: URL,
    transaction: AuthorizationTransaction,
  ): Promise<{ redirectTo: URL; session: Session }>;
  renew(session: Session): Promise<Session>;
  signOut(session?: Session): Promise<URL>;
}

export interface AuthManagerOptions {
  refreshSkewMilliseconds: number;
  clock?: Clock;
}

export class DefaultAuthManager implements AuthManager {
  readonly #client: AuthorizationClient;
  readonly #navigation: AuthNavigation;
  readonly #refreshSkewMilliseconds: number;
  readonly #clock: Clock;

  constructor(client: AuthorizationClient, navigation: AuthNavigation, options: AuthManagerOptions) {
    this.#client = client;
    this.#navigation = navigation;
    this.#refreshSkewMilliseconds = options.refreshSkewMilliseconds;
    this.#clock = options.clock ?? systemClock;
  }

  async beginSignIn(returnTo?: string | null) {
    const { redirectTo, state } = await this.#client.begin(this.#navigation.callbackUrl);
    return {
      redirectTo,
      transaction: { state, returnTo: this.#navigation.resolveReturnTo(returnTo) },
    };
  }

  async completeSignIn(callbackUrl: URL, transaction: AuthorizationTransaction) {
    // Reverse proxies may expose an internal request origin here; the token exchange must reuse the registered URI.
    const canonicalCallbackUrl = this.#navigation.callbackUrl;
    canonicalCallbackUrl.search = callbackUrl.search;

    return {
      session: await this.#client.complete(canonicalCallbackUrl, transaction.state),
      redirectTo: this.#navigation.returnToUrl(transaction.returnTo),
    };
  }

  async renew(session: Session): Promise<Session> {
    if (session.expiresAt > this.#clock() + this.#refreshSkewMilliseconds) return session;

    const renewed = await this.#client.renew(session);
    if (renewed.user.id !== session.user.id) throw new Error("Authorization subject changed during renewal");
    return renewed;
  }

  async signOut(session?: Session): Promise<URL> {
    const redirectUrl = this.#navigation.postLogoutRedirectUrl;
    if (!session) return redirectUrl;
    return this.#client.end(session, redirectUrl).catch(() => redirectUrl);
  }
}
