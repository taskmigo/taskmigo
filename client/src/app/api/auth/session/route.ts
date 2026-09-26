import { type NextRequest, NextResponse } from "next/server";

import { getAuth } from "@/auth";

export const runtime = "nodejs";

function noStore(response: NextResponse): NextResponse {
  response.headers.set("Cache-Control", "no-store");
  return response;
}

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { manager, sessions } = getAuth();
  const session = sessions.read(request.cookies);
  if (!session) return noStore(NextResponse.json({ authenticated: false }));

  try {
    const current = await manager.renew(session);
    const response = noStore(NextResponse.json({ authenticated: true, user: current.user }));
    if (current !== session) sessions.write(response.cookies, current);
    return response;
  } catch {
    const response = noStore(NextResponse.json({ authenticated: false }));
    sessions.clear(response.cookies);
    return response;
  }
}
