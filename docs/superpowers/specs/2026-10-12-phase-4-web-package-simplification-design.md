# Phase 4 Web Package Simplification Design

## Status

Approved design for the **Phase 4 — Web package simplification** slice of #252.

This slice starts from the current #253 integration head and changes only the Web executable application. Worker and Migration remain separate later Phase 4 slices.

## Goal

Replace the Web application's remaining Hexagonal-direction package vocabulary with responsibility-oriented runtime packages while preserving every externally observable behavior.

The target is not a new architecture for Web. Web is already an executable Spring runtime boundary. The change makes its package tree describe what the runtime code does instead of whether code was previously classified as an inbound adapter, outbound adapter, or composition component.

The stable top-level responsibilities are:

```text
io.taskmigo.web
  api/
  security/
  oauth/
  configuration/   # only when genuinely cross-cutting application wiring exists
```

`configuration` is an allowed responsibility, not a required symmetry package. This slice MUST NOT create an empty or artificial `configuration` package if every existing configuration class has a more precise API, Security, or OAuth owner.

## Constraints

This slice MUST preserve the #252 constraints:

- `server/apps/web` remains the Web executable application root.
- Public HTTP paths, methods, request/response contracts, media types, API versioning, and OpenAPI output do not change.
- Spring Security authentication/authorization behavior does not change.
- Session storage, invalidation, principal indexing, cookie behavior, and user-session lifecycle behavior do not change.
- OAuth 2.0 / Authorization Server behavior, persistence, signing-key behavior, and consent behavior do not change.
- Request Authorization and Object Authorization semantics do not change.
- Query/filter argument resolution and object filtering behavior do not change.
- Spring Modulith module ownership and published module dependencies do not broaden.
- No compatibility aliases or duplicate old/new package structures are retained; Taskmigo is v0.
- Worker and Migration are out of scope.
- No unrelated dependency, transaction, persistence, authorization-policy, or product refactor is included.

## Current structure and problem

The Web production tree still exposes the previous architecture vocabulary:

```text
io.taskmigo.web
  adapter/
    in/
      http/
      security/
    out/
      objectauthorization/
  composition/
    auth/
```

The direction labels are no longer useful boundaries:

- `adapter.in.http` contains controllers, API versioning, MVC argument resolvers, response handling, and other HTTP concerns.
- `adapter.in.security` contains Spring Security, request authorization, session security, and security error handling.
- `adapter.out.objectauthorization` contains a Spring MVC integration that derives Object Authorization target metadata.
- `composition.auth` mixes OAuth Authorization Server concerns with HTTP session configuration.

The existing Web architecture test also still describes Web in Hexagonal terms by calling `HexagonalOnionRules.checkDrivingApplication(...)` and referring to driving adapters and inbound ports. That terminology is specifically non-normative under #252.

## Design principles

### 1. Responsibility over direction

Packages answer “what runtime responsibility owns this class?” rather than “which side of a Hexagonal port is this class on?”.

### 2. Keep the existing feature/version boundaries

This is not a controller reorganization. Existing API version and feature isolation remains valuable and is preserved beneath `web.api`.

### 3. Keep framework code at the executable edge

Web is allowed to use Spring MVC, Spring Security, Spring Session, OAuth/Authorization Server, JDBC integration, servlet APIs, and other runtime framework APIs. Reusable business logic remains in server modules.

### 4. Do not create fake layers

There is no requirement for Web to contain domain/application/persistence layers. `configuration` is not created merely to complete a four-folder pattern.

### 5. Preserve dependency direction where it expresses real responsibility

Generic security infrastructure must not depend on a specific API version. API-version-specific renderers may implement a generic security-owned rendering contract, but shared security code must remain independent of `api.v0`.

## Target package structure

The intended production shape is:

```text
io.taskmigo.web
  api/
    OpenApiConfiguration
    v0/
      audit/
      auth/
        authorization/
        group/
        user/
      configuration/
      support/
        pagination/
        response/
        security/
    support/
      objectauthorization/
      query/
      versioning/

  security/
    ApiSecurityConfiguration
    ApiSecurityErrorRenderer
    RequestAuthorizationManager
    VersionedApiSecurityErrorHandler
    objectauthorization/
      SpringMvcObjectAuthorizationTargetResolver
    session/
      GuardedSessionRepository
      HttpSessionConfiguration
      UserSessionPrincipal

  oauth/
    AuthorizationServerConfiguration
    GuardedOAuth2AuthorizationConsentService
    GuardedOAuth2AuthorizationService
    IdentityProperties
    OAuthPersistenceConfiguration
    SigningKeyStore

  configuration/   # only if a real cross-cutting owner is discovered during implementation
```

Package-info files move with their owned packages and are rewritten only where their descriptions still use retired Hexagonal terminology.

## Ownership mapping

| Current owner | Target owner | Rationale |
| --- | --- | --- |
| `web.adapter.in.http.api..` | `web.api..` | Versioned HTTP/API surface is already a coherent runtime responsibility. |
| `web.adapter.in.http.support.objectauthorization..` | `web.api.support.objectauthorization..` | These are MVC argument resolvers that adapt controller parameters. |
| `web.adapter.in.http.support.query..` | `web.api.support.query..` | Query/filter argument resolution is HTTP/MVC API integration. |
| `web.adapter.in.http.support.versioning..` | `web.api.support.versioning..` | API version negotiation/configuration belongs to the API surface. |
| `web.adapter.in.http.support.security.ApiSecurityErrorRenderer` | `web.security.ApiSecurityErrorRenderer` | The contract is consumed by shared security handling and must be independent of a concrete API version. |
| `web.adapter.in.http.api.v0.support.security.ApiV0SecurityErrorRenderer` | `web.api.v0.support.security.ApiV0SecurityErrorRenderer` | The implementation owns the v0 response envelope and therefore stays with API v0 while implementing the security-owned contract. |
| `web.adapter.in.security..` | `web.security..` | Spring Security, request authorization, and security error dispatch are one runtime responsibility. |
| `web.adapter.in.security.session..` | `web.security.session..` | Session principal/repository guarding is part of authentication/session security. |
| `web.adapter.out.objectauthorization.SpringMvcObjectAuthorizationTargetResolver` | `web.security.objectauthorization.SpringMvcObjectAuthorizationTargetResolver` | It bridges Spring MVC route metadata into Object Authorization target resolution, so its responsibility is runtime authorization integration rather than a generic “out adapter”. |
| `web.composition.auth.HttpSessionConfiguration` | `web.security.session.HttpSessionConfiguration` | It configures the guarded Spring Session repository, principal index, timeout, and cookie behavior; it is not OAuth-specific. |
| remaining `web.composition.auth..` | `web.oauth..` | Authorization Server configuration, OAuth persistence wrappers, OAuth properties, consent/authorization guards, and signing keys form one OAuth runtime responsibility. |

`IdentityProperties` moves with OAuth because its configuration prefix is `taskmigo.oauth` and its fields configure OAuth signing-key persistence.

## API boundary design

The existing API hierarchy is preserved after dropping the obsolete `adapter.in.http` prefix.

For example:

```text
io.taskmigo.web.adapter.in.http.api.v0.auth.user.UserController
```

becomes:

```text
io.taskmigo.web.api.v0.auth.user.UserController
```

No controller responsibility, endpoint mapping, serialization model, validation behavior, or feature grouping changes.

Shared MVC support that is independent of API version stays under `web.api.support`. Version-specific support stays beneath the version that owns its response contract, such as `web.api.v0.support.response` and `web.api.v0.support.security`.

The existing feature-isolation rules between Authorization, Group, and User API packages remain mandatory.

## Security boundary design

`web.security` owns the servlet/Spring Security runtime edge:

- security filter-chain configuration;
- request authorization manager integration;
- authentication/authorization failure dispatch;
- shared API security rendering contract;
- Spring Session guarding and user-session lifecycle integration;
- Spring MVC route metadata integration needed by Object Authorization.

The security error-rendering dependency is intentionally inverted around a security-owned contract:

```text
web.security.VersionedApiSecurityErrorHandler
                 |
                 v
web.security.ApiSecurityErrorRenderer
                 ^
                 |
web.api.v0.support.security.ApiV0SecurityErrorRenderer
```

This keeps shared security code independent of API v0 while allowing each API version to own its response envelope.

The Object Authorization MVC responsibilities remain separated:

- controller parameter adaptation stays in `web.api.support.objectauthorization`;
- route/schema discovery used to resolve authorization targets moves to `web.security.objectauthorization`.

No authorization expressions, target matching, query schemas, or runtime authorization semantics change.

## OAuth boundary design

`web.oauth` owns the Spring Authorization Server and OAuth persistence runtime:

- Authorization Server configuration;
- guarded `OAuth2AuthorizationService` and consent service wrappers;
- OAuth JDBC persistence configuration;
- signing-key storage and creation;
- `taskmigo.oauth` configuration properties.

The current `composition.auth` package is therefore removed rather than renamed wholesale. `HttpSessionConfiguration` is separated into `web.security.session`, while the genuinely OAuth-owned classes move together to `web.oauth` so their existing package-private collaboration can remain package-private.

OAuth endpoint behavior, token/consent persistence, issuer/security configuration, and signing-key semantics are unchanged.

## Configuration responsibility

`web.configuration` is reserved for application-wide Web wiring that genuinely spans API, Security, and OAuth and has no more precise owner.

The current tree does not justify moving any known class there merely for symmetry. If implementation confirms that no such class exists, no `web.configuration` package or `package-info.java` is created in this slice.

## Spring and Modulith behavior

All moved production classes remain beneath `io.taskmigo.web`, so normal application component scanning remains within the same executable boundary.

The root `io.taskmigo.web` Spring Modulith module remains the same module. Its allowed dependency set is not broadened as part of this refactor. The root package documentation is updated from “driving adapters / driven adapters / composition” language to responsibility-oriented Web runtime language.

`ApplicationModules.verify()` remains mandatory and must pass after the move.

## Architecture enforcement

### RED proof

Before moving production code, add a Web package-architecture regression rule that rejects production classes under retired roots:

```text
io.taskmigo.web.adapter..
io.taskmigo.web.composition..
```

The RED checkpoint must fail against the current Web tree for those legacy roots. The failure should demonstrate the package problem being migrated, not an unrelated compilation or behavior failure.

### Final Web rules

After the move, Web architecture enforcement must guarantee at least:

- production classes no longer reside under `web.adapter..` or `web.composition..`;
- production Web classes use only the approved responsibility roots `api`, `security`, `oauth`, and, only when justified, `configuration`, except root package metadata;
- the existing retired `io.taskmigo.rest..` and `io.taskmigo.internal..` roots remain forbidden;
- shared API support does not depend on version-specific API code;
- Authorization, Group, and User API feature packages remain isolated from sibling feature packages;
- API code does not own Spring transaction behavior;
- shared `web.security` code does not depend on `web.api.v0..`;
- Spring Modulith verification remains green.

The existing Web test that calls `HexagonalOnionRules.checkDrivingApplication(...)` is removed/replaced for Web. The shared Hexagonal compatibility helper itself is not deleted in this slice because Worker/Migration and Phase 5 consolidation are separate checkpoints.

Architecture tests and test packages that mirror production packages move to the new responsibility paths so tests do not preserve retired vocabulary.

## Implementation shape

The implementation should remain a package/ownership refactor rather than a behavior rewrite:

1. Add the RED retired-package/responsibility-root architecture guard.
2. Move API production packages and mirrored tests, updating imports and ArchUnit selectors.
3. Move Security/session production packages and tests, including the generic API security-rendering contract.
4. Move Spring MVC Object Authorization target resolution to Security ownership.
5. Split the old `composition.auth` package into `oauth` and `security.session` based on actual responsibility.
6. Update root/package documentation and remove Web's Hexagonal-specific architecture test vocabulary.
7. Delete the retired package paths in the same change; do not leave aliases.
8. Run targeted Web tests, module verification, full server verification, and repository CI gates.

If a move exposes a previously hidden package-private dependency that contradicts the target ownership, implementation must resolve ownership explicitly rather than making classes public merely to preserve the old package shape. Such a discovery is part of this Web slice only when it is necessary to complete the approved ownership model without changing behavior.

## Testing and verification

The final child-PR SHA must verify:

- the RED architecture proof was observed before production moves;
- Web package/ArchUnit architecture tests;
- Web API integration tests, including API versioning and malformed/media-type behavior;
- Request/Object Authorization Web integration tests;
- session/security integration behavior;
- OAuth/Authorization Server integration behavior already covered by the server suite;
- Spring Modulith `ApplicationModules.verify()`;
- OpenAPI snapshot generation/verification;
- full Server workflow;
- Formatting;
- Markdown;
- Authorization performance;
- Qodana;
- Kubernetes Integration, including Helm and browser E2E checks.

No CI check is weakened or bypassed to make this refactor pass.

## Risks and mitigations

### Spring component discovery

**Risk:** a configuration/component move could stop bean discovery.

**Mitigation:** all classes remain under the existing scanned `io.taskmigo.web` boundary; full application startup/integration tests and Kubernetes deployment remain required.

### Package-private collaboration

**Risk:** splitting `composition.auth` could reveal accidental same-package coupling.

**Mitigation:** OAuth-owned collaborators move together; `HttpSessionConfiguration` has an independent Security/session responsibility. Do not widen visibility unless a real cross-package contract is required and justified.

### Security response behavior

**Risk:** moving the security-rendering contract could accidentally couple shared security to API v0 or alter error envelopes.

**Mitigation:** keep the contract security-owned, the v0 implementation API-v0-owned, and retain the existing generic-handler-to-versioned-renderer delegation behavior.

### Object Authorization route discovery

**Risk:** relocating the Spring MVC target resolver could alter component registration or route/schema discovery.

**Mitigation:** preserve the component annotations, initialization lifecycle, route matching, Query Schema lookup, and authorization-target interface unchanged; verify authorization and API integration tests.

### OAuth/session wiring

**Risk:** the old `composition.auth` package currently hides two responsibilities, so a mechanical rename could keep that ambiguity or break package-private wiring.

**Mitigation:** split by runtime responsibility, keep OAuth collaborators together, and move session configuration with the existing guarded session classes.

### Large package move noise

**Risk:** broad file moves can obscure accidental behavior edits.

**Mitigation:** keep source bodies unchanged except imports, package declarations, documentation that names retired architecture concepts, and architecture-test changes required by the package move. Review the final diff specifically for non-package semantic changes.

## Acceptance criteria

The Web slice is complete when all of the following are true:

- no Web production class remains under `io.taskmigo.web.adapter..` or `io.taskmigo.web.composition..`;
- Web's top-level package vocabulary describes API, Security, OAuth, and only genuinely needed cross-cutting configuration responsibilities;
- no empty/fake package is introduced for symmetry;
- Web architecture tests no longer require driving-adapter/inbound-port terminology;
- API version and feature boundaries remain enforced;
- shared security remains independent of API v0;
- Spring Modulith module dependencies are not broadened;
- public HTTP/OpenAPI behavior is unchanged;
- security, session, OAuth, authorization, query/filtering, and object-filtering behavior is unchanged;
- old package paths are removed without compatibility aliases;
- Worker and Migration are untouched;
- all required targeted and repository-wide CI checks pass on one exact final SHA.

After this slice is merged into the #253 integration branch, the next Phase 4 checkpoint is **Worker package simplification**.