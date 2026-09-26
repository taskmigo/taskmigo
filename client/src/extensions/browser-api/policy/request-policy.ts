import type { NextRequest } from "next/server";

import type { BrowserApiRequestPolicy } from "../proxy";

export class SameOriginBrowserApiRequestPolicy implements BrowserApiRequestPolicy {
  static readonly #SAFE_METHODS = new Set(["GET", "HEAD"]);
  static readonly #VERSION_PATTERN = /^v\d+$/;

  readonly #appOrigin: string;
  readonly #apiInternalUrl: URL;

  constructor(appUrl: URL, apiInternalUrl: URL) {
    this.#appOrigin = appUrl.origin;
    this.#apiInternalUrl = new URL(apiInternalUrl);
  }

  allows(request: NextRequest): boolean {
    return (
      SameOriginBrowserApiRequestPolicy.#SAFE_METHODS.has(request.method) ||
      request.headers.get("origin") === this.#appOrigin
    );
  }

  resolveBackendUrl(request: NextRequest, path: string[]): URL | undefined {
    if (
      !SameOriginBrowserApiRequestPolicy.#VERSION_PATTERN.test(path[0] ?? "") ||
      path.some((segment) => segment === "." || segment === "..")
    ) {
      return;
    }

    const url = new URL(`/api/${path.map((segment) => encodeURIComponent(segment)).join("/")}`, this.#apiInternalUrl);
    url.search = request.nextUrl.search;
    return url;
  }
}
