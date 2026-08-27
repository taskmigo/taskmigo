export interface AuthNavigationOptions {
  callbackPath?: string;
  postLogoutRedirectPath?: string;
  defaultReturnTo?: string;
}

export class AuthNavigation {
  private static readonly DEFAULT_CALLBACK_PATH = "/api/auth/callback";
  private static readonly DEFAULT_POST_LOGOUT_REDIRECT_PATH = "/";
  private static readonly DEFAULT_RETURN_TO = "/account";

  private readonly appHref: string;
  private readonly appOrigin: string;
  private readonly callbackHref: string;
  private readonly postLogoutRedirectHref: string;
  private readonly defaultReturnTo: string;

  constructor(appUrl: URL, options: AuthNavigationOptions = {}) {
    const app = new URL(appUrl);
    this.appHref = app.href;
    this.appOrigin = app.origin;
    this.callbackHref = this.requireSameOriginUrl(
      options.callbackPath ?? AuthNavigation.DEFAULT_CALLBACK_PATH,
      "Callback URL",
    ).href;
    this.postLogoutRedirectHref = this.requireSameOriginUrl(
      options.postLogoutRedirectPath ?? AuthNavigation.DEFAULT_POST_LOGOUT_REDIRECT_PATH,
      "Post-logout redirect URL",
    ).href;
    this.defaultReturnTo = this.requireLocalTarget(
      options.defaultReturnTo ?? AuthNavigation.DEFAULT_RETURN_TO,
      "Default return target",
    );
  }

  get callbackUrl(): URL {
    return new URL(this.callbackHref);
  }

  get postLogoutRedirectUrl(): URL {
    return new URL(this.postLogoutRedirectHref);
  }

  resolveReturnTo(candidate?: string | null): string {
    return (candidate && this.localTarget(candidate)) || this.defaultReturnTo;
  }

  returnToUrl(candidate: string): URL {
    return new URL(this.resolveReturnTo(candidate), this.appHref);
  }

  private localTarget(candidate: string): string | undefined {
    if (!candidate.startsWith("/")) return;

    try {
      const resolved = new URL(candidate, this.appHref);
      if (resolved.origin !== this.appOrigin) return;
      return `${resolved.pathname}${resolved.search}${resolved.hash}`;
    } catch {
      return;
    }
  }

  private requireLocalTarget(candidate: string, name: string): string {
    const target = this.localTarget(candidate);
    if (!target) throw new Error(`${name} must resolve to the application origin`);
    return target;
  }

  private requireSameOriginUrl(candidate: string, name: string): URL {
    const resolved = new URL(candidate, this.appHref);
    if (resolved.origin !== this.appOrigin) throw new Error(`${name} must resolve to the application origin`);
    return resolved;
  }
}
