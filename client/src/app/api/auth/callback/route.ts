import { type NextRequest, NextResponse } from "next/server";

import { getAuth } from "@/auth";

export const runtime = "nodejs";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { manager, sessions, transactions } = getAuth();
  const transaction = transactions.read(request.cookies);

  if (!transaction) {
    const response = NextResponse.json({ error: "Login transaction is missing or expired" }, { status: 400 });
    transactions.clear(response.cookies);
    return response;
  }

  try {
    const { redirectTo, session } = await manager.completeSignIn(new URL(request.url), transaction);
    const response = NextResponse.redirect(redirectTo);
    sessions.write(response.cookies, session);
    transactions.clear(response.cookies);
    return response;
  } catch (error) {
    console.error("OAuth callback validation failed", error);
    const response = NextResponse.json({ error: "Login callback could not be validated" }, { status: 400 });
    transactions.clear(response.cookies);
    return response;
  }
}
