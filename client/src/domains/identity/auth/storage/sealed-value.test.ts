import { createCipheriv, createHash } from "node:crypto";

import { describe, expect, test } from "vitest";

import { SealedValue } from "./sealed-value";

const secret = "01234567890123456789012345678901";
const context = "feature-state:v1";

type Subject = { subject: string };

function parseSubject(value: unknown): Subject | undefined {
  if (typeof value !== "object" || value === null) {
    return;
  }
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
