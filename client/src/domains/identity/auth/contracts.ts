export interface User {
  id: string;
  name?: string;
}

export interface Session {
  user: User;
  expiresAt: number;
  authorizationState: string;
}

export interface AuthorizedSession {
  session: Session;
  accessToken: string;
}

export interface AuthorizationTransaction {
  state: string;
  returnTo: string;
}

export interface AuthorizationClient {
  begin(redirectUri: URL): Promise<{ redirectTo: URL; state: string }>;
  complete(callbackUrl: URL, state: string): Promise<Session>;
  renew(session: Session): Promise<Session>;
  getAccessToken(session: Session): string;
  end(session: Session, postLogoutRedirectUri: URL): Promise<URL>;
}

export interface AuthManager {
  beginSignIn(returnTo?: string | null): Promise<{ redirectTo: URL; transaction: AuthorizationTransaction }>;
  completeSignIn(
    callbackUrl: URL,
    transaction: AuthorizationTransaction,
  ): Promise<{ redirectTo: URL; session: Session }>;
  renew(session: Session): Promise<Session>;
  authorize(session: Session): Promise<AuthorizedSession>;
  signOut(session?: Session): Promise<URL>;
}

export class SessionRenewalError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = new.target.name;
  }
}

export class InvalidSessionError extends SessionRenewalError {}
