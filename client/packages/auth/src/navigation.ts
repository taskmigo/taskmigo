import { OriginScope } from "@taskmigo/foundation/url";

export interface AuthNavigationOptions {
  appUrl: URL;
  callbackUrl: URL;
  postLogoutRedirectUrl: URL;
  defaultReturnTo: string;
}

export class AuthNavigation {
  readonly #scope: OriginScope;
  readonly #callbackHref: string;
  readonly #postLogoutRedirectHref: string;
  readonly #defaultReturnTo: string;

  constructor({ appUrl, callbackUrl, postLogoutRedirectUrl, defaultReturnTo }: AuthNavigationOptions) {
    this.#scope = new OriginScope(appUrl);
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
    return (candidate && this.#scope.path(candidate)) || this.#defaultReturnTo;
  }

  returnToUrl(candidate: string): URL {
    return new URL(this.resolveReturnTo(candidate), this.#scope.baseUrl);
  }

  #requireLocalTarget(candidate: string, name: string): string {
    const target = this.#scope.path(candidate);
    if (!target) throw new Error(`${name} must resolve to the application origin`);
    return target;
  }

  #requireSameOriginUrl(candidate: URL, name: string): URL {
    const resolved = this.#scope.resolve(candidate);
    if (!resolved) throw new Error(`${name} must resolve to the application origin`);
    return resolved;
  }
}
