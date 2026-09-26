import { type NextRequest, NextResponse } from "next/server";

import { getAuth } from "@/auth";

export const runtime = "nodejs";

export async function POST(request: NextRequest): Promise<NextResponse> {
  const { manager, sessions, transactions, refreshCoordinator } = getAuth();
  const session = sessions.read(request.cookies);
  if (session) await refreshCoordinator.remove(session.id, request.signal).catch(() => undefined);
  const response = NextResponse.redirect(await manager.signOut(session), 303);
  sessions.clear(response.cookies);
  transactions.clear(response.cookies);
  return response;
}
