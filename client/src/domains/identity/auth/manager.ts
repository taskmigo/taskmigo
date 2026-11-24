import type { AuthNavigation } from "./navigation";
import {
  InvalidSessionError,
  SessionRenewalError,
  type AuthManager,
  type AuthorizationClient,
  type AuthorizationTransaction,
  type AuthorizedSession,
  type Session,
} from "./contracts";

type Clock = () => number;

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
    this.#clock = options.clock ?? (() => Date.now());
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
    if (session.expiresAt > this.#clock() + this.#refreshSkewMilliseconds) {
      this.#client.getAccessToken(session);
      return session;
    }

    let renewed: Session;
    try {
      renewed = await this.#client.renew(session);
    } catch (error) {
      if (error instanceof SessionRenewalError) {
        throw error;
      }
      throw new SessionRenewalError("Authorization session renewal failed", { cause: error });
    }
    if (renewed.user.id !== session.user.id) {
      throw new InvalidSessionError("Authorization subject changed during renewal");
    }
    return renewed;
  }

  async authorize(session: Session): Promise<AuthorizedSession> {
    const current = await this.renew(session);
    return { session: current, accessToken: this.#client.getAccessToken(current) };
  }

  async signOut(session?: Session): Promise<URL> {
    const redirectUrl = this.#navigation.postLogoutRedirectUrl;
    if (!session) {
      return redirectUrl;
    }
    return this.#client.end(session, redirectUrl).catch(() => redirectUrl);
  }
}
