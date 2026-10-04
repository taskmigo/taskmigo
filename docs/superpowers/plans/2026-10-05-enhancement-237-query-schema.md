# Enhancement 237 Query Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Enhancement #237 so each JPA query implementation owns one operation-scoped, JPA-metadata-backed query schema shared by filtering, sorting, and Object Authorization, with independent trust boundaries and no Policy Engine policy cache.

**Architecture:** Keep the expression-language contracts persistence-neutral in `:modules:query`, and add the JPA-specific `QuerySchema<E>`, `QueryField<E,V>`, and typed `JpaPath<E,V>` implementation in `:modules:database`. Concrete schemas live beside their JPA repositories and publish only a persistence-neutral operation/schema view to authorization and HTTP infrastructure. HTTP selects an operation identifier, never a concrete JPA schema; repositories use the concrete schema to compile filter and authorization predicates independently and compose them with mandatory `AND`.

**Tech Stack:** Java 26, Spring Boot 4, Spring Data JPA Criteria/Specifications, Hibernate static metamodel, JUnit 5, AssertJ, Testcontainers, Spring MVC.

**Spec:** GitHub issue taskmigo/taskmigo#237 — the single source of truth for this implementation. Do not consult `taskmigo/specification`.

## Global Constraints

- Query schema is scoped to a query operation / query implementation, not to a JPA entity or response DTO.
- Static query fields derive exposed path, Java type, language type, nullability, and JPA binding from JPA metadata wherever deterministic.
- Every schema has exactly two field sources: `staticFields()` and `runtimeFields(QueryFieldContext)`; runtime discovery itself remains deferred and returns an empty collection in concrete schemas for this enhancement.
- Query schemas are explicit allowlists and default-deny; duplicate effective paths fail resolution.
- Embedded and to-one nested paths are supported; collection-valued relationship traversal is rejected.
- Field expression capabilities are operation-scoped and enforced before JPA execution.
- Request Authz, Object Authz, and `filterBy` reuse the same language infrastructure but keep separate environments.
- `filterBy` cannot see `principal.*`, `request.*`, authorization state, or policy internals.
- Object Authz and `filterBy` are parsed/validated/compiled independently.
- Final JPA predicate provenance is always mandatory authorization `AND` optional client filter.
- Single-object unauthorized and nonexistent targets both resolve as `404`; no unrestricted existence probe follows an authorized miss.
- Domain mutation validation runs only after authorized target resolution and remains responsible for `409`/`422`.
- Policy Engine policy caching, cache TTL/key/invalidation semantics, and replacement caches are removed.
- Inbound adapters must not import or bind a concrete JPA `QuerySchema`.
- Breaking changes are allowed.
- Verification commands run sequentially.
- New Spring tests follow the repository `spring-boot-testing` conventions; new public Java APIs use JEP 467 `///` Javadoc.

## Review Focus

- Two operation schemas backed by the same entity must remain distinct and the web layer must select by operation, never by entity/DTO type.
- A malicious client expression with top-level `OR`, `NOT`, parentheses, tautology, or malformed syntax must never weaken the mandatory authorization predicate.
- Schema resolution must reject duplicate static/runtime paths and any collection-valued JPA traversal before a Criteria query executes.
- String `contains` must be available only when the field declares `CONTAINS`; unsupported methods such as `startsWith` and unsupported ordering on that field must fail compilation.
- Object-authorization misses during mutations must become not-found before domain validation; business conflicts must remain visible only after authorized resolution.

---

### Task 1: JPA-backed operation query schema

**Files:**
- Create: `server/modules/query/src/main/java/io/taskmigo/query/QuerySchemaView.java`
- Create: `server/modules/query/src/main/java/io/taskmigo/query/QueryFieldDescriptor.java`
- Modify: `server/modules/query/src/main/java/io/taskmigo/query/QueryOperator.java`
- Create: `server/modules/database/src/main/java/io/taskmigo/database/query/QuerySchema.java`
- Create: `server/modules/database/src/main/java/io/taskmigo/database/query/QueryField.java`
- Create: `server/modules/database/src/main/java/io/taskmigo/database/query/JpaPath.java`
- Create: `server/modules/database/src/main/java/io/taskmigo/database/query/QueryFieldContext.java`
- Modify: `server/modules/database/build.gradle.kts`
- Test: `server/modules/database/src/test/java/io/taskmigo/database/query/QuerySchemaTest.java`

**Interfaces:**
- Produces: persistence-neutral `QuerySchemaView` consumed by query/filter and authorization layers.
- Produces: abstract JPA `QuerySchema<E>` with `staticFields()`, `runtimeFields(context)`, `field(...).operators(...)`, typed nested `JpaPath`, duplicate validation, and operation identity.

- [ ] **Step 1: Write failing schema tests** for metadata inference, default deny, duplicate paths, runtime/static collision, supported nested singular path, and rejected collection-valued traversal.
- [ ] **Step 2: Run** `./gradlew --no-daemon :modules:database:test --tests io.taskmigo.database.query.QuerySchemaTest`.
  Expected: FAIL because the JPA query-schema API does not exist.
- [ ] **Step 3: Implement the minimal JPA schema API** and persistence-neutral view required by the tests.
- [ ] **Step 4: Re-run the same test.**
  Expected: PASS.
- [ ] **Step 5: Commit the completed schema foundation.**

### Task 2: Unified expression semantics and client filter environment

**Files:**
- Modify: `server/modules/language/src/main/antlr/io/taskmigo/language/antlr/EmbeddedLanguage.g4`
- Modify: `server/modules/language/src/main/java/io/taskmigo/language/SemanticAst.java`
- Modify: `server/modules/language/src/main/java/io/taskmigo/language/LanguageCompilerVisitor.java`
- Modify: `server/modules/language/src/main/java/io/taskmigo/language/EmbeddedLanguageEvaluator.java`
- Modify: `server/modules/language/src/main/java/io/taskmigo/language/EmbeddedLanguagePartialEvaluator.java`
- Modify: `server/modules/language/src/main/java/io/taskmigo/language/CompilationFeature.java`
- Modify: query-language visitor/expression classes under `server/modules/query/src/main/java/io/taskmigo/query/`
- Modify: `server/modules/query/src/main/java/io/taskmigo/query/FilterByCompiler.java`
- Test: language conformance and `FilterByCompilerTest`

**Interfaces:**
- Consumes: `QuerySchemaView` from Task 1.
- Produces: expression-level `CONTAINS` and filter compilation where query fields are unprefixed client roots while trusted roots are absent.

- [ ] **Step 1: Add failing tests** proving `username == "alice"`, `username.contains("ali")`, and `username in [...]` work only when enabled; `username.startsWith(...)`, `username > ...`, `principal.*`, and `request.*` are rejected.
- [ ] **Step 2: Run focused language/query tests.**
  Expected: FAIL for missing contains syntax and operation-scoped schema view.
- [ ] **Step 3: Implement contains syntax/AST/evaluation/partial evaluation and reshape `FilterByCompiler` around `QuerySchemaView` without a policy cache.**
- [ ] **Step 4: Re-run focused tests.**
  Expected: PASS.
- [ ] **Step 5: Commit unified expression/filter behavior.**

### Task 3: Object Authorization consumes QuerySchema and policy cache is removed

**Files:**
- Delete: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/ObjectAuthorizationSchema.java`
- Delete: `ObjectAuthorizationField.java`, `ObjectAuthorizationPath.java`, `ObjectAuthorizationOperator.java`
- Modify: Object Authorization service, validator, predicate-model factory, embedded-language schemas, ports, and target resolver contracts.
- Delete: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/application/service/StatementArtifactCache.java`
- Modify: `StatementArtifactFactory.java`, `EffectiveStatement.java`, `server/modules/authorization/build.gradle.kts`
- Test: Object Authorization validator/service/environment tests and Statement artifact tests.

**Interfaces:**
- Consumes: `QuerySchemaView` and `QueryOperator`.
- Produces: Object Authz predicates whose schema identity comes from the same operation schema as client filtering and JPA binding; no cached policy derivatives.

- [ ] **Step 1: Add failing tests** that Object Authz resolves `object.*` against a `QuerySchemaView`, enforces the same per-field operators including `CONTAINS`, and recompiles authoritative Statement policy state without cache reuse.
- [ ] **Step 2: Run focused authorization tests.**
  Expected: FAIL against the duplicated ObjectAuthorizationSchema/cache implementation.
- [ ] **Step 3: Replace duplicate object schema metadata with `QuerySchemaView` and remove Caffeine policy artifact caching/wiring.**
- [ ] **Step 4: Re-run focused tests.**
  Expected: PASS.
- [ ] **Step 5: Commit authorization/schema unification and cache removal.**

### Task 4: Operation-scoped concrete schemas and JPA expression binding

**Files:**
- Add Hibernate static metamodel generation to affected server modules.
- Replace `UserResourceSchemas`, `GroupResourceSchemas`, `RoleResourceSchemas`, and `StatementResourceSchemas` with concrete operation schemas beside JPA query implementations.
- Modify JPA query/object-authorization binders in Identity and Access Control to resolve paths and Java types from the concrete `QuerySchema<E>` instead of string/type maps.
- Modify JPA expression binders for typed paths and `CONTAINS`.
- Test: binder tests and operation-schema tests for User/Group/Role/Statement.

**Interfaces:**
- Consumes: concrete `QuerySchema<E>` from Task 1 and expression models from Tasks 2–3.
- Produces: operation-specific schemas such as list-users/delete-user/update-user-statements, with multiple schemas allowed for `UserEntity`.

- [ ] **Step 1: Add failing tests** showing two User operations expose different fields/capabilities while sharing `UserEntity`, static metamodel paths bind without string maps, and an undeclared field cannot bind.
- [ ] **Step 2: Run focused persistence tests.**
  Expected: FAIL while resources still use global DTO schemas/string maps.
- [ ] **Step 3: Implement generated metamodel configuration, concrete operation schemas, and schema-backed JPA binders; remove duplicate maps/configuration.**
- [ ] **Step 4: Re-run focused tests.**
  Expected: PASS.
- [ ] **Step 5: Commit operation schemas and JPA binding.**

### Task 5: Inbound operation selection, authorization composition, and mutation semantics

**Files:**
- Create a persistence-neutral query-operation identifier/annotation in `:modules:query` as needed.
- Modify web `FilteredQueryArgumentResolver`, `ObjectAuthorizationPredicateArgumentResolver`, and `SpringMvcObjectAuthorizationTargetResolver` so they select an operation schema view rather than a concrete schema or DTO type.
- Modify User/Group/Role/Statement controllers and application ports only as needed to carry independently compiled filter/auth predicates.
- Modify JPA query repositories so authorization and filter are bound independently and composed as mandatory authorization `AND` optional filter.
- Modify User mutation flow/controller exception mapping so unauthorized/nonexistent targets are `404` and domain validation runs afterward.
- Test: resolver tests, repository/integration tests, User API security/mutation tests.

**Interfaces:**
- Consumes: operation schema views and schema-backed binders.
- Produces: transport-neutral operation selection with no inbound dependency on concrete JPA schema and the normative `403 / 404 / domain error` boundary.

- [ ] **Step 1: Add failing tests** for operation-based selection with multiple same-entity schemas; OR/NOT/parentheses/tautology filter bypass attempts; malformed filter; unauthorized/nonexistent single targets; and authorized domain conflicts.
- [ ] **Step 2: Run focused web/integration tests sequentially.**
  Expected: FAIL on global DTO schema selection and current 403 mutation handling.
- [ ] **Step 3: Implement operation selection and mandatory predicate provenance; remove any unrestricted second lookup and move mutation/domain checks after authorized resolution.**
- [ ] **Step 4: Re-run focused integration tests.**
  Expected: PASS.
- [ ] **Step 5: Commit end-to-end operation semantics.**

### Task 6: Full verification and cleanup

**Files:**
- Update architecture tests and public API documentation touched by the final design.
- Remove dead legacy schema/binder/cache code and unused dependencies.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: branch satisfying #237 acceptance criteria with no stale duplicate schema or Policy Cache concepts.

- [ ] **Step 1: Run** `npm run format:check`.
  Expected: PASS.
- [ ] **Step 2: Run** `cd server && ./gradlew --no-daemon build`.
  Expected: PASS.
- [ ] **Step 3: Inspect required GitHub Actions checks sequentially; for Qodana read the `Analyze` job summary `## Qodana results` before changing source.**
  Expected: all required checks PASS.
- [ ] **Step 4: Perform a whole-branch review against #237, fix Critical/Important findings with RED→GREEN tests, and record deferred minors/rulings.**
- [ ] **Step 5: Finish only when required verification is green.**
