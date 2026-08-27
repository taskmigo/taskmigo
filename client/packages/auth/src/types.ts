import { z } from "zod";

export const authTransactionSchema = z.object({
  providerState: z.string().min(1),
  returnTo: z.string().min(1),
});

export const sessionSchema = z.object({
  subject: z.string().min(1),
  name: z.string().min(1).optional(),
  accessToken: z.string().min(1),
  accessTokenExpiresAt: z.number(),
  refreshToken: z.string().min(1),
  idToken: z.string().min(1),
});

export type AuthTransaction = z.infer<typeof authTransactionSchema>;
export type Session = z.infer<typeof sessionSchema>;

export interface OAuthTokens {
  accessToken: string;
  expiresIn: number;
  refreshToken?: string;
  idToken?: string;
  subject?: string;
  name?: string;
}

export interface AuthorizationProvider {
  beginLogin(callbackUrl: URL): Promise<{ url: URL; state: string }>;
  completeLogin(input: { callbackUrl: URL; state: string }): Promise<OAuthTokens>;
  refresh(refreshToken: string): Promise<OAuthTokens>;
  endSession(input: { idToken: string; postLogoutRedirectUrl: URL }): Promise<URL>;
}

export interface AuthConfig {
  appUrl: URL;
  sessionSecret: string;
  provider: AuthorizationProvider;
  defaultReturnTo?: string;
}
