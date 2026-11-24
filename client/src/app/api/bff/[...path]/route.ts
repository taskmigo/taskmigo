import { browserApiRouteHandler } from "@/server/bff/route-handler";

export const runtime = "nodejs";

export const DELETE = browserApiRouteHandler.handle;
export const GET = browserApiRouteHandler.handle;
export const PATCH = browserApiRouteHandler.handle;
export const POST = browserApiRouteHandler.handle;
export const PUT = browserApiRouteHandler.handle;
