export interface E2EEnvironment {
  baseUrl: URL;
  authorizationOrigin: string;
  username: string;
  password: string;
}

export interface E2EApiEnvironment {
  clientId: string;
  clientSecret: string;
}

const requiredEnvironment = (name: string): string => {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
};

export const e2eEnvironment = (): E2EEnvironment => ({
  baseUrl: new URL(requiredEnvironment("E2E_BASE_URL")),
  authorizationOrigin: new URL(requiredEnvironment("E2E_AUTH_ORIGIN")).origin,
  username: requiredEnvironment("E2E_USERNAME"),
  password: requiredEnvironment("E2E_PASSWORD"),
});

export const e2eApiEnvironment = (): E2EApiEnvironment => ({
  clientId: requiredEnvironment("E2E_API_CLIENT_ID"),
  clientSecret: requiredEnvironment("E2E_API_CLIENT_SECRET"),
});
