import "server-only";

import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";

import type { AuthManager, Session } from "@taskmigo/auth";
import type { StringCodec } from "@taskmigo/foundation/state";
import { z } from "zod";

const claimSchema = z.discriminatedUnion("state", [
  z.object({ state: z.literal("ready"), payload: z.string(), generation: z.number().int().nonnegative() }),
  z.object({ state: z.literal("leader"), payload: z.string(), generation: z.number().int().nonnegative() }),
  z.object({ state: z.literal("wait"), retryAfterMilliseconds: z.number().int().positive() }),
]);

export class SessionReplicationMissingError extends Error {}

export class SessionRefreshUnavailableError extends Error {}

interface RefreshCoordinatorOptions {
  backendUrl: URL;
  internalSecret: string;
  sessionCodec: StringCodec<Session>;
  sessionMaxAgeSeconds: number;
  refreshSkewMilliseconds: number;
  refreshWaitMilliseconds: number;
  requestTimeoutMilliseconds: number;
}

export class RefreshCoordinator {
  readonly #backendUrl: URL;
  readonly #internalSecret: string;
  readonly #sessionCodec: StringCodec<Session>;
  readonly #sessionMaxAgeSeconds: number;
  readonly #refreshSkewMilliseconds: number;
  readonly #refreshWaitMilliseconds: number;
  readonly #requestTimeoutMilliseconds: number;

  constructor(options: RefreshCoordinatorOptions) {
    this.#backendUrl = new URL(options.backendUrl);
    this.#internalSecret = options.internalSecret;
    this.#sessionCodec = options.sessionCodec;
    this.#sessionMaxAgeSeconds = options.sessionMaxAgeSeconds;
    this.#refreshSkewMilliseconds = options.refreshSkewMilliseconds;
    this.#refreshWaitMilliseconds = options.refreshWaitMilliseconds;
    this.#requestTimeoutMilliseconds = options.requestTimeoutMilliseconds;
  }

  async register(session: Session, signal?: AbortSignal): Promise<void> {
    const response = await this.#request(this.#sessionUrl(session.id), {
      method: "PUT",
      body: JSON.stringify({
        payload: this.#sessionCodec.encode(session),
        tokenExpiresAt: session.expiresAt,
        expiresAt: Date.now() + this.#sessionMaxAgeSeconds * 1000,
      }),
      signal,
    });
    if (!response.ok) throw new SessionRefreshUnavailableError("BFF session replication is unavailable");
  }

  async remove(sessionId: string, signal?: AbortSignal): Promise<void> {
    const response = await this.#request(this.#sessionUrl(sessionId), { method: "DELETE", signal });
    if (!response.ok && response.status !== 404)
      throw new SessionRefreshUnavailableError("BFF session replication is unavailable");
  }

  async current(session: Session, manager: AuthManager, signal?: AbortSignal): Promise<Session> {
    if (session.expiresAt > Date.now() + this.#refreshSkewMilliseconds) return session;

    const owner = randomUUID();
    const deadline = Date.now() + this.#refreshWaitMilliseconds;

    while (Date.now() < deadline) {
      const claim = await this.#claim(session.id, owner, signal);
      if (claim.state === "wait") {
        await delay(claim.retryAfterMilliseconds, undefined, signal === undefined ? undefined : { signal });
        continue;
      }

      const shared = this.#decode(claim.payload);
      if (claim.state === "ready") return shared;

      try {
        const renewed = await manager.renew(shared);
        const completed = await this.#complete(renewed, owner, signal);
        if (!completed) throw new SessionRefreshUnavailableError("BFF refresh ownership was lost");
        return renewed;
      } catch (error) {
        await this.#release(session.id, owner, signal).catch(() => undefined);
        throw error;
      }
    }

    throw new SessionRefreshUnavailableError("Timed out waiting for the shared refresh result");
  }

  async authorize(
    session: Session,
    manager: AuthManager,
    signal?: AbortSignal,
  ): Promise<{ session: Session; accessToken: string }> {
    const current = await this.current(session, manager, signal);
    return { session: current, accessToken: manager.accessToken(current) };
  }

  async #claim(sessionId: string, owner: string, signal?: AbortSignal): Promise<z.output<typeof claimSchema>> {
    const response = await this.#request(new URL(`refresh-claims`, this.#sessionUrl(sessionId)), {
      method: "POST",
      body: JSON.stringify({ owner, refreshSkewMilliseconds: this.#refreshSkewMilliseconds }),
      signal,
    });
    if (response.status === 404) throw new SessionReplicationMissingError("BFF session replication is missing");
    if (!response.ok) throw new SessionRefreshUnavailableError("BFF refresh coordination is unavailable");
    return claimSchema.parse(await response.json());
  }

  async #complete(session: Session, owner: string, signal?: AbortSignal): Promise<boolean> {
    const response = await this.#request(
      new URL(`refresh-claims/${encodeURIComponent(owner)}`, this.#sessionUrl(session.id)),
      {
        method: "PUT",
        body: JSON.stringify({
          payload: this.#sessionCodec.encode(session),
          tokenExpiresAt: session.expiresAt,
        }),
        signal,
      },
    );
    if (response.status === 409) return false;
    if (!response.ok) throw new SessionRefreshUnavailableError("BFF refresh coordination is unavailable");
    return true;
  }

  async #release(sessionId: string, owner: string, signal?: AbortSignal): Promise<void> {
    const response = await this.#request(
      new URL(`refresh-claims/${encodeURIComponent(owner)}`, this.#sessionUrl(sessionId)),
      { method: "DELETE", signal },
    );
    if (!response.ok && response.status !== 404)
      throw new SessionRefreshUnavailableError("BFF refresh coordination is unavailable");
  }

  #decode(payload: string): Session {
    const session = this.#sessionCodec.decode(payload);
    if (!session) throw new SessionRefreshUnavailableError("BFF session replication returned invalid state");
    return session;
  }

  #sessionUrl(sessionId: string): URL {
    return new URL(
      `/_internal/bff/sessions/${encodeURIComponent(sessionId)}/`,
      this.#backendUrl,
    );
  }

  async #request(target: URL, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set("Content-Type", "application/json");
    headers.set("X-Taskmigo-BFF-Secret", this.#internalSecret);
    const timeout = AbortSignal.timeout(this.#requestTimeoutMilliseconds);
    const signal = init.signal === undefined ? timeout : AbortSignal.any([init.signal, timeout]);

    try {
      return await fetch(target, {
        ...init,
        headers,
        signal,
        cache: "no-store",
        redirect: "error",
      });
    } catch (error) {
      throw new SessionRefreshUnavailableError("BFF session coordinator request failed", { cause: error });
    }
  }
}
