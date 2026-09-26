import { type NextRequest, NextResponse } from "next/server";

import { getConfig } from "@taskmigo/config/server";

import { getAuth } from "@/auth";

export const runtime = "nodejs";

const UPSTREAM_API_PREFIX = "/api/v0";
const BFF_API_PREFIX = "/backend/v0";
const BFF_PROXY_NAME = "taskmigo-bff";
const SAFE_METHODS = new Set(["GET", "HEAD"]);
const ALLOWED_FETCH_SITES = new Set(["same-origin", "none"]);
const UNSAFE_PATH_SEGMENTS = new Set(["", ".", ".."]);
const HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);
const OWNED_REQUEST_HEADERS = new Set([
  "authorization",
  "content-length",
  "cookie",
  "forwarded",
  "host",
  "via",
  "x-forwarded-for",
  "x-forwarded-host",
  "x-forwarded-port",
  "x-forwarded-prefix",
  "x-forwarded-proto",
]);
const BLOCKED_RESPONSE_HEADERS = new Set([...HOP_BY_HOP_HEADERS, "content-encoding", "content-length", "set-cookie"]);

interface RouteContext {
  params: Promise<{ path: string[] }>;
}

function protectedResponse(response: NextResponse): NextResponse {
  response.headers.set("Cache-Control", "no-store");
  response.headers.set("Cross-Origin-Resource-Policy", "same-origin");
  return response;
}

function problemResponse(status: number, title: string, detail: string): NextResponse {
  const response = NextResponse.json(
    {
      type: "about:blank",
      title,
      status,
      detail,
    },
    { status },
  );
  response.headers.set("Content-Type", "application/problem+json");
  return protectedResponse(response);
}

function isTrustedBrowserRequest(request: NextRequest, appUrl: URL): boolean {
  const fetchSite = request.headers.get("sec-fetch-site");
  if (fetchSite !== null && !ALLOWED_FETCH_SITES.has(fetchSite)) return false;
  if (SAFE_METHODS.has(request.method)) return true;
  return request.headers.get("origin") === appUrl.origin;
}

function hasUnsafePathSegment(path: string[]): boolean {
  return path.some((segment) => UNSAFE_PATH_SEGMENTS.has(segment));
}

function upstreamUrl(backendUrl: URL, path: string[], search: string): URL {
  const encodedPath = path.map((segment) => encodeURIComponent(segment)).join("/");
  const target = new URL(`${UPSTREAM_API_PREFIX}/${encodedPath}`, backendUrl);
  target.search = search;
  return target;
}

function connectionHeaderNames(headers: Headers): Set<string> {
  return new Set(
    (headers.get("connection") ?? "")
      .split(",")
      .map((name) => name.trim().toLowerCase())
      .filter(Boolean),
  );
}

function copyEndToEndHeaders(source: Headers, blocked: Set<string>): Headers {
  const connectionHeaders = connectionHeaderNames(source);
  const headers = new Headers();
  source.forEach((value, name) => {
    const normalized = name.toLowerCase();
    if (!blocked.has(normalized) && !connectionHeaders.has(normalized)) headers.append(name, value);
  });
  return headers;
}

function publicPort(appUrl: URL): string {
  if (appUrl.port) return appUrl.port;
  return appUrl.protocol === "https:" ? "443" : "80";
}

function upstreamHeaders(source: Headers, accessToken: string, appUrl: URL): Headers {
  const headers = copyEndToEndHeaders(source, new Set([...HOP_BY_HOP_HEADERS, ...OWNED_REQUEST_HEADERS]));
  headers.set("Authorization", `Bearer ${accessToken}`);
  headers.set("Accept-Encoding", "identity");
  headers.set(
    "Forwarded",
    `by=_${BFF_PROXY_NAME};host=${JSON.stringify(appUrl.host)};proto=${appUrl.protocol.slice(0, -1)}`,
  );
  headers.set("Via", `1.1 ${BFF_PROXY_NAME}`);
  headers.set("X-Forwarded-Host", appUrl.host);
  headers.set("X-Forwarded-Port", publicPort(appUrl));
  headers.set("X-Forwarded-Prefix", BFF_API_PREFIX);
  headers.set("X-Forwarded-Proto", appUrl.protocol.slice(0, -1));
  return headers;
}

function rewriteBackendLocation(value: string, requestUrl: URL, backendUrl: URL): string {
  let target: URL;
  try {
    target = new URL(value, requestUrl);
  } catch {
    return value;
  }

  if (target.origin !== backendUrl.origin) return value;

  const suffix =
    target.pathname === UPSTREAM_API_PREFIX || target.pathname.startsWith(`${UPSTREAM_API_PREFIX}/`)
      ? `${BFF_API_PREFIX}${target.pathname.slice(UPSTREAM_API_PREFIX.length)}`
      : target.pathname;
  return `${suffix}${target.search}${target.hash}`;
}

function downstreamHeaders(source: Headers, requestUrl: URL, backendUrl: URL): Headers {
  const headers = copyEndToEndHeaders(source, BLOCKED_RESPONSE_HEADERS);
  for (const name of [...headers.keys()]) {
    if (name.toLowerCase().startsWith("access-control-")) headers.delete(name);
  }

  for (const name of ["location", "content-location"]) {
    const value = headers.get(name);
    if (value !== null) headers.set(name, rewriteBackendLocation(value, requestUrl, backendUrl));
  }

  const upstreamVia = headers.get("via");
  headers.set("Via", upstreamVia === null ? `1.1 ${BFF_PROXY_NAME}` : `${upstreamVia}, 1.1 ${BFF_PROXY_NAME}`);
  headers.set("Cache-Control", "no-store");
  headers.set("Cross-Origin-Resource-Policy", "same-origin");
  return headers;
}

async function proxy(request: NextRequest, context: RouteContext): Promise<NextResponse> {
  const config = getConfig();

  if (!isTrustedBrowserRequest(request, config.appUrl)) {
    return problemResponse(403, "Forbidden", "Cross-origin backend requests are not allowed");
  }

  const { path } = await context.params;
  if (hasUnsafePathSegment(path)) return problemResponse(400, "Bad Request", "Invalid backend path");

  const { manager, sessions } = getAuth();
  const session = sessions.read(request.cookies);
  if (!session) return problemResponse(401, "Unauthorized", "Authentication is required");

  let credential;
  try {
    credential = await manager.getAccessToken(session);
  } catch {
    const response = problemResponse(401, "Unauthorized", "Authentication is required");
    sessions.clear(response.cookies);
    return response;
  }

  const target = upstreamUrl(config.backend.url, path, request.nextUrl.search);
  const initialRequest = new Request(target, request);
  const upstreamRequest = new Request(initialRequest, {
    headers: upstreamHeaders(initialRequest.headers, credential.accessToken, config.appUrl),
  });
  const timeoutSignal = AbortSignal.timeout(config.backend.timeoutMilliseconds);

  let upstream: Response;
  try {
    upstream = await fetch(upstreamRequest, {
      redirect: "manual",
      signal: AbortSignal.any([request.signal, timeoutSignal]),
    });
  } catch {
    const response = timeoutSignal.aborted
      ? problemResponse(504, "Gateway Timeout", "Backend timed out")
      : problemResponse(502, "Bad Gateway", "Backend unavailable");
    if (credential.session !== session) sessions.write(response.cookies, credential.session);
    return response;
  }

  const response = new NextResponse(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: downstreamHeaders(upstream.headers, target, config.backend.url),
  });
  if (credential.session !== session) sessions.write(response.cookies, credential.session);
  return response;
}

export const GET = proxy;
export const HEAD = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
export const OPTIONS = proxy;
