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
  complete(input: { callbackUrl: URL; state: string }): Promise<Session>;
  renew(session: Session): Promise<Session>;
  end(session: Session, postLogoutRedirectUri: URL): Promise<URL>;
}

export interface PublicSession {
  authenticated: true;
  user: User;
}

export interface AuthManager {
  beginSignIn(returnTo?: string | null): Promise<{ redirectTo: URL; transaction: AuthorizationTransaction }>;
  completeSignIn(
    callbackUrl: URL,
    transaction: AuthorizationTransaction,
  ): Promise<{ redirectTo: URL; session: Session }>;
  currentSession(session: Session): Promise<Session>;
  publicSession(session: Session): PublicSession;
  signOut(session?: Session): Promise<URL>;
}

export interface AuthManagerOptions {
  appUrl: URL;
  defaultReturnTo?: string;
}

const RETURN_TO_BASE = new URL("https://taskmigo.invalid");
const DEFAULT_RETURN_TO = "/account";
const CALLBACK_PATH = "/api/auth/callback";
const REFRESH_SKEW_MILLISECONDS = 30_000;

function safeReturnTo(value: string | null | undefined, fallback: string): string {
  if (!value?.startsWith("/")) return fallback;

  try {
    return new URL(value, RETURN_TO_BASE).origin === RETURN_TO_BASE.origin ? value : fallback;
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
    private readonly authorizationClient: AuthorizationClient,
    options: AuthManagerOptions,
  ) {
    this.appUrl = new URL(options.appUrl);
    this.callbackUrl = new URL(CALLBACK_PATH, this.appUrl);
    this.postLogoutRedirectUrl = new URL("/", this.appUrl);
    this.defaultReturnTo = options.defaultReturnTo ?? DEFAULT_RETURN_TO;
  }

  async beginSignIn(returnTo?: string | null) {
    const authorization = await this.authorizationClient.begin(this.callbackUrl);
    return {
      redirectTo: authorization.redirectTo,
      transaction: {
        state: authorization.state,
        returnTo: safeReturnTo(returnTo, this.defaultReturnTo),
      },
    };
  }

  async completeSignIn(callbackUrl: URL, transaction: AuthorizationTransaction) {
    const session = await this.authorizationClient.complete({ callbackUrl, state: transaction.state });
    return { redirectTo: new URL(transaction.returnTo, this.appUrl), session };
  }

  async currentSession(session: Session): Promise<Session> {
    if (session.expiresAt > Date.now() + REFRESH_SKEW_MILLISECONDS) return session;

    const renewed = await this.authorizationClient.renew(session);
    if (renewed.user.id !== session.user.id) {
      throw new Error("Renewed identity changed the authenticated subject");
    }
    return renewed;
  }

  publicSession(session: Session): PublicSession {
    return { authenticated: true, user: session.user };
  }

  async signOut(session?: Session): Promise<URL> {
    if (!session) return this.postLogoutRedirectUrl;

    try {
      return await this.authorizationClient.end(session, this.postLogoutRedirectUrl);
    } catch {
      return this.postLogoutRedirectUrl;
    }
  }
}
