import { SessionRenewalRejectedError } from "@taskmigo/auth";
import { type NextRequest, NextResponse } from "next/server";

import { getAuth } from "@/auth";
import { SessionReplicationMissingError } from "@/auth/refresh-coordinator";

export const runtime = "nodejs";

function noStore(response: NextResponse): NextResponse {
  response.headers.set("Cache-Control", "no-store");
  return response;
}

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { manager, sessions, refreshCoordinator } = getAuth();
  const session = sessions.read(request.cookies);
  if (!session) return noStore(NextResponse.json({ authenticated: false }));

  try {
    const current = await refreshCoordinator.current(session, manager, request.signal);
    const response = noStore(NextResponse.json({ authenticated: true, user: current.user }));
    if (current !== session) sessions.write(response.cookies, current);
    return response;
  } catch (error) {
    if (error instanceof SessionRenewalRejectedError || error instanceof SessionReplicationMissingError) {
      const response = noStore(NextResponse.json({ authenticated: false }));
      sessions.clear(response.cookies);
      return response;
    }
    return noStore(
      NextResponse.json(
        {
          type: "about:blank",
          title: "Service Unavailable",
          status: 503,
          detail: "Authentication refresh is temporarily unavailable",
        },
        { status: 503, headers: { "Content-Type": "application/problem+json" } },
      ),
    );
  }
}
