import { type NextRequest } from "next/server";

import { getAuth } from "@/auth";

export const runtime = "nodejs";

export const POST = (request: NextRequest) => getAuth().handlers.logout(request);
