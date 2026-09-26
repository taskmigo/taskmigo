import "server-only";

import { getConfig } from "@taskmigo/config/server";
import type { NextRequest, NextResponse } from "next/server";

import { getAuth } from "@/auth";

import { SessionBrowserApiAuthorizer } from "./auth/session-authorizer";
import { FetchBrowserApiBackend } from "./http/backend";
import { SameOriginBrowserApiRequestPolicy } from "./policy/request-policy";
import { BrowserApiProxy, type BrowserApiProxyContext } from "./proxy";

export class BrowserApiRouteHandler {
  readonly handle = (request: NextRequest, context: BrowserApiProxyContext): Promise<NextResponse> => {
    const { appUrl, apiInternalUrl } = getConfig();
    const { manager, sessions } = getAuth();
    return new BrowserApiProxy(
      new SameOriginBrowserApiRequestPolicy(appUrl, apiInternalUrl),
      new SessionBrowserApiAuthorizer(manager, sessions),
      new FetchBrowserApiBackend(fetch),
    ).handle(request, context);
  };
}

export const browserApiRouteHandler = new BrowserApiRouteHandler();
