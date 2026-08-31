import { expect, test, vi } from "vitest";

const auth = vi.hoisted(() => {
  const context = {};
  const session = {};
  return {
    context,
    session,
    getAuth: vi.fn(() => context),
    getSession: vi.fn(async () => session),
  };
});

vi.mock("server-only", () => ({}));
vi.mock("./auth/runtime", () => ({ getAuth: auth.getAuth, getSession: auth.getSession }));

test("auth facade exposes runtime operations without wrapping them", async () => {
  const facade = await import("./auth");

  expect(facade.getAuth).toBe(auth.getAuth);
  expect(facade.getSession).toBe(auth.getSession);
  expect(facade.getAuth()).toBe(auth.context);
  await expect(facade.getSession()).resolves.toBe(auth.session);
});
