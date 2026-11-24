import { type NextRequest, NextResponse } from "next/server";

import { getAuth } from "@/domains/identity";

export async function handleLogin(request: NextRequest): Promise<NextResponse> {
  const { manager, returnToParameter, transactions } = getAuth();

  try {
    const { redirectTo, transaction } = await manager.beginSignIn(request.nextUrl.searchParams.get(returnToParameter));
    const response = NextResponse.redirect(redirectTo);
    transactions.write(response.cookies, transaction);
    return response;
  } catch {
    return NextResponse.json({ error: "Browser authentication is not configured correctly" }, { status: 500 });
  }
}
