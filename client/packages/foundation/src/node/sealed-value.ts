import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";

import type { StringCodec } from "../state";

export interface SealedValueOptions<T> {
  secret: string;
  version: string;
  context: string;
  parse(value: unknown): T | undefined;
}

export class SealedValue<T> implements StringCodec<T> {
  static readonly #IV_LENGTH = 12;
  static readonly #TAG_LENGTH = 16;

  readonly #key: Buffer;
  readonly #version: string;
  readonly #context: Buffer;
  readonly #parse: (value: unknown) => T | undefined;

  constructor({ secret, version, context, parse }: SealedValueOptions<T>) {
    this.#key = createHash("sha256").update(secret, "utf8").digest();
    this.#version = version;
    this.#context = Buffer.from(context, "utf8");
    this.#parse = parse;
  }

  encode(value: T): string {
    const iv = randomBytes(SealedValue.#IV_LENGTH);
    const cipher = createCipheriv("aes-256-gcm", this.#key, iv);
    cipher.setAAD(this.#context);
    const encrypted = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
    const payload = Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString("base64url");
    return `${this.#version}.${payload}`;
  }

  decode(value: string | undefined): T | undefined {
    if (!value) return;

    const plaintext = this.#open(value);
    if (plaintext === undefined) return;

    try {
      return this.#parse(JSON.parse(plaintext) as unknown);
    } catch {
      return;
    }
  }

  #open(value: string): string | undefined {
    const [version, encoded] = value.split(".", 2);
    if (version !== this.#version || !encoded) return;

    try {
      const payload = Buffer.from(encoded, "base64url");
      if (payload.length <= SealedValue.#IV_LENGTH + SealedValue.#TAG_LENGTH) return;

      const tagEnd = SealedValue.#IV_LENGTH + SealedValue.#TAG_LENGTH;
      const decipher = createDecipheriv("aes-256-gcm", this.#key, payload.subarray(0, SealedValue.#IV_LENGTH));
      decipher.setAAD(this.#context);
      decipher.setAuthTag(payload.subarray(SealedValue.#IV_LENGTH, tagEnd));
      return Buffer.concat([decipher.update(payload.subarray(tagEnd)), decipher.final()]).toString("utf8");
    } catch {
      return;
    }
  }
}
