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
  appUrl: URL;
  defaultReturnTo?: string;
}

const DEFAULT_RETURN_TO = "/account";
const CALLBACK_PATH = "/api/auth/callback";
const REFRESH_SKEW_MILLISECONDS = 30_000;

function safeReturnTo(value: string | null | undefined, appUrl: URL, fallback: string): string {
  if (!value?.startsWith("/")) return fallback;

  try {
    const resolved = new URL(value, appUrl);
    return resolved.origin === appUrl.origin ? `${resolved.pathname}${resolved.search}${resolved.hash}` : fallback;
  } catch {
    return fallback;
  }
}

export class DefaultAuthManager implements AuthManager {
  private readonly appUrl: URL;
  private readonly callbackUrl: URL;
  private readonly postLogoutRedirectUrl: URL;
  private readonly defaultReturnTo: string;

  constructor(
    private readonly client: AuthorizationClient,
    { appUrl, defaultReturnTo = DEFAULT_RETURN_TO }: AuthManagerOptions,
  ) {
    this.appUrl = new URL(appUrl);
    this.callbackUrl = new URL(CALLBACK_PATH, this.appUrl);
    this.postLogoutRedirectUrl = new URL("/", this.appUrl);
    this.defaultReturnTo = defaultReturnTo;
  }

  async beginSignIn(returnTo?: string | null) {
    const { redirectTo, state } = await this.client.begin(this.callbackUrl);
    return {
      redirectTo,
      transaction: { state, returnTo: safeReturnTo(returnTo, this.appUrl, this.defaultReturnTo) },
    };
  }

  async completeSignIn(callbackUrl: URL, transaction: AuthorizationTransaction) {
    return {
      session: await this.client.complete(callbackUrl, transaction.state),
      redirectTo: new URL(transaction.returnTo, this.appUrl),
    };
  }

  async renew(session: Session): Promise<Session> {
    if (session.expiresAt > Date.now() + REFRESH_SKEW_MILLISECONDS) return session;

    const renewed = await this.client.renew(session);
    if (renewed.user.id !== session.user.id) throw new Error("Authorization subject changed during renewal");
    return renewed;
  }

  async signOut(session?: Session): Promise<URL> {
    if (!session) return this.postLogoutRedirectUrl;
    return this.client.end(session, this.postLogoutRedirectUrl).catch(() => this.postLogoutRedirectUrl);
  }
}
