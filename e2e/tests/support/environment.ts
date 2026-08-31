export interface E2EEnvironment {
  baseUrl: URL;
  authorizationOrigin: string;
  username: string;
  password: string;
}

export interface E2EApiEnvironment {
  baseUrl: URL;
  authorizationOrigin: string;
  clientId: string;
  clientSecret: string;
}

const requiredEnvironment = (name: string): string => {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
};

const baseEnvironment = (): {
  baseUrl: URL;
  authorizationOrigin: string;
} => ({
  baseUrl: new URL(requiredEnvironment("E2E_BASE_URL")),
  authorizationOrigin: new URL(requiredEnvironment("E2E_AUTH_ORIGIN")).origin,
});

export const e2eEnvironment = (): E2EEnvironment => ({
  ...baseEnvironment(),
  username: requiredEnvironment("E2E_USERNAME"),
  password: requiredEnvironment("E2E_PASSWORD"),
});

export const e2eApiEnvironment = (): E2EApiEnvironment => ({
  ...baseEnvironment(),
  clientId: requiredEnvironment("E2E_API_CLIENT_ID"),
  clientSecret: requiredEnvironment("E2E_API_CLIENT_SECRET"),
});
