import { createCipheriv, createHash } from "node:crypto";

import { afterEach, describe, expect, test, vi } from "vitest";

import { SealedValue } from "./node/sealed-value";
import { globalSingleton, systemClock } from "./runtime";
import { CookieState, type StringCodec } from "./state";
import { OriginScope } from "./url";

const secret = "01234567890123456789012345678901";
const context = "feature-state:v1";
const singletonKey = Symbol.for("taskmigo.foundation.test.singleton");

type Subject = { subject: string };

function parseSubject(value: unknown): Subject | undefined {
  if (typeof value !== "object" || value === null) return;
  const subject = (value as Record<string, unknown>).subject;
  return typeof subject === "string" ? { subject } : undefined;
}

function sealed(overrides: Partial<ConstructorParameters<typeof SealedValue<Subject>>[0]> = {}) {
  return new SealedValue({ secret, version: "v1", context, parse: parseSubject, ...overrides });
}

function sealRaw(plaintext: string): string {
  const key = createHash("sha256").update(secret, "utf8").digest();
  const iv = Buffer.alloc(12, 1);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  cipher.setAAD(Buffer.from(context, "utf8"));
  const encrypted = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  return `v1.${Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString("base64url")}`;
}

afterEach(() => {
  Reflect.deleteProperty(globalThis, singletonKey);
});

describe("runtime primitives", () => {
  test("uses the system clock and memoizes global values by symbol", () => {
    const before = Date.now();
    expect(systemClock()).toBeGreaterThanOrEqual(before);

    const create = vi.fn(() => ({ id: 1 }));
    const first = globalSingleton(singletonKey, create);
    const second = globalSingleton(singletonKey, create);

    expect(second).toBe(first);
    expect(create).toHaveBeenCalledTimes(1);
  });
});

describe("OriginScope", () => {
  const scope = new OriginScope(new URL("https://app.example/base/"));

  test("resolves only URLs inside its origin and returns defensive base copies", () => {
    expect(scope.resolve("/projects")?.href).toBe("https://app.example/projects");
    expect(scope.resolve(new URL("https://app.example/settings"))?.pathname).toBe("/settings");
    expect(scope.resolve("https://attacker.example/path")).toBeUndefined();
    expect(scope.resolve("http://[invalid")).toBeUndefined();

    const base = scope.baseUrl;
    base.pathname = "/tampered";
    expect(scope.baseUrl.pathname).toBe("/base/");
  });

  test("canonicalizes local paths while rejecting non-local and malformed targets", () => {
    expect(scope.path("/projects?mine=true#active")).toBe("/projects?mine=true#active");
    expect(scope.path("projects")).toBeUndefined();
    expect(scope.path("//attacker.example/path")).toBeUndefined();
    expect(scope.path(String.raw`/\\attacker.example/path`)).toBeUndefined();
    expect(scope.path("//[invalid")).toBeUndefined();
  });
});

describe("SealedValue", () => {
  test("round-trips typed JSON state", () => {
    const codec = sealed();
    expect(codec.decode(codec.encode({ subject: "developer" }))).toEqual({ subject: "developer" });
  });

  test.each([undefined, "", "v2.payload", "v1.", "v1.AA"])("rejects invalid envelope %s", (value) => {
    expect(sealed().decode(value)).toBeUndefined();
  });

  test("isolates state by secret, context, and parser", () => {
    const value = sealed().encode({ subject: "developer" });

    expect(sealed({ secret: "different-secret" }).decode(value)).toBeUndefined();
    expect(sealed({ context: "different-context" }).decode(value)).toBeUndefined();
    expect(
      sealed({
        parse: (): Subject | undefined => {
          return;
        },
      }).decode(value),
    ).toBeUndefined();
  });

  test("rejects tampered ciphertext and invalid authenticated JSON without leaking errors", () => {
    const value = sealed().encode({ subject: "developer" });
    const tampered = `${value.slice(0, -1)}${value.endsWith("A") ? "B" : "A"}`;

    expect(sealed().decode(tampered)).toBeUndefined();
    expect(sealed().decode(sealRaw("not-json"))).toBeUndefined();
  });

  test("validates decoded values even when callers bypass the TypeScript type", () => {
    const codec = sealed();
    const invalid = codec.encode({ subject: 42 } as unknown as Subject);
    expect(codec.decode(invalid)).toBeUndefined();
  });
});

describe("CookieState", () => {
  const attributes = { httpOnly: true, sameSite: "lax" as const, secure: true, path: "/", maxAge: 60 };

  test("composes a string codec with cookie storage without owning the transport", () => {
    const codec: StringCodec<Subject> = {
      encode: vi.fn(() => "encoded"),
      decode: vi.fn((value) => (value === "encoded" ? { subject: "developer" } : undefined)),
    };
    const state = new CookieState({ name: "state", codec, attributes });
    const reader = { get: vi.fn(() => ({ value: "encoded" })) };
    const writer = { set: vi.fn(), delete: vi.fn() };

    expect(state.read(reader)).toEqual({ subject: "developer" });
    state.write(writer, { subject: "developer" });
    state.clear(writer);

    expect(writer.set).toHaveBeenCalledWith("state", "encoded", attributes);
    expect(writer.delete).toHaveBeenCalledWith("state");
    expect(state.name).toBe("state");
    expect(state.attributes).toEqual(attributes);
    expect(Object.isFrozen(state.attributes)).toBe(true);
  });

  test("passes missing cookie values through the codec", () => {
    const decode = vi.fn((): Subject | undefined => {
      return;
    });
    const state = new CookieState<Subject>({
      name: "state",
      codec: { encode: () => "encoded", decode },
      attributes,
    });

    expect(
      state.read({
        get: (): { value: string } | undefined => {
          return;
        },
      }),
    ).toBeUndefined();
    expect(decode).toHaveBeenCalledWith(undefined);
  });
});
