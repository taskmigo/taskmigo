export class OriginScope {
  readonly #baseHref: string;
  readonly #origin: string;

  constructor(baseUrl: URL) {
    const base = new URL(baseUrl);
    this.#baseHref = base.href;
    this.#origin = base.origin;
  }

  get baseUrl(): URL {
    return new URL(this.#baseHref);
  }

  resolve(candidate: string | URL): URL | undefined {
    try {
      const resolved = new URL(candidate, this.#baseHref);
      return resolved.origin === this.#origin ? resolved : undefined;
    } catch {
      return;
    }
  }

  path(candidate: string): string | undefined {
    if (!candidate.startsWith("/")) return;

    const resolved = this.resolve(candidate);
    return resolved ? `${resolved.pathname}${resolved.search}${resolved.hash}` : undefined;
  }
}
