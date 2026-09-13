import { expect, test, type APIRequestContext, type APIResponse } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

import { e2eApiEnvironment, e2eEnvironment } from "../support/environment";

type JsonObject = Record<string, unknown>;

interface AuthorizationFeature {
  name: string;
  tags?: string[];
  setup?: {
    users?: UserFixture[];
    statements?: StatementFixture[];
  };
  request: HttpRequest;
  expected: ExpectedResponse;
}

interface UserFixture {
  alias: string;
  username: string;
  emails?: string[];
  firstName: string;
  lastName: string;
}

interface StatementFixture {
  alias?: string;
  description?: string | null;
  effect: "allow" | "deny";
  scope: "request" | "object";
  target: {
    method: string;
    path: string;
  };
  policy: string;
}

interface HttpRequest {
  method: string;
  path: string;
  headers?: Record<string, string>;
  body?: unknown;
}

interface ExpectedResponse {
  status: number;
  body?: {
    contains?: unknown;
    collections?: Array<{
      path: string;
      contains?: unknown[];
      excludes?: unknown[];
    }>;
  };
}

interface HttpResult {
  status: number;
  body: unknown;
}

const featureDirectory = fileURLToPath(new URL("../../features/authorization/", import.meta.url));
const features = readdirSync(featureDirectory)
  .filter((name) => name.endsWith(".yaml") && !name.startsWith("_"))
  .sort()
  .map((name) => loadFeature(name));

test.describe("Authorization feature cases @authorization @feature", () => {
  for (const feature of features) {
    const tags = feature.tags?.map((tag) => `@${tag}`).join(" ") ?? "";
    test(`${feature.name} ${tags}`.trim(), async ({ request }) => {
      await runFeature(request, feature);
    });
  }
});

const runFeature = async (request: APIRequestContext, feature: AuthorizationFeature): Promise<void> => {
  const environment = e2eEnvironment();
  const token = await accessToken(request);
  const runId = randomUUID().replaceAll("-", "").slice(0, 12);
  const variables: JsonObject = { runId };
  const principal = await findPrincipal(request, environment.baseUrl, token, environment.username);
  variables.principal = principal;

  const cleanupPath = `/api/v0/users/${principal.id}/statements`;
  await replacePrincipalStatements(request, environment.baseUrl, token, cleanupPath, []);

  try {
    for (const user of feature.setup?.users ?? []) {
      const fixture = resolveTemplate(user, variables) as UserFixture;
      const result = await apiCall(request, environment.baseUrl, token, {
        method: "POST",
        path: "/api/v0/users",
        body: {
          username: fixture.username,
          emails: fixture.emails ?? [],
          firstName: fixture.firstName,
          lastName: fixture.lastName,
          roleIds: [],
          groupIds: [],
        },
      });
      expect(result.status, `creating user fixture ${fixture.alias}`).toBe(201);
      const id = requiredString(valueAtPath(result.body, "data.id"), `user fixture ${fixture.alias} id`);
      setNestedVariable(variables, ["users", fixture.alias], { ...fixture, id });
    }

    const statementIds: string[] = [];
    for (const statement of feature.setup?.statements ?? []) {
      const fixture = resolveTemplate(statement, variables) as StatementFixture;
      assertCleanupRouteIsNotDenied(fixture, cleanupPath);
      const result = await apiCall(request, environment.baseUrl, token, {
        method: "POST",
        path: "/api/v0/statements",
        body: {
          name: `e2e-${runId}-${statementIds.length + 1}`,
          description: fixture.description ?? null,
          effect: fixture.effect,
          scope: fixture.scope,
          target: {
            api: {
              method: fixture.target.method,
              path: fixture.target.path,
            },
          },
          policy: fixture.policy,
        },
      });
      expect(result.status, `creating ${fixture.scope} authorization statement`).toBe(201);
      const id = requiredString(valueAtPath(result.body, "data.id"), "statement id");
      statementIds.push(id);
      if (fixture.alias) {
        setNestedVariable(variables, ["statements", fixture.alias], { ...fixture, id });
      }
    }

    if (statementIds.length > 0) {
      await replacePrincipalStatements(request, environment.baseUrl, token, cleanupPath, statementIds);
    }

    const featureRequest = resolveTemplate(feature.request, variables) as HttpRequest;
    const result = await apiCall(request, environment.baseUrl, token, featureRequest);
    assertExpectedResponse(result, resolveTemplate(feature.expected, variables) as ExpectedResponse);
  } finally {
    await replacePrincipalStatements(request, environment.baseUrl, token, cleanupPath, []);
  }
};

const accessToken = async (request: APIRequestContext): Promise<string> => {
  const environment = e2eEnvironment();
  const api = e2eApiEnvironment();
  const authorization = Buffer.from(`${api.clientId}:${api.clientSecret}`, "utf8").toString("base64");
  const response = await request.post(new URL("/oauth2/token", environment.authorizationOrigin).toString(), {
    headers: {
      Authorization: `Basic ${authorization}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    form: {
      grant_type: "client_credentials",
      scope: "taskmigo.api",
    },
    failOnStatusCode: false,
  });
  const body = await bodyOf(response);
  expect(response.status(), "obtaining E2E API access token").toBe(200);
  return requiredString(valueAtPath(body, "access_token"), "OAuth access token");
};

const findPrincipal = async (
  request: APIRequestContext,
  baseUrl: URL,
  token: string,
  username: string,
): Promise<JsonObject> => {
  let page = 1;
  while (true) {
    const result = await apiCall(request, baseUrl, token, {
      method: "GET",
      path: `/api/v0/users?page=${page}&pageSize=100`,
    });
    expect(result.status, "listing users to resolve the E2E principal").toBe(200);
    const users = valueAtPath(result.body, "data");
    if (!Array.isArray(users)) throw new Error("Expected user list response data to be an array");

    const principal = users.find((user) => isRecord(user) && user.username === username);
    if (isRecord(principal)) {
      return {
        id: requiredString(principal.id, "principal id"),
        username: requiredString(principal.username, "principal username"),
      };
    }

    const totalPages = valueAtPath(result.body, "meta.pagination.offset.totalPages");
    if (typeof totalPages !== "number" || page >= totalPages) {
      throw new Error(`Could not resolve E2E principal with username ${username}`);
    }
    page += 1;
  }
};

const replacePrincipalStatements = async (
  request: APIRequestContext,
  baseUrl: URL,
  token: string,
  path: string,
  statementIds: string[],
): Promise<void> => {
  const result = await apiCall(request, baseUrl, token, {
    method: "PATCH",
    path,
    body: { statementIds },
  });
  expect(result.status, "replacing E2E principal direct statements").toBe(200);
};

const apiCall = async (
  request: APIRequestContext,
  baseUrl: URL,
  token: string,
  input: HttpRequest,
): Promise<HttpResult> => {
  const response = await request.fetch(new URL(input.path, baseUrl).toString(), {
    method: input.method.toUpperCase(),
    headers: {
      Authorization: `Bearer ${token}`,
      ...(input.body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(input.headers ?? {}),
    },
    data: input.body,
    failOnStatusCode: false,
  });
  return { status: response.status(), body: await bodyOf(response) };
};

const bodyOf = async (response: APIResponse): Promise<unknown> => {
  const text = await response.text();
  if (text.length === 0) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
};

const assertExpectedResponse = (actual: HttpResult, expected: ExpectedResponse): void => {
  expect(actual.status).toBe(expected.status);
  if (!expected.body) return;

  if (expected.body.contains !== undefined) {
    expect(
      matchesPartial(actual.body, expected.body.contains),
      `response body should contain ${JSON.stringify(expected.body.contains)}`,
    ).toBe(true);
  }

  for (const collectionExpectation of expected.body.collections ?? []) {
    const collection = valueAtPath(actual.body, collectionExpectation.path);
    expect(Array.isArray(collection), `${collectionExpectation.path} should be an array`).toBe(true);
    if (!Array.isArray(collection)) continue;

    for (const item of collectionExpectation.contains ?? []) {
      expect(
        collection.some((actualItem) => matchesPartial(actualItem, item)),
        `${collectionExpectation.path} should contain ${JSON.stringify(item)}`,
      ).toBe(true);
    }
    for (const item of collectionExpectation.excludes ?? []) {
      expect(
        collection.some((actualItem) => matchesPartial(actualItem, item)),
        `${collectionExpectation.path} should exclude ${JSON.stringify(item)}`,
      ).toBe(false);
    }
  }
};

const matchesPartial = (actual: unknown, expected: unknown): boolean => {
  if (Array.isArray(expected)) {
    return (
      Array.isArray(actual) &&
      expected.every((expectedItem) => actual.some((actualItem) => matchesPartial(actualItem, expectedItem)))
    );
  }
  if (isRecord(expected)) {
    return isRecord(actual) && Object.entries(expected).every(([key, value]) => matchesPartial(actual[key], value));
  }
  return Object.is(actual, expected);
};

const resolveTemplate = (value: unknown, variables: JsonObject): unknown => {
  if (typeof value === "string") {
    const exact = /^\{\{\s*([^{}]+?)\s*\}\}$/.exec(value);
    if (exact) return requiredVariable(variables, exact[1]);
    return value.replace(/\{\{\s*([^{}]+?)\s*\}\}/g, (_, path: string) =>
      String(requiredVariable(variables, path)),
    );
  }
  if (Array.isArray(value)) return value.map((item) => resolveTemplate(item, variables));
  if (isRecord(value)) {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, resolveTemplate(item, variables)]));
  }
  return value;
};

const requiredVariable = (variables: JsonObject, path: string): unknown => {
  const value = valueAtPath(variables, path);
  if (value === undefined) throw new Error(`Unknown feature variable: ${path}`);
  return value;
};

const valueAtPath = (root: unknown, path: string): unknown => {
  let current = root;
  for (const segment of path.split(".")) {
    if (!isRecord(current)) return undefined;
    current = current[segment];
  }
  return current;
};

const setNestedVariable = (variables: JsonObject, path: string[], value: unknown): void => {
  let current = variables;
  for (const segment of path.slice(0, -1)) {
    const existing = current[segment];
    if (isRecord(existing)) {
      current = existing;
    } else {
      const created: JsonObject = {};
      current[segment] = created;
      current = created;
    }
  }
  current[path.at(-1) ?? "value"] = value;
};

const assertCleanupRouteIsNotDenied = (statement: StatementFixture, cleanupPath: string): void => {
  if (statement.scope !== "request" || statement.effect !== "deny") return;
  const methodMatches = statement.target.method === "*" || statement.target.method.toUpperCase() === "PATCH";
  if (!methodMatches) return;

  let pathMatches = false;
  try {
    pathMatches = new RegExp(`^(?:${statement.target.path})$`).test(cleanupPath);
  } catch {
    return;
  }
  if (pathMatches) {
    throw new Error(
      `Request DENY target ${statement.target.method} ${statement.target.path} can block E2E cleanup route ${cleanupPath}`,
    );
  }
};

const loadFeature = (name: string): AuthorizationFeature => {
  const parsed = parse(readFileSync(new URL(`../../features/authorization/${name}`, import.meta.url), "utf8")) as unknown;
  if (!isRecord(parsed) || typeof parsed.name !== "string") {
    throw new Error(`Invalid authorization feature file: ${name}`);
  }
  return parsed as unknown as AuthorizationFeature;
};

const requiredString = (value: unknown, label: string): string => {
  if (typeof value !== "string" || value.length === 0) throw new Error(`Expected ${label} to be a non-empty string`);
  return value;
};

const isRecord = (value: unknown): value is JsonObject =>
  typeof value === "object" && value !== null && !Array.isArray(value);
