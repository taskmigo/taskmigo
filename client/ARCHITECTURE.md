# Client architecture

The client is a single Next.js application organized by business domain. Keep business behavior, its UI, and its tests together instead of grouping all application code by technical type.

## Source ownership

- `src/domains/identity/` owns the current identity domain: sign-in, sign-out, session renewal, safe navigation, OIDC integration, encrypted authentication cookies, and the signed-in account screen. The domain exposes its server-only authentication contract through `@/domains/identity`, its HTTP handlers through `@/domains/identity/http`, and its account screen through `@/domains/identity/account-page`.
- `src/app/` owns Next.js routing, route configuration, layouts, and the application landing page. Route entry points delegate to domain screens and handlers or the BFF. Route paths and runtime declarations remain here.
- `src/server/` owns cross-domain server infrastructure. Its configuration module validates environment settings, and its BFF module forwards authorized browser API requests to the backend. The BFF uses the public identity contract, never its cookie or OIDC implementation.

## Dependency rules

- App route entry points consume public domain entry points or server infrastructure. They do not import private domain files.
- Domain code must not depend on the app routing tree or BFF transport. Domains may use server configuration and their own internal adapters.
- The BFF depends on the public identity contract, not the private session storage.
- Keep tests beside the code they verify. Route integration tests stay with the route entry points so they verify actual exports.
- Add sibling domains when implementing new business capabilities. Do not create empty modules for features that are not implemented. Avoid a generic foundation or shared module unless multiple independent domains actually need the same abstraction.

Preserve the server-only boundary, public HTTP paths, cookie and token formats, and session semantics during internal refactors.
