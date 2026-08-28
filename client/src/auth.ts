import "server-only";

import { AuthRuntime } from "./auth/runtime";

export const getAuth = () => AuthRuntime.get().auth;
