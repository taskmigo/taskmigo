import { Buffer } from "node:buffer";
import { randomUUID } from "node:crypto";

import { expect, test, type APIRequestContext } from "@playwright/test";

import { e2eApiEnvironment } from "../support/environment.js";

interface ApiResponse<T> {
  success: boolean;
  status_code: number;
  message: {
    code: string;
    text: string;
  };
  data: T;
}

interface TokenResponse {
  access_token: string;
}

interface HistoryEntry {
  action: string;
  target: {
    displayName: string;
  } | null;
  changes: Array<{
    field: string;
    before: unknown;
    after: unknown;
  }>;
}

const accessToken = async (request: APIRequestContext): Promise<string> => {
  const environment = e2eApiEnvironment();
  const credentials = Buffer.from(`${environment.clientId}:${environment.clientSecret}`).toString("base64");
  const response = await request.post("/oauth2/token", {
    headers: {
      Authorization: `Basic ${credentials}`,
    },
    form: {
      grant_type: "client_credentials",
      scope: "taskmigo.api",
    },
  });

  expect(response.status()).toBe(200);
  const token = (await response.json()) as TokenResponse;
  expect(token.access_token).toBeTruthy();
  return token.access_token;
};

const authorization = (token: string): Record<string, string> => ({
  Authorization: `Bearer ${token}`,
});

test.describe("Project API", { tag: ["@api", "@project"] }, () => {
  test("supports PATCH-only project lifecycle through the deployed gateway", async ({ request }) => {
    const token = await accessToken(request);
    const headers = authorization(token);
    const suffix = randomUUID();

    const organizationResponse = await request.post("/api/v0/organizations", {
      headers,
      data: {
        key: `e2e-${suffix}`,
        name: "E2E Project API",
      },
    });
    expect(organizationResponse.status()).toBe(201);
    const organization = (await organizationResponse.json()) as ApiResponse<{ id: string }>;
    expect(organization.success).toBe(true);

    const oldKey = `before-${suffix}`;
    const newKey = `after-${suffix}`;
    const createResponse = await request.post(`/api/v0/organizations/${organization.data.id}/projects`, {
      headers,
      data: {
        key: oldKey,
        name: "Before",
        description: "Before description",
      },
    });
    expect(createResponse.status()).toBe(201);
    const project = (await createResponse.json()) as ApiResponse<{ id: string }>;
    expect(project.message.code).toBe("resource.project.created");

    const projectUrl = `/api/v0/projects/${project.data.id}`;

    const namePatch = await request.patch(projectUrl, {
      headers,
      data: { name: "After" },
    });
    expect(namePatch.status()).toBe(200);

    const clearDescription = await request.patch(projectUrl, {
      headers,
      data: { description: null },
    });
    expect(clearDescription.status()).toBe(200);

    const keyPatch = await request.patch(projectUrl, {
      headers,
      data: { key: newKey },
    });
    expect(keyPatch.status()).toBe(200);

    const noOpPatch = await request.patch(projectUrl, {
      headers,
      data: {
        key: newKey,
        name: "After",
        description: null,
      },
    });
    expect(noOpPatch.status()).toBe(200);

    const unsupportedPatch = await request.patch(projectUrl, {
      headers,
      data: { organizationId: randomUUID() },
    });
    expect(unsupportedPatch.status()).toBe(400);

    const putResponse = await request.fetch(projectUrl, {
      method: "PUT",
      headers,
      data: { name: "PUT must not be supported" },
    });
    expect(putResponse.status()).toBe(405);

    const reuseOldKey = await request.post(`/api/v0/organizations/${organization.data.id}/projects`, {
      headers,
      data: {
        key: oldKey,
        name: "Old key reused",
      },
    });
    expect(reuseOldKey.status()).toBe(201);

    const duplicateCurrentKey = await request.post(`/api/v0/organizations/${organization.data.id}/projects`, {
      headers,
      data: {
        key: newKey,
        name: "Duplicate current key",
      },
    });
    expect(duplicateCurrentKey.status()).toBe(409);

    const historyBeforeArchiveResponse = await request.get(`${projectUrl}/history?limit=20`, { headers });
    expect(historyBeforeArchiveResponse.status()).toBe(200);
    const historyBeforeArchive = (await historyBeforeArchiveResponse.json()) as ApiResponse<HistoryEntry[]>;
    const updates = historyBeforeArchive.data.filter((entry) => entry.action === "PROJECT_UPDATED");
    expect(updates).toHaveLength(3);
    expect(updates.map((entry) => entry.changes)).toEqual(
      expect.arrayContaining([
        [{ field: "name", before: "Before", after: "After" }],
        [{ field: "description", before: "Before description", after: null }],
        [{ field: "key", before: oldKey, after: newKey }],
      ]),
    );
    expect(updates.find((entry) => entry.changes[0]?.field === "key")?.target?.displayName).toBe("After");

    const archiveResponse = await request.patch(`${projectUrl}/archive`, { headers });
    expect(archiveResponse.status()).toBe(200);

    const archivedPatch = await request.patch(projectUrl, {
      headers,
      data: { name: "Must stay archived" },
    });
    expect(archivedPatch.status()).toBe(409);

    const historyAfterArchiveResponse = await request.get(`${projectUrl}/history?limit=20`, { headers });
    expect(historyAfterArchiveResponse.status()).toBe(200);
    const historyAfterArchive = (await historyAfterArchiveResponse.json()) as ApiResponse<HistoryEntry[]>;
    expect(historyAfterArchive.data[0]?.action).toBe("PROJECT_ARCHIVED");
    expect(historyAfterArchive.data[0]?.changes.map((change) => change.field)).toEqual(["status", "archivedAt"]);
  });
});
