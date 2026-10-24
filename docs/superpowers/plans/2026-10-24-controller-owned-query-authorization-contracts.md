# Controller-Owned Query and Authorization Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace operation-string query/authz schema routing with typed controller contracts while keeping Web annotation DX and adding a first-class explicit worker authorization API.

**Architecture:** Web annotations select typed query/authz schema contracts. Spring MVC injects server-owned Object Authorization predicates from the authz contract; feature services compile raw list-query intent from feature-owned query contracts; non-HTTP callers authorize explicitly with principal + request + authz schema. Persistence keeps SQL push-down.

**Tech Stack:** Java 25, Spring Boot/Spring MVC, Spring Security, Spring Modulith, Springdoc OpenAPI, JPA, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-24-controller-owned-query-authorization-contracts-design.md`

## Global Constraints

- No backward compatibility for `@QueryOperation` or operation-string schema routing.
- Controller controls Authorization schema selection.
- Query Contract and Authorization Contract stay separate.
- OpenAPI `x-filter-by` and `x-sort-by` metadata must remain generated from Query Contract metadata.
- Worker authorization must not depend on Spring MVC or ambient HTTP state.
- Query and authorization predicates remain database-pushed before pagination.
- Pipeline-specific repair is last.

## Review Focus

- Internal authorization predicate parameters must not appear in generated OpenAPI.
- Ticket runtime EAV fields must resolve correctly for list/read/update authz contracts.
- A declared contract with zero or multiple beans must fail closed.
- Worker execution must re-authorize user X at execution time rather than accepting a serialized predicate.
- Query/authz schema identity mismatches must fail rather than silently bind against another schema.

---

### Task 1: Core Authorization Contract and Worker API

**Files:** authorization object API/model/service and tests.

**Interfaces:**
- Produces: `ObjectAuthorizationSchema<Q>` and an explicit `ObjectAuthorization.authorize(AuthorizationPrincipal, AuthorizationRequest, ObjectAuthorizationSchema<Q>, QueryFieldContext)` API.
- Consumes: existing `RequestAuthorization` and `AuthorizationContext`.

- [ ] Add failing tests for typed authz schema evaluation and explicit worker principal/request authorization.
- [ ] Run the focused authorization tests and confirm RED.
- [ ] Add the authz schema abstraction, migrate predicate models/validator/language schema/target resolver, and implement explicit worker authorization.
- [ ] Run focused authorization tests and module tests; confirm GREEN.
- [ ] Commit.

### Task 2: Web Authorization Contract DX

**Files:** Web object-authorization annotations/resolvers, MVC target resolver, Ticket runtime context source, controller tests.

**Interfaces:**
- Consumes: `ObjectAuthorizationSchema<Q>` from Task 1.
- Produces: `@AuthorizationContract` + predicate argument injection selected by contract class.

- [ ] Add/convert resolver tests so annotation-selected schemas are required and client-supplied auth data is ignored.
- [ ] Run focused Web tests and confirm RED.
- [ ] Replace `ObjectAuthorizationResolver` / `@QueryOperation` authz routing with annotation-driven predicate argument resolution and class-keyed runtime field context.
- [ ] Run focused Web tests and confirm GREEN.
- [ ] Commit.

### Task 3: Query Contract and OpenAPI

**Files:** Query annotation, OpenAPI customizer/tests, query context lookup where still needed.

**Interfaces:**
- Produces: `@QueryContract(Class<? extends QuerySchemaView>)`.
- OpenAPI consumes the declared contract class and schema bean directly; no operation-string lookup.

- [ ] Add tests pinning `x-filter-by` / `x-sort-by` to `@QueryContract` metadata.
- [ ] Run focused tests and confirm RED.
- [ ] Implement typed query-contract lookup and remove `@QueryOperation` dependency from OpenAPI/query routing.
- [ ] Verify generated OpenAPI snapshot behavior.
- [ ] Commit.

### Task 4: Feature Contracts and Controllers

**Files:** Ticket/User/Group/Role/Statement public contract markers, concrete query/authz schemas, repositories, Ticket/User services, all affected Web controllers.

**Interfaces:**
- Ticket/User feature list services consume typed Query Contract schema and raw `ListQueryRequest`.
- Controllers declare query/authz contract annotations and receive `ObjectAuthorizationPredicate<T>` parameters.

- [ ] Convert Ticket/User tests first and confirm RED against old schema wrappers.
- [ ] Add public typed contract markers and make concrete schemas implement the right contract only.
- [ ] Separate query and authz schema implementations where their capabilities/ownership differ.
- [ ] Migrate controllers from manual resolver calls to injected predicates and typed annotations.
- [ ] Remove obsolete resolver/wrapper/operation-string APIs and tests.
- [ ] Run server tests and architecture tests; confirm GREEN.
- [ ] Commit.

### Task 5: Verification and Pipeline Repair

**Files:** only files required by formatter/OpenAPI snapshot/Qodana/CI findings.

**Interfaces:** final branch only.

- [ ] Run full server build/tests.
- [ ] Verify OpenAPI snapshot and confirm authz internal parameters do not leak.
- [ ] Run formatting checks; apply only formatter-required changes.
- [ ] Run authorization performance and architecture checks.
- [ ] Push final branch and inspect Formatting, Server, Qodana, Authorization performance, and Kubernetes workflows.
- [ ] Repair CI-specific failures only now, with exact artifacts/logs.
