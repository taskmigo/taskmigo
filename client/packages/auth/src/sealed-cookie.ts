import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";

import type { ZodType } from "zod";

const VERSION = "v1";
const IV_LENGTH = 12;
const TAG_LENGTH = 16;

function key(secret: string): Buffer {
  return createHash("sha256").update(secret, "utf8").digest();
}

function additionalData(purpose: string): Buffer {
  return Buffer.from(`taskmigo:${purpose}:${VERSION}`, "utf8");
}

function seal(value: unknown, secret: string, purpose: string): string {
  const iv = randomBytes(IV_LENGTH);
  const cipher = createCipheriv("aes-256-gcm", key(secret), iv);
  cipher.setAAD(additionalData(purpose));
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
  return `${VERSION}.${Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString("base64url")}`;
}

function unseal(value: string, secret: string, purpose: string): unknown | undefined {
  const [version, encoded] = value.split(".", 2);
  if (version !== VERSION || !encoded) return undefined;

  try {
    const payload = Buffer.from(encoded, "base64url");
    if (payload.length <= IV_LENGTH + TAG_LENGTH) return undefined;
    const decipher = createDecipheriv("aes-256-gcm", key(secret), payload.subarray(0, IV_LENGTH));
    decipher.setAAD(additionalData(purpose));
    decipher.setAuthTag(payload.subarray(IV_LENGTH, IV_LENGTH + TAG_LENGTH));
    const plaintext = Buffer.concat([decipher.update(payload.subarray(IV_LENGTH + TAG_LENGTH)), decipher.final()]);
    return JSON.parse(plaintext.toString("utf8")) as unknown;
  } catch {
    return undefined;
  }
}

export interface SealedCookie<T> {
  readonly name: string;
  encode(value: T): string;
  decode(value: string | undefined): T | undefined;
  readonly options: {
    httpOnly: true;
    sameSite: "lax";
    secure: boolean;
    path: "/";
    maxAge: number;
  };
}

export function createSealedCookie<T>(input: {
  name: string;
  purpose: string;
  schema: ZodType<T>;
  secret: string;
  maxAge: number;
}): SealedCookie<T> {
  return {
    name: input.name,
    encode: (value) => seal(value, input.secret, input.purpose),
    decode(value) {
      if (!value) return;
      const parsed = input.schema.safeParse(unseal(value, input.secret, input.purpose));
      return parsed.success ? parsed.data : undefined;
    },
    options: {
      httpOnly: true,
      sameSite: "lax",
      secure: process.env.NODE_ENV === "production",
      path: "/",
      maxAge: input.maxAge,
    },
  };
}
