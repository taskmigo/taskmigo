import { expect, test, vi } from "vitest";

const auth = vi.hoisted(() => ({ value: {} }));

vi.mock("server-only", () => ({}));
vi.mock("./auth/runtime", () => ({
  AuthRuntime: {
    get: vi.fn(() => ({ auth: auth.value })),
  },
}));

test("getAuth exposes the global auth runtime", async () => {
  const { getAuth } = await import("./auth");
  expect(getAuth()).toBe(auth.value);
});
