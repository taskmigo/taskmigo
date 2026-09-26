import { type NextRequest, NextResponse } from "next/server";

export interface BrowserApiAuthorization {
  readonly accessToken: string;
  persist(response: NextResponse): void;
}

export interface BrowserApiAuthorizer {
  authorize(request: NextRequest): Promise<BrowserApiAuthorization | undefined>;
  clear(response: NextResponse): void;
}

export interface BrowserApiBackend {
  forward(request: NextRequest, url: URL, accessToken: string): Promise<NextResponse | undefined>;
}

export interface BrowserApiRequestPolicy {
  allows(request: NextRequest): boolean;
  resolveBackendUrl(request: NextRequest, path: string[]): URL | undefined;
}

export interface BrowserApiProxyContext {
  params: Promise<{ path: string[] }>;
}

export class BrowserApiProxy {
  static readonly #PRIVATE_NO_STORE = "private, no-store";

  readonly #requestPolicy: BrowserApiRequestPolicy;
  readonly #authorizer: BrowserApiAuthorizer;
  readonly #backend: BrowserApiBackend;

  constructor(requestPolicy: BrowserApiRequestPolicy, authorizer: BrowserApiAuthorizer, backend: BrowserApiBackend) {
    this.#requestPolicy = requestPolicy;
    this.#authorizer = authorizer;
    this.#backend = backend;
  }

  async handle(request: NextRequest, context: BrowserApiProxyContext): Promise<NextResponse> {
    if (!this.#requestPolicy.allows(request)) {
      return BrowserApiProxy.#error(403, "Cross-origin browser API request rejected");
    }

    const { path } = await context.params;
    const url = this.#requestPolicy.resolveBackendUrl(request, path);
    if (!url) {
      return BrowserApiProxy.#error(404, "Backend API route not found");
    }

    const authorization = await this.#authorizer.authorize(request);
    if (!authorization) {
      return this.#unauthorized();
    }

    const response = await this.#backend.forward(request, url, authorization.accessToken);
    if (!response) {
      return BrowserApiProxy.#error(502, "Backend API unavailable");
    }

    authorization.persist(response);
    return response;
  }

  #unauthorized(): NextResponse {
    const response = BrowserApiProxy.#error(401, "Authentication required");
    this.#authorizer.clear(response);
    return response;
  }

  static #error(status: number, error: string): NextResponse {
    return NextResponse.json({ error }, { status, headers: { "Cache-Control": BrowserApiProxy.#PRIVATE_NO_STORE } });
  }
}
