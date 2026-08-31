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
  test("persists organization ACL through the deployed gateway", async ({ request }) => {
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
        spec: {
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
      },
    });
    expect(saveResponse.status()).toBe(200);

    const listedPolicies = await request.get(`/api/v0/organizations/${organization.data.id}/acl-policies`, {
      headers,
    });
    expect(listedPolicies.status()).toBe(200);
    expect(((await listedPolicies.json()) as ApiResponse<string[]>).data).toContain(policyName);

    // The integration client runs as Taskmigo's organization-less system user. It may administer tenant ACLs, but a
    // tenant policy must not become a global policy and therefore must not apply to this system request.
    const systemRequest = await request.get("/api/v0/projects", { headers });
    expect(systemRequest.status()).toBe(200);

    const deleteResponse = await request.delete(policyUrl, { headers });
    expect(deleteResponse.status()).toBe(200);

    const listedAfterDelete = await request.get(`/api/v0/organizations/${organization.data.id}/acl-policies`, {
      headers,
    });
    expect(listedAfterDelete.status()).toBe(200);
    expect(((await listedAfterDelete.json()) as ApiResponse<string[]>).data).not.toContain(policyName);
  });

  test("rejects an unauthenticated API request through the deployed gateway", async ({ request }) => {
    const response = await request.get("/api/v0/projects");
    expect(response.status()).toBe(401);
  });
});
