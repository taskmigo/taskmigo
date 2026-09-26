export interface TaskmigoCredentials {
  username: string;
  password: string;
}

export interface TaskmigoConfig {
  appUrl: URL;
  authorizationOrigin: string;
  credentials: TaskmigoCredentials;
}

const requiredEnvironment = (name: string): string => {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
};

export const taskmigoConfigFromEnvironment = (): TaskmigoConfig => ({
  appUrl: new URL(requiredEnvironment("E2E_BASE_URL")),
  authorizationOrigin: new URL(requiredEnvironment("E2E_AUTH_ORIGIN")).origin,
  credentials: {
    username: requiredEnvironment("E2E_USERNAME"),
    password: requiredEnvironment("E2E_PASSWORD"),
  },
});
