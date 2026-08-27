import { type NextRequest } from "next/server";

import { getAuth } from "@/auth";

export const runtime = "nodejs";

export const GET = (request: NextRequest) => getAuth().handlers.login(request);
