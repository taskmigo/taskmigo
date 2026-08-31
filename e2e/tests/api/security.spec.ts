import { expectFailure, expectOAuthError } from "./client.js";
import { expect, test } from "./fixtures.js";

test.describe("API security @api @security", () => {
  test("rejects an unauthenticated API request", async ({ api }) => {
    const response = await api.raw.get("/api/v0/permissions");
    await expectFailure(response, 401, "security.unauthorized", "UNAUTHORIZED");
  });

  test("rejects a malformed bearer token", async ({ api }) => {
    const response = await api.raw.get("/api/v0/permissions", {
      headers: { Authorization: "Bearer definitely-not-a-jwt" },
    });
    await expectFailure(response, 401, "security.unauthorized", "UNAUTHORIZED");
  });

  test("issues a client-credentials token that can call the API", async ({ api }) => {
    expect(await api.permissions()).not.toHaveLength(0);
  });

  test("rejects invalid OAuth client credentials", async ({ api }) => {
    const response = await api.requestToken({ clientSecret: "incorrect-e2e-secret" });
    await expectOAuthError(response, 401, "invalid_client");
  });

  test("rejects an unsupported OAuth scope", async ({ api }) => {
    const response = await api.requestToken({ scope: "taskmigo.invalid" });
    await expectOAuthError(response, 400, "invalid_scope");
  });
});
