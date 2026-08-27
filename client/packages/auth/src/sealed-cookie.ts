import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";

import type { ZodType } from "zod";

export interface SealedCookieAttributes {
  httpOnly: boolean;
  sameSite: "strict" | "lax" | "none";
  secure: boolean;
  path: string;
  maxAge: number;
}

export interface SealedCookieOptions<T> {
  name: string;
  schema: ZodType<T>;
  secret: string;
  version: string;
  additionalAuthenticatedData: string;
  attributes: SealedCookieAttributes;
}

export class SealedCookie<T> {
  static readonly #IV_LENGTH = 12;
  static readonly #TAG_LENGTH = 16;

  readonly name: string;
  readonly options: Readonly<SealedCookieAttributes>;

  readonly #schema: ZodType<T>;
  readonly #key: Buffer;
  readonly #version: string;
  readonly #additionalData: Buffer;

  constructor({ name, schema, secret, version, additionalAuthenticatedData, attributes }: SealedCookieOptions<T>) {
    this.name = name;
    this.#schema = schema;
    this.#key = createHash("sha256").update(secret, "utf8").digest();
    this.#version = version;
    this.#additionalData = Buffer.from(additionalAuthenticatedData, "utf8");
    this.options = Object.freeze({ ...attributes });
  }

  encode(value: T): string {
    const iv = randomBytes(SealedCookie.#IV_LENGTH);
    const cipher = createCipheriv("aes-256-gcm", this.#key, iv);
    cipher.setAAD(this.#additionalData);
    const encrypted = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
    const payload = Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString("base64url");
    return `${this.#version}.${payload}`;
  }

  decode(value: string | undefined): T | undefined {
    if (!value) return;

    const parsed = this.#schema.safeParse(this.#unseal(value));
    return parsed.success ? parsed.data : undefined;
  }

  #unseal(value: string): unknown | undefined {
    const [version, encoded] = value.split(".", 2);
    if (version !== this.#version || !encoded) return;

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
