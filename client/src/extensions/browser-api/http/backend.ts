import { type NextRequest, NextResponse } from "next/server";

import type { BrowserApiBackend } from "../proxy";

type FetchBackend = (request: Request, init: RequestInit) => Promise<Response>;

export class FetchBrowserApiBackend implements BrowserApiBackend {
  static readonly #HOP_BY_HOP_HEADERS = new Set([
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "proxy-connection",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
  ]);
  static readonly #REQUEST_HEADERS_TO_REMOVE = new Set([
    ...FetchBrowserApiBackend.#HOP_BY_HOP_HEADERS,
    "authorization",
    "content-length",
    "cookie",
    "forwarded",
    "host",
  ]);
  static readonly #RESPONSE_HEADERS_TO_REMOVE = new Set([...FetchBrowserApiBackend.#HOP_BY_HOP_HEADERS, "set-cookie"]);
  static readonly #PRIVATE_NO_STORE = "private, no-store";

  readonly #fetch: FetchBackend;

  constructor(fetchBackend: FetchBackend) {
    this.#fetch = fetchBackend;
  }

  async forward(request: NextRequest, url: URL, accessToken: string): Promise<NextResponse | undefined> {
    try {
      const upstream = await this.#fetch(this.#toBackendRequest(request, url, accessToken), {
        cache: "no-store",
        redirect: "manual",
      });
      return this.#toBrowserResponse(upstream);
    } catch {
      return;
    }
  }

  #toBackendRequest(request: NextRequest, url: URL, accessToken: string): Request {
    const upstream = new Request(url, request);
    this.#removeHeaders(upstream.headers, FetchBrowserApiBackend.#REQUEST_HEADERS_TO_REMOVE, "x-forwarded-");
    upstream.headers.set("Accept-Encoding", "identity");
    upstream.headers.set("Authorization", `Bearer ${accessToken}`);
    return upstream;
  }

  #toBrowserResponse(upstream: Response): NextResponse {
    const response = new NextResponse(upstream.body, upstream);
    this.#removeHeaders(response.headers, FetchBrowserApiBackend.#RESPONSE_HEADERS_TO_REMOVE);
    response.headers.set("Cache-Control", FetchBrowserApiBackend.#PRIVATE_NO_STORE);
    return response;
  }

  #removeHeaders(headers: Headers, blocked: ReadonlySet<string>, blockedPrefix?: string): void {
    for (const name of new Headers(headers).keys()) {
      if (blocked.has(name) || (blockedPrefix !== undefined && name.startsWith(blockedPrefix))) {
        headers.delete(name);
      }
    }
  }
}
