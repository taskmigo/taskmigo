import { describe, expect, test } from "vitest";
import { z } from "zod";

import { safeReturnTo } from "./return-to";
import { createSealedCookie } from "./sealed-cookie";

const secret = "01234567890123456789012345678901";

describe("authentication primitives", () => {
  test("rejects external return targets", () => {
    expect(safeReturnTo("//attacker.example/path", "/account")).toBe("/account");
    expect(safeReturnTo(String.raw`/\attacker.example/path`, "/account")).toBe("/account");
    expect(safeReturnTo("https://attacker.example/path", "/account")).toBe("/account");
    expect(safeReturnTo("/projects?mine=true", "/account")).toBe("/projects?mine=true");
  });

  test("separates and validates encrypted cookies", () => {
    const schema = z.object({ subject: z.string() });
    const session = createSealedCookie({ name: "session", purpose: "session", schema, secret, maxAge: 60 });
    const transaction = createSealedCookie({
      name: "transaction",
      purpose: "transaction",
      schema,
      secret,
      maxAge: 60,
    });
    const value = session.encode({ subject: "developer" });

    expect(session.decode(value)).toEqual({ subject: "developer" });
    expect(transaction.decode(value)).toBeUndefined();
  });
});
