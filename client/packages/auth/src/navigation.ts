export interface AuthNavigationOptions {
  appUrl: URL;
  callbackUrl: URL;
  postLogoutRedirectUrl: URL;
  defaultReturnTo: string;
}

export class AuthNavigation {
  readonly #appHref: string;
  readonly #appOrigin: string;
  readonly #callbackHref: string;
  readonly #postLogoutRedirectHref: string;
  readonly #defaultReturnTo: string;

  constructor({ appUrl, callbackUrl, postLogoutRedirectUrl, defaultReturnTo }: AuthNavigationOptions) {
    const app = new URL(appUrl);
    this.#appHref = app.href;
    this.#appOrigin = app.origin;
    this.#callbackHref = this.#requireSameOriginUrl(callbackUrl, "Callback URL").href;
    this.#postLogoutRedirectHref = this.#requireSameOriginUrl(postLogoutRedirectUrl, "Post-logout redirect URL").href;
    this.#defaultReturnTo = this.#requireLocalTarget(defaultReturnTo, "Default return target");
  }

  get callbackUrl(): URL {
    return new URL(this.#callbackHref);
  }

  get postLogoutRedirectUrl(): URL {
    return new URL(this.#postLogoutRedirectHref);
  }

  resolveReturnTo(candidate?: string | null): string {
    return (candidate && this.#localTarget(candidate)) || this.#defaultReturnTo;
  }

  returnToUrl(candidate: string): URL {
    return new URL(this.resolveReturnTo(candidate), this.#appHref);
  }

  #localTarget(candidate: string): string | undefined {
    if (!candidate.startsWith("/")) return;

    try {
      const resolved = new URL(candidate, this.#appHref);
      if (resolved.origin !== this.#appOrigin) return;
      return `${resolved.pathname}${resolved.search}${resolved.hash}`;
    } catch {
      return;
    }
  }

  #requireLocalTarget(candidate: string, name: string): string {
    const target = this.#localTarget(candidate);
    if (!target) throw new Error(`${name} must resolve to the application origin`);
    return target;
  }

  #requireSameOriginUrl(candidate: URL, name: string): URL {
    const resolved = new URL(candidate);
    if (resolved.origin !== this.#appOrigin) throw new Error(`${name} must resolve to the application origin`);
    return resolved;
  }
}
