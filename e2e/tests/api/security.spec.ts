import { expect, test } from "@playwright/test";

import {
  apiHeaders,
  expectFailure,
  expectSuccess,
  requestClientCredentialsToken,
} from "./client.js";

test.describe("API security @api @security", () => {
  test("rejects an unauthenticated API request", async ({ request }) => {
    const response = await request.get("/api/v0/permissions");
    await expectFailure(
      response,
      401,
      "security.unauthorized",
      "UNAUTHORIZED",
    );
  });

  test("rejects a malformed bearer token", async ({ request }) => {
    const response = await request.get("/api/v0/permissions", {
      headers: { Authorization: "Bearer definitely-not-a-jwt" },
    });
    await expectFailure(
      response,
      401,
      "security.unauthorized",
      "UNAUTHORIZED",
    );
  });

  test("issues a client-credentials token that can call the API", async ({
    request,
  }) => {
    const response = await request.get("/api/v0/permissions", {
      headers: await apiHeaders(request),
    });
    await expectSuccess<string[]>(
      response,
      200,
      "resource.permissions.retrieved",
    );
  });

  test("rejects invalid OAuth client credentials", async ({ request }) => {
    const response = await requestClientCredentialsToken(request, {
      clientSecret: "incorrect-e2e-secret",
    });
    expect(response.status()).toBe(401);
    await expect(response.json()).resolves.toMatchObject({
      error: "invalid_client",
    });
  });

  test("rejects an unsupported OAuth scope", async ({ request }) => {
    const response = await requestClientCredentialsToken(request, {
      scope: "taskmigo.invalid",
    });
    expect(response.status()).toBe(400);
    await expect(response.json()).resolves.toMatchObject({
      error: "invalid_scope",
    });
  });
});
