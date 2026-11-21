export interface StringCodec<T> {
  encode(value: T): string;
  decode(value: string | undefined): T | undefined;
}

export interface CookieAttributes {
  httpOnly: boolean;
  sameSite: "strict" | "lax" | "none";
  secure: boolean;
  path: string;
  maxAge: number;
}

export interface CookieReader {
  get(name: string): { value: string } | undefined;
}

export interface CookieWriter<A extends object> {
  set(name: string, value: string, attributes: A): unknown;
  delete(name: string): unknown;
}

export interface CookieStateOptions<T, A extends object> {
  name: string;
  codec: StringCodec<T>;
  attributes: A;
}

export class CookieState<T, A extends object = CookieAttributes> {
  readonly name: string;
  readonly attributes: A;
  readonly #codec: StringCodec<T>;

  constructor({ name, codec, attributes }: CookieStateOptions<T, A>) {
    this.name = name;
    this.#codec = codec;
    this.attributes = Object.freeze({ ...attributes }) as A;
  }

  read(cookies: CookieReader): T | undefined {
    return this.#codec.decode(cookies.get(this.name)?.value);
  }

  write(cookies: CookieWriter<A>, value: T): void {
    cookies.set(this.name, this.#codec.encode(value), this.attributes);
  }

  clear(cookies: Pick<CookieWriter<A>, "delete">): void {
    cookies.delete(this.name);
  }
}
