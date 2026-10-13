# Phase 4 Web Package Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Web executable application's remaining Hexagonal-direction package vocabulary with API, Security, and OAuth responsibility packages without changing HTTP, security, session, OAuth, authorization, filtering, or OpenAPI behavior.

**Architecture:** Keep `server/apps/web` as one executable Spring boundary and reorganize only its internal packages. Move HTTP/MVC code to `web.api`, servlet/security/session integration to `web.security`, Authorization Server and OAuth persistence/signing-key code to `web.oauth`, and create `web.configuration` only if implementation discovers genuinely cross-cutting Web wiring with no more precise owner.

**Tech Stack:** Java 26, Gradle 9.7.1, Spring Boot, Spring MVC, Spring Security, Spring Session JDBC, Spring Authorization Server, Spring Modulith, ArchUnit, JUnit 5, Mockito, AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-12-phase-4-web-package-simplification-design.md`

## Global Constraints

- `server/apps/web` remains the Web executable application root.
- Preserve public HTTP paths, methods, request/response contracts, media types, API versioning, and OpenAPI output.
- Preserve Spring Security authentication and authorization behavior.
- Preserve session storage, invalidation, principal indexing, 30-minute inactivity timeout, `JSESSIONID` cookie behavior, and user-session lifecycle behavior.
- Preserve OAuth 2.0 / Authorization Server endpoints, persistence, consent, issuer/security behavior, and signing-key behavior.
- Preserve Request Authorization, Object Authorization, query/filter argument resolution, and object-filtering semantics.
- Preserve the existing Spring Modulith Web module and its current `allowedDependencies`; do not broaden module dependencies.
- Do not retain compatibility aliases or duplicate old/new packages; Taskmigo is v0.
- Worker and Migration are out of scope.
- Do not introduce a top-level `web.configuration` package unless a concrete class has no more precise API, Security, or OAuth owner.
- Do not delete or change the shared `HexagonalOnionRules` helper in this slice; only Web stops using its driving-adapter rule.
- No unrelated persistence, transaction, dependency, authorization-policy, workflow, or product refactor is included.
- Production source bodies stay semantically unchanged except for package/import updates, responsibility documentation, and architecture enforcement required by the move.

## Review Focus

- OpenAPI generation must still execute after its test package moves; `:apps:web:generateOpenApi` must target `io.taskmigo.web.api.OpenApiGenerationIntegrationTest` and produce no snapshot diff.
- API-v0 authentication and authorization failures must still use the v0 response contract while shared `web.security` remains independent of `web.api.v0`; pin this with `VersionedApiSecurityErrorHandlerTest` and `ApiV0SecurityErrorRendererTest`.
- Session revocation and serialized `UserSessionPrincipal` behavior must survive moving `HttpSessionConfiguration` and the guarded repository into `web.security.session`; pin this with `HttpSessionRevocationIntegrationTest`, `GuardedSessionRepositoryTest`, and `SessionPrincipalSerializationTest`.
- Spring MVC Object Authorization route/schema discovery must preserve method/path matching and Query Schema selection after the target resolver moves; pin this with `SpringMvcObjectAuthorizationTargetResolverTest` and `ObjectAuthorizationRouteIntegrationTest`.
- OAuth persistence, signing keys, interactive authentication, and OIDC provider discovery must survive splitting `composition.auth`; pin this with `GuardedOAuthPersistenceTest`, `SigningKeyStoreTest`, and `InteractiveAuthenticationIntegrationTest`.

---

### Task 1: Establish the RED Web responsibility-package gate

**Files:**

- Create: `server/apps/web/src/test/java/io/taskmigo/web/WebPackageArchitectureTest.java`

**Interfaces:**

- Consumes: production classes under `io.taskmigo.web..` through ArchUnit.
- Produces: the TDD regression gate that forbids the retired Web roots before any production package is moved.

- [ ] **Step 1: Write the failing retired-root architecture test**

Create `WebPackageArchitectureTest` with a test named `shouldRejectRetiredWebPackagesWhenProductionPackagesAreInspected`. Import production classes with `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS` and assert that no class resides in any of:

```text
io.taskmigo.web.adapter..
io.taskmigo.web.composition..
io.taskmigo.rest..
io.taskmigo.internal..
```

Do not add the final responsibility whitelist or `security -> api.v0` dependency rule yet; the RED proof should have one obvious reason for failure.

- [ ] **Step 2: Run the architecture test and record the RED proof**

Run from `server/`:

```bash
./gradlew :apps:web:test --tests 'io.taskmigo.web.WebPackageArchitectureTest'
```

Expected: FAIL because current production classes still reside under `io.taskmigo.web.adapter..` and `io.taskmigo.web.composition..`. Confirm the failure is not compilation, Spring startup, or an unrelated architecture violation.

- [ ] **Step 3: Commit the RED checkpoint**

Run from repository root:

```bash
git add server/apps/web/src/test/java/io/taskmigo/web/WebPackageArchitectureTest.java
git commit -m "test(web): require responsibility packages"
```

### Task 2: Move the API surface and MVC support

**Files:**

- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/api/**` -> `server/apps/web/src/main/java/io/taskmigo/web/api/**`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/support/objectauthorization/**` -> `server/apps/web/src/main/java/io/taskmigo/web/api/support/objectauthorization/**`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/support/query/**` -> `server/apps/web/src/main/java/io/taskmigo/web/api/support/query/**`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/support/versioning/**` -> `server/apps/web/src/main/java/io/taskmigo/web/api/support/versioning/**`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/support/package-info.java` -> `server/apps/web/src/main/java/io/taskmigo/web/api/support/package-info.java`.
- Move: `server/apps/web/src/test/java/io/taskmigo/web/adapter/in/http/api/**` -> `server/apps/web/src/test/java/io/taskmigo/web/api/**`.
- Move: `server/apps/web/src/test/java/io/taskmigo/web/adapter/in/http/support/objectauthorization/**` -> `server/apps/web/src/test/java/io/taskmigo/web/api/support/objectauthorization/**`.
- Move: `server/apps/web/src/test/java/io/taskmigo/web/adapter/in/http/support/query/**` -> `server/apps/web/src/test/java/io/taskmigo/web/api/support/query/**`.
- Move/rename: `server/apps/web/src/test/java/io/taskmigo/web/adapter/in/http/HttpPackageArchitectureTest.java` -> `server/apps/web/src/test/java/io/taskmigo/web/api/ApiPackageArchitectureTest.java`.
- Modify: `server/apps/web/build.gradle.kts`.

**Interfaces:**

- Produces: `io.taskmigo.web.api..` as the HTTP/API runtime responsibility and `io.taskmigo.web.api.support..` as version-neutral MVC support.
- Preserves: existing controller classes, `ApiResponse`, API-v0 response/security implementations, query/object-authorization argument resolvers, `ApiVersioningConfiguration`, and `OpenApiConfiguration` with unchanged runtime behavior.
- Temporarily consumes: the still-legacy `ApiSecurityErrorRenderer` package until Task 3 moves that shared security contract.

- [ ] **Step 1: Move the production API tree without semantic edits**

Move `OpenApiConfiguration`, all v0 controllers/support classes, and their `package-info.java` files from `adapter.in.http.api` to the same relative paths beneath `web.api`. Update package declarations and imports only.

- [ ] **Step 2: Move version-neutral MVC support**

Move exactly these production classes with their package metadata:

```text
AuthorizationContextArgumentResolver
ObjectAuthorizationPredicateArgumentResolver
FilteredQueryArgumentResolver
QuerySupportConfiguration
ApiVersioningConfiguration
```

Their targets are respectively `web.api.support.objectauthorization`, `web.api.support.query`, and `web.api.support.versioning`. Preserve resolver order, annotations, method signatures, and configuration behavior.

- [ ] **Step 3: Move API and MVC-support tests with their production owners**

Move the complete `adapter.in.http.api` test tree to `web.api`, including `OpenApiGenerationIntegrationTest`, v0 integration tests, and the v0 test-support helpers. Move the Object Authorization argument-resolver and filtered-query unit tests to the corresponding `web.api.support` packages. Change package/import references only.

- [ ] **Step 4: Replace the HTTP-named architecture test with the API-named rule set**

Rename `HttpPackageArchitectureTest` to `ApiPackageArchitectureTest`, move it to `io.taskmigo.web.api`, and retarget its existing rules to:

```text
io.taskmigo.web.api..
io.taskmigo.web.api.support..
io.taskmigo.web.api.v0.auth.authorization..
io.taskmigo.web.api.v0.auth.group..
io.taskmigo.web.api.v0.auth.user..
```

Preserve the existing guarantees: no `io.taskmigo.internal..` dependency, shared API support does not depend on `api.v0`, Authorization/Group/User feature packages remain isolated, and API code does not depend on `org.springframework.transaction..`.

- [ ] **Step 5: Retarget the OpenAPI Gradle test filters**

In `server/apps/web/build.gradle.kts`, replace both occurrences of the old OpenAPI test FQCN with:

```text
io.taskmigo.web.api.OpenApiGenerationIntegrationTest
```

Keep the ordinary `test` exclusion and `generateOpenApi` inclusion behavior unchanged.

- [ ] **Step 6: Run API-focused verification**

Run from `server/`:

```bash
./gradlew :apps:web:test \
  --tests 'io.taskmigo.web.api.ApiPackageArchitectureTest' \
  --tests 'io.taskmigo.web.api.v0.ApiVersioningIntegrationTest' \
  --tests 'io.taskmigo.web.api.v0.MalformedInputApiIntegrationTest' \
  --tests 'io.taskmigo.web.api.v0.MediaTypeApiIntegrationTest' \
  --tests 'io.taskmigo.web.api.support.objectauthorization.*' \
  --tests 'io.taskmigo.web.api.support.query.*'
./gradlew :apps:web:generateOpenApi
git diff --exit-code -- apps/web/src/main/resources/static/api/docs/openapi.yaml
```

Expected: targeted tests PASS; OpenAPI generation runs the moved test; the committed snapshot has no diff.

- [ ] **Step 7: Commit the API move**

Run from repository root:

```bash
git add server/apps/web
git commit -m "refactor(web): organize api packages"
```

### Task 3: Move Security, session, and Object Authorization runtime integration

**Files:**

- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/security/ApiSecurityConfiguration.java` -> `server/apps/web/src/main/java/io/taskmigo/web/security/ApiSecurityConfiguration.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/security/RequestAuthorizationManager.java` -> `server/apps/web/src/main/java/io/taskmigo/web/security/RequestAuthorizationManager.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/security/VersionedApiSecurityErrorHandler.java` -> `server/apps/web/src/main/java/io/taskmigo/web/security/VersionedApiSecurityErrorHandler.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/security/package-info.java` -> `server/apps/web/src/main/java/io/taskmigo/web/security/package-info.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/support/security/ApiSecurityErrorRenderer.java` -> `server/apps/web/src/main/java/io/taskmigo/web/security/ApiSecurityErrorRenderer.java`.
- Delete after move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/support/security/package-info.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/security/session/**` -> `server/apps/web/src/main/java/io/taskmigo/web/security/session/**`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/HttpSessionConfiguration.java` -> `server/apps/web/src/main/java/io/taskmigo/web/security/session/HttpSessionConfiguration.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/out/objectauthorization/SpringMvcObjectAuthorizationTargetResolver.java` -> `server/apps/web/src/main/java/io/taskmigo/web/security/objectauthorization/SpringMvcObjectAuthorizationTargetResolver.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/adapter/out/objectauthorization/package-info.java` -> `server/apps/web/src/main/java/io/taskmigo/web/security/objectauthorization/package-info.java`.
- Move matching tests from `adapter.in.security..`, `adapter.out.objectauthorization..`, and the session-focused tests currently under `composition.auth` to `web.security..`.
- Create: `server/apps/web/src/test/java/io/taskmigo/web/security/VersionedApiSecurityErrorHandlerTest.java`.
- Create: `server/apps/web/src/test/java/io/taskmigo/web/api/v0/support/security/ApiV0SecurityErrorRendererTest.java`.
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/api/v0/support/security/ApiV0SecurityErrorRenderer.java`.

**Interfaces:**

- Produces: `web.security.ApiSecurityErrorRenderer` as the generic response-rendering contract; `web.api.v0.support.security.ApiV0SecurityErrorRenderer` implements it.
- Produces: `web.security.session.GuardedSessionRepository`, `HttpSessionConfiguration`, and `UserSessionPrincipal` as one session-security responsibility.
- Produces: `web.security.objectauthorization.SpringMvcObjectAuthorizationTargetResolver` as the Spring MVC route/schema bridge for Object Authorization.
- Preserves: `ObjectAuthorizationTargetResolver`, `RequestAuthorizationManager`, Spring Security entry-point/access-denied contracts, and Spring Session repository behavior.

- [ ] **Step 1: Move the shared Security classes and invert the API error-renderer dependency around Security ownership**

Move the three existing `adapter.in.security` classes and `ApiSecurityErrorRenderer` into `web.security`. Update `ApiV0SecurityErrorRenderer` to import `io.taskmigo.web.security.ApiSecurityErrorRenderer`; do not make `VersionedApiSecurityErrorHandler` depend on `web.api.v0`.

- [ ] **Step 2: Add unit coverage for shared security-error dispatch**

Add `VersionedApiSecurityErrorHandlerTest` in `web.security`. Pin both existing branches:

- when multiple renderers exist, the first renderer whose `supports(request)` returns `true` receives the status/code/text and later renderers are not invoked;
- when no renderer supports the request, `HttpServletResponse.sendError(status.value(), messageText)` is used.

Exercise `commence` with `401` / `security.unauthorized` and `handle` with `403` / `security.forbidden` without changing production behavior.

- [ ] **Step 3: Add unit coverage for the v0 renderer contract**

Add `ApiV0SecurityErrorRendererTest` in `web.api.v0.support.security`. Assert that:

- `supports` is true for `/api/v0/...` and false for another API version/path;
- `write` sets the requested HTTP status and `application/json` content type;
- `401` requests call `ApiResponseFactory.failureBody` with error code `UNAUTHORIZED`;
- `403` requests call it with error code `FORBIDDEN`.

Mock `ApiResponseFactory`, `ObjectMapper`, and servlet request/response dependencies so the test pins delegation and envelope selection rather than duplicating JSON serialization implementation.

- [ ] **Step 4: Move the session-security classes together**

Move `GuardedSessionRepository`, `UserSessionPrincipal`, their package metadata, and `HttpSessionConfiguration` into `web.security.session`. Preserve `FlushMode.ON_SAVE`, the 30-minute inactivity interval, principal-name index value derived from `userId`, `JSESSIONID`, and delegation to `UserSessionLifecycleService`.

Move these tests to `web.security.session` and change only package/import references unless required to keep package-private coverage:

```text
GuardedSessionRepositoryTest
HttpSessionRevocationIntegrationTest
SessionPrincipalSerializationTest
```

- [ ] **Step 5: Move the Object Authorization MVC target resolver**

Move `SpringMvcObjectAuthorizationTargetResolver` and package metadata to `web.security.objectauthorization`. Move `SpringMvcObjectAuthorizationTargetResolverTest` and `ObjectAuthorizationRouteIntegrationTest` to the same test package. Preserve `@Component`, `@Primary`, `SmartInitializingSingleton`, method/path matching, API version substitution, exactly-one-predicate enforcement, `@QueryOperation` lookup, and exactly-one-schema enforcement.

- [ ] **Step 6: Move the request-authorization test**

Move `RequestAuthorizationManagerTest` to `io.taskmigo.web.security` and update only package/import references.

- [ ] **Step 7: Run Security/session/Object Authorization verification**

Run from `server/`:

```bash
./gradlew :apps:web:test \
  --tests 'io.taskmigo.web.security.RequestAuthorizationManagerTest' \
  --tests 'io.taskmigo.web.security.VersionedApiSecurityErrorHandlerTest' \
  --tests 'io.taskmigo.web.api.v0.support.security.ApiV0SecurityErrorRendererTest' \
  --tests 'io.taskmigo.web.security.session.*' \
  --tests 'io.taskmigo.web.security.objectauthorization.*'
```

Expected: PASS with no change to security error envelopes, session lifecycle behavior, or Object Authorization route resolution.

- [ ] **Step 8: Commit the Security move**

Run from repository root:

```bash
git add server/apps/web
git commit -m "refactor(web): organize security packages"
```

### Task 4: Split OAuth ownership out of the old composition package

**Files:**

- Move: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/AuthorizationServerConfiguration.java` -> `server/apps/web/src/main/java/io/taskmigo/web/oauth/AuthorizationServerConfiguration.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/GuardedOAuth2AuthorizationConsentService.java` -> `server/apps/web/src/main/java/io/taskmigo/web/oauth/GuardedOAuth2AuthorizationConsentService.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/GuardedOAuth2AuthorizationService.java` -> `server/apps/web/src/main/java/io/taskmigo/web/oauth/GuardedOAuth2AuthorizationService.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/IdentityProperties.java` -> `server/apps/web/src/main/java/io/taskmigo/web/oauth/IdentityProperties.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/OAuthPersistenceConfiguration.java` -> `server/apps/web/src/main/java/io/taskmigo/web/oauth/OAuthPersistenceConfiguration.java`.
- Move: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/SigningKeyStore.java` -> `server/apps/web/src/main/java/io/taskmigo/web/oauth/SigningKeyStore.java`.
- Move/rewrite: `server/apps/web/src/main/java/io/taskmigo/web/composition/auth/package-info.java` -> `server/apps/web/src/main/java/io/taskmigo/web/oauth/package-info.java`.
- Move: `server/apps/web/src/test/java/io/taskmigo/web/composition/auth/GuardedOAuthPersistenceTest.java` -> `server/apps/web/src/test/java/io/taskmigo/web/oauth/GuardedOAuthPersistenceTest.java`.
- Move: `server/apps/web/src/test/java/io/taskmigo/web/composition/auth/SigningKeyStoreTest.java` -> `server/apps/web/src/test/java/io/taskmigo/web/oauth/SigningKeyStoreTest.java`.
- Move: `server/apps/web/src/test/java/io/taskmigo/web/composition/auth/InteractiveAuthenticationIntegrationTest.java` -> `server/apps/web/src/test/java/io/taskmigo/web/oauth/InteractiveAuthenticationIntegrationTest.java`.

**Interfaces:**

- Produces: `web.oauth` as the single owner of Authorization Server configuration, OAuth authorization/consent persistence guards, `taskmigo.oauth` properties, persistence wiring, and signing keys.
- Preserves: package-private collaboration among OAuth-owned classes; no visibility broadening is expected.

- [ ] **Step 1: Move all remaining OAuth production collaborators together**

Move the six classes above into `web.oauth`, update package declarations/imports, and keep existing class visibility. `IdentityProperties` retains `@ConfigurationProperties(prefix = "taskmigo.oauth")` and its existing fields unchanged.

- [ ] **Step 2: Rewrite OAuth package documentation**

Move `composition.auth/package-info.java` to `oauth/package-info.java` and describe Authorization Server, OAuth persistence/consent guarding, and signing-key ownership without composition/adapter terminology.

- [ ] **Step 3: Move OAuth-focused tests**

Move `GuardedOAuthPersistenceTest`, `SigningKeyStoreTest`, and `InteractiveAuthenticationIntegrationTest` to `io.taskmigo.web.oauth`. Preserve all assertions, including OIDC discovery containing `authorization_endpoint`, `token_endpoint`, `jwks_uri`, and `end_session_endpoint`.

- [ ] **Step 4: Run OAuth verification**

Run from `server/`:

```bash
./gradlew :apps:web:test \
  --tests 'io.taskmigo.web.oauth.GuardedOAuthPersistenceTest' \
  --tests 'io.taskmigo.web.oauth.SigningKeyStoreTest' \
  --tests 'io.taskmigo.web.oauth.InteractiveAuthenticationIntegrationTest'
```

Expected: PASS; no OAuth/Authorization Server behavior or signing-key semantics change.

- [ ] **Step 5: Confirm the legacy composition tree is gone and commit**

Run from repository root:

```bash
find server/apps/web/src/main/java/io/taskmigo/web/composition -type f -print
```

Expected: no files. Remove empty directories if the filesystem still contains them, then commit:

```bash
git add server/apps/web
git commit -m "refactor(web): organize oauth packages"
```

### Task 5: Replace Web's Hexagonal architecture vocabulary with final responsibility rules

**Files:**

- Modify: `server/apps/web/src/test/java/io/taskmigo/web/WebPackageArchitectureTest.java`.
- Modify: `server/apps/web/src/test/java/io/taskmigo/ModulithArchitectureTest.java`.
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/package-info.java`.
- Modify: moved `package-info.java` files under `server/apps/web/src/main/java/io/taskmigo/web/api/**`, `security/**`, and `oauth/**` only where text still uses retired Hexagonal-direction terminology.

**Interfaces:**

- Produces: the final Web package contract: `api`, `security`, `oauth`, and optionally `configuration` are the only top-level production responsibilities below `io.taskmigo.web`.
- Preserves: Spring Modulith `ApplicationModules.verify()` and the existing `web` module `allowedDependencies` list byte-for-byte except documentation text around it.

- [ ] **Step 1: Expand `WebPackageArchitectureTest` to the final package contract**

Keep the retired-root rule and add a responsibility whitelist for non-`package-info` production classes under `io.taskmigo.web..`: they must reside in one of:

```text
io.taskmigo.web.api..
io.taskmigo.web.security..
io.taskmigo.web.oauth..
io.taskmigo.web.configuration..
```

Allow `configuration` but do not create it merely to satisfy the rule. Add a dependency rule asserting classes in `io.taskmigo.web.security..` do not depend on classes in `io.taskmigo.web.api.v0..`.

- [ ] **Step 2: Remove Web's `HexagonalOnionRules.checkDrivingApplication(...)` test**

In `ModulithArchitectureTest`, delete only the `HexagonalOnionRules` import and `shouldKeepDrivingAdaptersOnInboundPortsWhenWebPackagesAreInspected` test. Keep `ApplicationModules.of(TaskmigoApplication.class).verify()`, the Spring Modulith metadata rule, and the query-schema retirement check.

Move the existing `io.taskmigo.rest..` / `io.taskmigo.internal..` Web-package assertion out of `ModulithArchitectureTest` if it would duplicate `WebPackageArchitectureTest`; one authoritative Web package rule is sufficient.

- [ ] **Step 3: Rewrite root Web package documentation without changing module dependencies**

Change the root package description from “driving adapters, framework-specific driven adapters, and composition” to responsibility-oriented language covering the Web executable's API, Security/session, and OAuth runtime integration. Do not change any `@ApplicationModule(allowedDependencies = {...})` entry.

- [ ] **Step 4: Sweep moved package documentation and source references for stale architecture vocabulary**

Run from repository root:

```bash
rg -n 'io\.taskmigo\.web\.(adapter|composition)|driving adapter|driven adapter|inbound port|outbound port|Hexagonal' \
  server/apps/web/src/main/java/io/taskmigo/web \
  server/apps/web/src/test/java/io/taskmigo/web
```

Expected after documentation/package-reference cleanup: no obsolete Web package path or normative Hexagonal-direction wording remains. Do not rewrite unrelated comments that use “adapter” in a concrete framework-integration sense unless they describe the retired package architecture.

- [ ] **Step 5: Run final architecture and Modulith verification**

Run from `server/`:

```bash
./gradlew :apps:web:test \
  --tests 'io.taskmigo.web.WebPackageArchitectureTest' \
  --tests 'io.taskmigo.web.api.ApiPackageArchitectureTest' \
  --tests 'io.taskmigo.ModulithArchitectureTest'
```

Expected: PASS. `ApplicationModules.verify()` must not report a broadened or invalid explicit module dependency.

- [ ] **Step 6: Confirm all retired production roots are physically empty and commit**

Run from repository root:

```bash
find server/apps/web/src/main/java/io/taskmigo/web/adapter -type f -print
find server/apps/web/src/main/java/io/taskmigo/web/composition -type f -print
```

Expected: no files. Then commit:

```bash
git add server/apps/web
git commit -m "test(web): enforce responsibility boundaries"
```

### Task 6: Run complete Web and repository verification on one exact SHA

**Files:**

- No planned production changes.
- Modify only files required to correct failures caused by the approved package move; do not weaken tests, architecture rules, or CI configuration.

**Interfaces:**

- Consumes: final API, Security, OAuth, and architecture packages from Tasks 2-5.
- Produces: one exact reviewable child-branch SHA with all required local and CI evidence green.

- [ ] **Step 1: Run the complete Web test suite**

Run from `server/`:

```bash
./gradlew :apps:web:test
```

Expected: PASS, including API versioning/malformed/media-type coverage, Request/Object Authorization, session/security, OAuth, Spring Modulith, and architecture tests.

- [ ] **Step 2: Verify the OpenAPI snapshot independently**

Run from `server/` exactly as Server CI does:

```bash
./gradlew --no-daemon --console=plain :apps:web:generateOpenApi
git diff --exit-code -- apps/web/src/main/resources/static/api/docs/openapi.yaml
```

Expected: generation succeeds and the committed OpenAPI YAML is unchanged.

- [ ] **Step 3: Run the full Server build exactly as the Server workflow does**

Run from `server/`:

```bash
./gradlew --no-daemon --console=plain --continue build
```

Expected: PASS.

- [ ] **Step 4: Run repository formatting and confirm it is clean**

Run from repository root:

```bash
npm ci
npm run format:fix
git diff --exit-code
```

Expected: formatting makes no uncommitted changes. If it changes files, review those changes, commit only legitimate formatting, and rerun Steps 1-4.

- [ ] **Step 5: Perform final package/import sweeps**

Run from repository root:

```bash
rg -n 'io\.taskmigo\.web\.(adapter|composition)' server/apps/web server/modules || true
find server/apps/web/src/main/java/io/taskmigo/web -name '*.java' -print
```

Expected: no stale imports or production package declarations reference the retired Web roots; production classes are under `web.api`, `web.security`, or `web.oauth`, with `web.configuration` absent unless implementation found and justified a real owner.

- [ ] **Step 6: Commit any verification-only formatting correction**

Only if Step 4 produced legitimate formatting changes, run from repository root:

```bash
git add -A
git commit -m "style(web): format package simplification"
```

Otherwise do not create an empty commit.

- [ ] **Step 7: Push the child branch and verify all required checks on the exact final SHA**

Required green checks before declaring this slice complete:

```text
Server
Formatting
Markdown
Authorization performance
Qodana
Kubernetes Integration
```

For Kubernetes Integration, confirm the deployment, Helm integration tests, and browser E2E steps succeed. Do not mark the slice complete from stale runs on an earlier SHA.

- [ ] **Step 8: Review the final diff for semantic drift**

Compare against the Phase 4 Web base and confirm changes are limited to package/file moves, package/import references, Web architecture tests, package documentation, the OpenAPI Gradle FQCN filters, and the two security-error regression tests. Reject accidental controller logic, security policy, session parameters, OAuth configuration, persistence logic, query/filter logic, or `@ApplicationModule.allowedDependencies` changes.

- [ ] **Step 9: Prepare the child PR for review without merging it**

The PR summary must state that this is the **Phase 4 Web package simplification** child slice for #252, identify the final responsibility roots (`api`, `security`, `oauth`), record the RED architecture proof, and list the exact final SHA and green CI checks. Worker package simplification remains the next Phase 4 checkpoint.
