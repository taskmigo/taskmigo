import { Buffer } from "node:buffer";
import { randomUUID } from "node:crypto";

import { expect, test, type APIRequestContext } from "@playwright/test";

import { e2eApiEnvironment } from "../support/environment.js";

interface ApiResponse<T> {
  success: boolean;
  data: T;
}

interface TokenResponse {
  access_token: string;
}

const accessToken = async (request: APIRequestContext): Promise<string> => {
  const environment = e2eApiEnvironment();
  const credentials = Buffer.from(`${environment.clientId}:${environment.clientSecret}`).toString("base64");
  const response = await request.post("/oauth2/token", {
    headers: { Authorization: `Basic ${credentials}` },
    form: {
      grant_type: "client_credentials",
      scope: "taskmigo.api",
    },
  });

  expect(response.status()).toBe(200);
  return ((await response.json()) as TokenResponse).access_token;
};

const authorization = (token: string): Record<string, string> => ({
  Authorization: `Bearer ${token}`,
});

test.describe("API ACL", { tag: ["@api", "@acl"] }, () => {
  test("loads a persisted request ACL on the next request through the deployed gateway", async ({ request }) => {
    const token = await accessToken(request);
    const headers = authorization(token);
    const suffix = randomUUID();

    const organizationResponse = await request.post("/api/v0/organizations", {
      headers,
      data: {
        key: `acl-e2e-${suffix}`,
        name: "ACL E2E",
      },
    });
    expect(organizationResponse.status()).toBe(201);
    const organization = (await organizationResponse.json()) as ApiResponse<{ id: string }>;

    const policyName = `deny-project-list-${suffix}`;
    const policyUrl = `/api/v0/organizations/${organization.data.id}/acl-policies/${policyName}`;
    const saveResponse = await request.put(policyUrl, {
      headers,
      data: {
        kind: "acl/request",
        target: {
          methods: ["GET"],
          path: "/api/v0/projects",
        },
        rules: {
          "deny-authenticated": {
            effect: "deny",
            when: {
              exists: "principal.id",
            },
          },
        },
      },
    });
    expect(saveResponse.status()).toBe(200);

    const listedPolicies = await request.get(`/api/v0/organizations/${organization.data.id}/acl-policies`, {
      headers,
    });
    expect(listedPolicies.status()).toBe(200);
    expect(((await listedPolicies.json()) as ApiResponse<string[]>).data).toContain(policyName);

    const deniedResponse = await request.get("/api/v0/projects", { headers });
    expect(deniedResponse.status()).toBe(403);

    const deleteResponse = await request.delete(policyUrl, { headers });
    expect(deleteResponse.status()).toBe(200);

    const allowedAgain = await request.get("/api/v0/projects", { headers });
    expect(allowedAgain.status()).toBe(200);
  });
});
