import { type NextRequest, NextResponse } from "next/server";

import { getAuth } from "@/auth";

export const runtime = "nodejs";

export async function POST(request: NextRequest): Promise<NextResponse> {
  const { manager, sessions, transactions } = getAuth();
  const response = NextResponse.redirect(await manager.signOut(sessions.read(request.cookies)), 303);
  sessions.clear(response.cookies);
  transactions.clear(response.cookies);
  return response;
}
