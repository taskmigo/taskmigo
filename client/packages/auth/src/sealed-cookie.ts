import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";

import type { ZodType } from "zod";

export interface SealedCookieOptions<T> {
  name: string;
  purpose: string;
  schema: ZodType<T>;
  secret: string;
  maxAge: number;
}

export class SealedCookie<T> {
  static readonly #VERSION = "v1";
  static readonly #IV_LENGTH = 12;
  static readonly #TAG_LENGTH = 16;

  readonly name: string;
  readonly options: Readonly<{
    httpOnly: true;
    sameSite: "lax";
    secure: boolean;
    path: "/";
    maxAge: number;
  }>;

  readonly #schema: ZodType<T>;
  readonly #key: Buffer;
  readonly #additionalData: Buffer;

  constructor({ name, purpose, schema, secret, maxAge }: SealedCookieOptions<T>) {
    this.name = name;
    this.#schema = schema;
    this.#key = createHash("sha256").update(secret, "utf8").digest();
    this.#additionalData = Buffer.from(`taskmigo:${purpose}:${SealedCookie.#VERSION}`, "utf8");
    this.options = Object.freeze({
      httpOnly: true,
      sameSite: "lax",
      secure: process.env.NODE_ENV === "production",
      path: "/",
      maxAge,
    });
  }

  encode(value: T): string {
    const iv = randomBytes(SealedCookie.#IV_LENGTH);
    const cipher = createCipheriv("aes-256-gcm", this.#key, iv);
    cipher.setAAD(this.#additionalData);
    const encrypted = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
    const payload = Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString("base64url");
    return `${SealedCookie.#VERSION}.${payload}`;
  }

  decode(value: string | undefined): T | undefined {
    if (!value) return;

    const parsed = this.#schema.safeParse(this.#unseal(value));
    return parsed.success ? parsed.data : undefined;
  }

  #unseal(value: string): unknown | undefined {
    const [version, encoded] = value.split(".", 2);
    if (version !== SealedCookie.#VERSION || !encoded) return;

    try {
      const payload = Buffer.from(encoded, "base64url");
      if (payload.length <= SealedCookie.#IV_LENGTH + SealedCookie.#TAG_LENGTH) return;

      const tagEnd = SealedCookie.#IV_LENGTH + SealedCookie.#TAG_LENGTH;
      const decipher = createDecipheriv("aes-256-gcm", this.#key, payload.subarray(0, SealedCookie.#IV_LENGTH));
      decipher.setAAD(this.#additionalData);
      decipher.setAuthTag(payload.subarray(SealedCookie.#IV_LENGTH, tagEnd));
      const plaintext = Buffer.concat([decipher.update(payload.subarray(tagEnd)), decipher.final()]);
      return JSON.parse(plaintext.toString("utf8")) as unknown;
    } catch {
      return;
    }
  }
}
