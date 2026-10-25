# Query and Authorization Contract DX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate query and authorization contracts at the controller boundary while keeping Web endpoint authoring concise and allowing workers to authorize explicitly as any user.

**Architecture:** Controllers declare independent `@QueryContract` and `@AuthorizationContract` metadata. Spring MVC injects the resolved Object Authorization predicate from the authorization annotation, while non-Web callers use an explicit authorization application API with `AuthorizationPrincipal` + `AuthorizationRequest`; feature services continue receiving raw query intent plus an opaque authorization predicate and persistence keeps SQL push-down.

**Tech Stack:** Java 25, Spring MVC/Spring Security, Springdoc OpenAPI, Spring Modulith, Gradle, JUnit 5, Mockito.

**Spec:** Conversation-approved redesign for issue #288 / PR #296.

## Global Constraints

- No backward-compatibility layer for `@QueryOperation`.
- Controller owns authorization contract selection.
- Feature owns query compilation and must not receive `AuthorizationContext`.
- Web DX must be annotation + injected resolved authorization predicate; controllers must not call authorization gateways manually.
- Workers must be able to authorize explicitly as user X without HTTP request state.
- Query and authorization contracts are independent metadata surfaces even when they reuse lower-level field/JPA binding primitives.
- OpenAPI query metadata comes from `@QueryContract`; authorization-only parameters must not leak into OpenAPI.
- Query and authorization predicates remain SQL-pushed before pagination.
- Pipeline repair happens last.

## Review Focus

- A handler with an authorization predicate but no `@AuthorizationContract` must fail closed.
- Query and authorization contract IDs must resolve independently; equal OpenAPI operation IDs must not couple them.
- Ticket runtime custom-field context must still be used for authorization and query compilation.
- Worker authorization must evaluate current user policy at execution time, not reuse a serialized predicate.
- Springdoc must not expose the injected authorization predicate as an HTTP parameter.

---

### Task 1: Contract metadata and registries

**Files:** query module; authorization module; focused unit tests.

**Interfaces:**
- Produces `@QueryContract(String value)` and `@AuthorizationContract(String value)`.
- Produces separate query/authz schema registries keyed by contract ID.

- [ ] Add failing tests proving query and authz contract lookup are independent.
- [ ] Add the two annotations and separate registry abstractions.
- [ ] Run focused module tests.
- [ ] Commit.

### Task 2: Web DX and OpenAPI

**Files:** Web argument resolver, Ticket/User controllers, Spring MVC route target resolver, OpenAPI customizer, tests.

**Interfaces:**
- Consumes contract metadata from Task 1.
- Produces annotation-driven `ObjectAuthorizationPredicate<T>` injection and OpenAPI query metadata from `@QueryContract`.

- [ ] Update tests first to require `@AuthorizationContract` for authz injection and `@QueryContract` for OpenAPI metadata.
- [ ] Replace manual `ObjectAuthorizationResolver.resolve()` calls in Ticket/User controllers with injected predicates.
- [ ] Make Object Authorization route discovery use `@AuthorizationContract`, never query metadata.
- [ ] Make OpenAPI query metadata use `@QueryContract`, never authorization metadata.
- [ ] Verify the OpenAPI snapshot.
- [ ] Commit.

### Task 3: Worker authorization API

**Files:** authorization application API/service/configuration and tests.

**Interfaces:**
- Produces an explicit worker-safe API accepting `AuthorizationPrincipal`, `AuthorizationRequest`, authorization contract ID, and runtime field context.
- Returns the same opaque `ObjectAuthorizationPredicate<T>` consumed by feature services.

- [ ] Add failing tests for worker-as-user authorization and request-denied fail-closed behavior.
- [ ] Implement the facade by composing Request Authorization with Object Authorization using current policy state.
- [ ] Verify authorization module tests and performance tests.
- [ ] Commit.

### Task 4: Ticket/User contract migration and cleanup

**Files:** Ticket/User schema declarations, context sources, obsolete query-operation plumbing, architecture tests.

**Interfaces:**
- Consumes Tasks 1-3.
- Produces independent query/authz contract IDs for Ticket/User endpoints and removes `@QueryOperation` from the redesigned path.

- [ ] Add/adjust tests proving Ticket dynamic fields and User static fields resolve through the correct contract.
- [ ] Migrate Ticket/User controller annotations and schema registrations.
- [ ] Remove obsolete `QueryRequestContextResolver`/manual Object Authorization resolver path where no longer used.
- [ ] Run full server tests, OpenAPI snapshot verification, formatting, Qodana, authorization performance, and Kubernetes integration.
- [ ] Repair CI-only failures last.
- [ ] Commit.
