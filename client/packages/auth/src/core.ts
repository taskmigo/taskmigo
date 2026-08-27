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

export interface Auth {
  beginSignIn(returnTo?: string | null): Promise<{ redirectTo: URL; transaction: AuthorizationTransaction }>;
  completeSignIn(callbackUrl: URL, transaction: AuthorizationTransaction): Promise<{ redirectTo: URL; session: Session }>;
  currentSession(session: Session): Promise<Session>;
  publicSession(session: Session): PublicSession;
  signOut(session?: Session): Promise<URL>;
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

export function createAuth(config: {
  appUrl: URL;
  authorizationClient: AuthorizationClient;
  defaultReturnTo?: string;
}): Auth {
  const callbackUrl = new URL(CALLBACK_PATH, config.appUrl);
  const postLogoutRedirectUrl = new URL("/", config.appUrl);
  const defaultReturnTo = config.defaultReturnTo ?? DEFAULT_RETURN_TO;

  return {
    async beginSignIn(returnTo) {
      const authorization = await config.authorizationClient.begin(callbackUrl);
      return {
        redirectTo: authorization.redirectTo,
        transaction: {
          state: authorization.state,
          returnTo: safeReturnTo(returnTo, defaultReturnTo),
        },
      };
    },

    async completeSignIn(callbackUrl, transaction) {
      const session = await config.authorizationClient.complete({ callbackUrl, state: transaction.state });
      return { redirectTo: new URL(transaction.returnTo, config.appUrl), session };
    },

    async currentSession(session) {
      if (session.expiresAt > Date.now() + REFRESH_SKEW_MILLISECONDS) return session;

      const renewed = await config.authorizationClient.renew(session);
      if (renewed.user.id !== session.user.id) throw new Error("Renewed identity changed the authenticated subject");
      return renewed;
    },

    publicSession(session) {
      return { authenticated: true, user: session.user };
    },

    async signOut(session) {
      if (!session) return postLogoutRedirectUrl;

      try {
        return await config.authorizationClient.end(session, postLogoutRedirectUrl);
      } catch {
        return postLogoutRedirectUrl;
      }
    },
  };
}
