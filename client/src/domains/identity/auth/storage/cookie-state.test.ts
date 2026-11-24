import { describe, expect, test, vi } from "vitest";

import { CookieState, type StringCodec } from "./cookie-state";

type Subject = { subject: string };

describe("CookieState", () => {
  const attributes = { httpOnly: true, sameSite: "lax" as const, secure: true, path: "/", maxAge: 60 };

  test("composes a string codec with cookie storage without owning the transport", () => {
    const codec: StringCodec<Subject> = {
      encode: vi.fn(() => "encoded"),
      decode: vi.fn((value) => (value === "encoded" ? { subject: "developer" } : undefined)),
    };
    const state = new CookieState({ name: "state", codec, attributes });
    const reader = { get: vi.fn(() => ({ value: "encoded" })) };
    const writer = { set: vi.fn(), delete: vi.fn() };

    expect(state.read(reader)).toEqual({ subject: "developer" });
    state.write(writer, { subject: "developer" });
    state.clear(writer);

    expect(writer.set).toHaveBeenCalledWith("state", "encoded", attributes);
    expect(writer.delete).toHaveBeenCalledWith("state");
    expect(state.name).toBe("state");
    expect(state.attributes).toEqual(attributes);
    expect(Object.isFrozen(state.attributes)).toBe(true);
  });

  test("passes missing cookie values through the codec", () => {
    const decode = vi.fn((): Subject | undefined => {
      return;
    });
    const state = new CookieState<Subject>({
      name: "state",
      codec: { encode: () => "encoded", decode },
      attributes,
    });

    expect(
      state.read({
        get: (): { value: string } | undefined => {
          return;
        },
      }),
    ).toBeUndefined();
    expect(decode).toHaveBeenCalledWith(undefined);
  });
});
