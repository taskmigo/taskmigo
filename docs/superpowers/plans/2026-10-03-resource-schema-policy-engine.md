# Resource Schema Policy Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all duplicated Policy Engine schema APIs with one entity-agnostic semantic resource schema and separate execution bindings, including runtime-defined typed fields and fingerprint-safe compiled artifacts.

**Architecture:** The language module owns semantic resource identity, field identity, schema resolution, compiler root bindings, and resolved typed IR. Query and Object Authorization consume that IR through `FieldId`-based execution bindings; Statement artifacts compile per effective resource fingerprint so schema changes cannot reuse incompatible policy state.

**Tech Stack:** Java 26, Gradle Kotlin DSL, Spring Boot 4, Spring Modulith, JUnit 5, AssertJ, Mockito, Spring Data JPA, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-10-03-resource-schema-policy-engine-design.md`

## Global Constraints

- Make a complete breaking migration; do not add compatibility adapters for `EnvironmentSchema`, `QuerySchema`, or `ObjectAuthorizationSchema`.
- Keep `ResourceSchema` persistence-neutral and keep Java `Class<?>` out of semantic identity.
- Treat static and runtime-created schemas identically after resolution.
- Resolve source paths during compilation and never fall back from `FieldId` to raw path lookup during translation.
- Include effective schema fingerprints in compiled metadata and derived-artifact cache identity.
- Do not modify the separate `taskmigo/specification` repository.
- Do not run performance benchmarks locally; compilation of benchmark sources is allowed.
- Run all verification commands sequentially.
- New and changed public Java APIs use Markdown Javadoc comments per the repository `java-javadoc` skill.
- Spring tests follow the repository `spring-boot-testing` conventions, including `@DisplayName`, behavior/why comments, Arrange-Act-Assert, and `shouldExpectedBehaviorWhenCondition` names.

## Review Focus

- Two schemas reuse the same display path with different `FieldId` values: compiled and translated behavior must remain resource-specific (Task 1 and Task 2 tests).
- A runtime provider changes type/nullability without changing resource type: the fingerprint must change and stale compiled/bound state must be rejected (Task 1 and Task 4 tests).
- A query binding omits a valid semantic field or operator: translation must fail explicitly without path fallback (Task 2 tests).
- One Object Statement targets multiple resources whose common path has different identities: each resource must select its own compiled variant (Task 4 tests).
- A runtime value is missing or incompatible with an already-resolved path: evaluation/authorization must fail through the established closed error boundary (Task 1 and Task 3 tests).

---

### Task 1: Semantic resource schema and resolved Language IR

**Files:**

- Create: `server/modules/language/src/main/java/io/taskmigo/language/ResourceType.java`
- Create: `server/modules/language/src/main/java/io/taskmigo/language/FieldId.java`
- Create: `server/modules/language/src/main/java/io/taskmigo/language/FieldPath.java`
- Create: `server/modules/language/src/main/java/io/taskmigo/language/Field.java`
- Create: `server/modules/language/src/main/java/io/taskmigo/language/SchemaFingerprint.java`
- Create: `server/modules/language/src/main/java/io/taskmigo/language/ResourceSchema.java`
- Create: `server/modules/language/src/main/java/io/taskmigo/language/ResourceSchemaResolver.java`
- Create: `server/modules/language/src/main/java/io/taskmigo/language/SchemaContext.java`
- Create: `server/modules/language/src/main/java/io/taskmigo/language/CompilerEnvironment.java`
- Modify: `server/modules/language/src/main/java/io/taskmigo/language/{LanguageCompiler,EmbeddedLanguageCompiler,LanguageCompilerVisitor,SemanticAst,SemanticExpressionMapper,CompiledSource,EmbeddedLanguageEvaluator,EmbeddedLanguagePartialEvaluator}.java`
- Modify: `server/modules/language/src/main/java/io/taskmigo/language/ast/ExpressionVisitor.java`
- Delete: `server/modules/language/src/main/java/io/taskmigo/language/EnvironmentSchema.java`
- Test: `server/modules/language/src/test/java/io/taskmigo/language/{ResourceSchemaTest,EmbeddedLanguageCompilerTest,LanguageConformanceTest,AlphaSixLanguageTest}.java`

**Interfaces:**

- Produces: `ResourceSchema.type()`, `ResourceSchema.resolve(FieldPath)`, `ResourceSchema.fingerprint()`; `ResourceSchemaResolver.resolve(ResourceType, SchemaContext)`; `CompilerEnvironment` root bindings; expression visitor references containing `ResourceType`, `FieldId`, canonical `FieldPath`, type, and nullability.
- Consumes: existing `LanguageType`, compiler profiles, source spans, evaluator root maps, and slot/dependency machinery.

- [ ] **Step 1: Add failing semantic-contract tests**

  Test deterministic fingerprints independent of declaration order, distinct identities for equal display paths in different resources, runtime-built typed fields, duplicate/invalid declarations, and fingerprint changes for type/nullability/identity changes.

- [ ] **Step 2: Run focused language tests and confirm the new contracts are absent**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:language:test --tests io.taskmigo.language.ResourceSchemaTest`

  Expected: compilation failure for missing resource schema types.

- [ ] **Step 3: Implement the semantic value types, immutable code-backed `ResourceSchema`, resolver boundary, and compiler root bindings**

  Use validated nonblank canonical identities, immutable values, collision-resistant deterministic fingerprints, and exact-path resolution. `SchemaContext` remains generic and carries only context required by providers.

- [ ] **Step 4: Add failing compiler/visitor tests for resolved `FieldId` and schema fingerprint metadata**

  Assert unknown paths fail at compilation, equal paths with different IDs map differently, compiled references expose stable identities, schema fingerprints are retained per root binding, and local-variable references remain separate from resource fields.

- [ ] **Step 5: Replace `EnvironmentSchema` throughout Language compilation and evaluation**

  Change `LanguageCompiler.compile` to accept `CompilerEnvironment`; make resource references store resolved identities and canonical paths; preserve evaluator slot performance without schema re-resolution; update public Markdown Javadoc.

- [ ] **Step 6: Run all Language tests**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:language:test`

  Expected: PASS.

- [ ] **Step 7: Commit the semantic model and IR migration**

  Commit message: `Add semantic resource schemas to Language`

### Task 2: FieldId-based query execution bindings

**Files:**

- Create: `server/modules/query/src/main/java/io/taskmigo/query/QueryBinding.java`
- Create or modify: `server/modules/query/src/main/java/io/taskmigo/query/QueryFieldBinding.java`
- Modify: `server/modules/query/src/main/java/io/taskmigo/query/{FilterByCompiler,QueryPredicateFactory,DefaultQueryPredicates}.java`
- Modify: `server/modules/query/src/main/java/io/taskmigo/query/model/{QueryExpression,QueryPredicateModel}.java`
- Delete: `server/modules/query/src/main/java/io/taskmigo/query/{QuerySchema,QuerySchemaValidator}.java`
- Test: `server/modules/query/src/test/java/io/taskmigo/query/{FilterByCompilerTest,QueryBindingTest}.java`
- Delete: `server/modules/query/src/test/java/io/taskmigo/query/{QuerySchemaIdentityTest,QuerySchemaValidatorTest}.java`

**Interfaces:**

- Consumes: Task 1 `ResourceSchema`, `SchemaFingerprint`, `ResourceType`, `FieldId`, `CompilerEnvironment`, and resolved visitor references.
- Produces: `QueryBinding<Q>` compatibility metadata and `FieldId`-to-execution-field binding; opaque query expressions/predicates containing semantic field identities.

- [ ] **Step 1: Add failing query-binding tests**

  Cover successful runtime-defined field filtering, schema fingerprint mismatch, missing field binding, unsupported operator, same path/different ID isolation, nested/list fields, nullability, and blank filter behavior.

- [ ] **Step 2: Run focused query tests and verify failure against the old schema API**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:query:test --tests io.taskmigo.query.QueryBindingTest`

  Expected: compilation failure for missing binding API.

- [ ] **Step 3: Implement query execution binding and migrate the query expression model**

  `FilterByCompiler.compile(ResourceSchema, QueryBinding<Q>, String)` builds a one-root compiler environment, accepts resolved `FieldId` values from the Language visitor, and validates only execution capabilities in the binding.

- [ ] **Step 4: Remove `QuerySchema` and its duplicated semantic validation**

  Keep application generic type information only in the binding/API integration; use schema fingerprint and resource type for compatibility.

- [ ] **Step 5: Run all query tests**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:query:test`

  Expected: PASS.

- [ ] **Step 6: Commit the query binding migration**

  Commit message: `Replace query schemas with execution bindings`

### Task 3: Shared semantic Object Authorization model

**Files:**

- Create: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/ObjectAuthorizationBinding.java`
- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/application/port/in/api/ObjectAuthorization.java`
- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/application/port/out/ObjectAuthorizationTargetResolver.java`
- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/application/service/{ObjectAuthorizationService,ObjectAuthorizationExpressionValidator}.java`
- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/model/{ObjectAuthorizationExpression,ObjectAuthorizationPredicateModel,ObjectAuthorizationPredicateModels}.java`
- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/embeddedlanguage/{AuthorizationEmbeddedLanguageSchemas,AuthorizationEmbeddedLanguageConfiguration}.java`
- Delete: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/{ObjectAuthorizationSchema,ObjectAuthorizationField,ObjectAuthorizationPath}.java`
- Test: `server/modules/authorization/src/test/java/io/taskmigo/authorization/object/{ObjectAuthorizationBindingTest,application/service/ObjectAuthorizationExpressionValidatorTest,application/service/ObjectAuthorizationServiceTest}.java`
- Delete: `server/modules/authorization/src/test/java/io/taskmigo/authorization/object/ObjectAuthorizationSchemaIdentityTest.java`

**Interfaces:**

- Consumes: Task 1 semantic schemas/resolved IR and Task 2 binding conventions.
- Produces: route-resolvable `ObjectAuthorizationBinding<Q>` with `ResourceSchema`; predicates and residual expressions keyed by `FieldId` plus `SchemaFingerprint`.

- [ ] **Step 1: Add failing Object Authorization tests**

  Cover resolved identity preservation through partial evaluation, runtime-defined typed object fields, missing/incompatible binding rejection, path-with-different-ID isolation, runtime access failure, and unchanged permit/deny composition semantics.

- [ ] **Step 2: Run focused authorization tests and verify failure**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:access-control:test --tests 'io.taskmigo.authorization.object.*'`

  Expected: compilation failure for missing binding types/signatures.

- [ ] **Step 3: Migrate authorization environments, residual expressions, predicate models, and target resolution**

  Compile `principal`, `request`, and `object` as resource bindings; require exact schema fingerprint compatibility before producing or composing predicates; preserve opaque `ObjectAuthorizationPredicate<Q>` as application integration only.

- [ ] **Step 4: Remove the independent Object Authorization schema API and duplicate type/operator validation**

  Operator support belongs to execution bindings; semantic type/nullability comes only from `ResourceSchema`.

- [ ] **Step 5: Run Object Authorization module tests**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:access-control:test`

  Expected: PASS.

- [ ] **Step 6: Commit the Object Authorization migration**

  Commit message: `Unify object authorization resource semantics`

### Task 4: Per-resource Statement artifacts and fingerprint-safe caching

**Files:**

- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/application/service/StatementArtifactFactory.java`
- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/statement/StatementExecutionArtifact.java`
- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/application/model/AuthorizationOperation.java`
- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/composition/RequestAuthorizationConfiguration.java`
- Test: `server/modules/authorization/src/test/java/io/taskmigo/authorization/request/application/service/StatementArtifactFactoryTest.java`
- Test: `server/modules/authorization/src/test/java/io/taskmigo/authorization/object/application/service/ObjectAuthorizationServiceTest.java`

**Interfaces:**

- Consumes: Task 3 target bindings, schemas, and Object Authorization service.
- Produces: request-policy artifact or immutable object-policy variants keyed by `ResourceType` and `SchemaFingerprint`; cache identity including the sorted effective schema set.

- [ ] **Step 1: Add failing multi-resource and cache tests**

  Assert one target compiles per applicable schema, same path/different IDs remain distinct, incompatible policy fails validation for the affected resource, active authorization selects the exact variant, and a type/nullability/fingerprint change invalidates retained artifacts.

- [ ] **Step 2: Run focused artifact tests and confirm old union behavior fails assertions**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:access-control:test --tests io.taskmigo.authorization.request.application.service.StatementArtifactFactoryTest`

  Expected: FAIL because artifacts currently hold one union-schema `CompiledSource`.

- [ ] **Step 3: Implement per-resource compiled variants and exact selection**

  Preserve request-scope single-artifact behavior. Reject duplicate resource type with conflicting fingerprints and missing/stale variants rather than choosing by Java class or path.

- [ ] **Step 4: Run authorization tests**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:access-control:test`

  Expected: PASS.

- [ ] **Step 5: Commit artifact/cache migration**

  Commit message: `Compile object policies per resource schema`

### Task 5: Migrate static resources and persistence adapters

**Files:**

- Modify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/{role,statement}/adapter/out/persistence/*ResourceSchemas.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/{group,user}/adapter/out/persistence/*ResourceSchemas.java`
- Modify: `server/modules/{authorization,identity}/src/main/java/io/taskmigo/**/adapter/out/persistence/query/{JpaQueryPredicateBinder,JpaObjectAuthorizationPredicateBinder,QueryPredicateBinder,ObjectAuthorizationPredicateBinder}.java`
- Modify: `server/modules/query/src/main/java/io/taskmigo/query/model/QueryExpression.java`
- Test: corresponding authorization and identity persistence/query binder tests.

**Interfaces:**

- Consumes: Tasks 1–4 resource schemas, query bindings, object bindings, predicates, and expressions.
- Produces: one static `ResourceSchema` per Role, Statement, Group, and User surface; JPA execution mappings keyed only by stable `FieldId` values.

- [ ] **Step 1: Add or update failing binder integration tests**

  Assert every exposed static field has one stable ID, filter and Object Authorization reuse the same schema, JPA binding uses IDs rather than logical path strings, unsupported/missing IDs fail, and existing nested target/list queries still execute.

- [ ] **Step 2: Run affected authorization persistence tests and confirm migration failures**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:access-control:test`

  Expected: failures until resource declarations and binders use the new interfaces.

- [ ] **Step 3: Migrate Role and Statement declarations/binders**

  Define field IDs once beside each owning resource schema; bind those IDs to JPA paths/operators without repeating semantic type/nullability.

- [ ] **Step 4: Run affected identity persistence tests and confirm migration failures**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:identity:test`

  Expected: failures until Group/User declarations and binders use the new interfaces.

- [ ] **Step 5: Migrate Group and User declarations/binders**

  Preserve existing query and authorization behavior while removing all path-based schema identity checks.

- [ ] **Step 6: Run authorization and identity module tests sequentially**

  Run, in order:

  1. `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:access-control:test`
  2. `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :modules:identity:test`

  Expected: PASS for both.

- [ ] **Step 7: Commit static resource and persistence migration**

  Commit message: `Migrate static resources to semantic schemas`

### Task 6: Spring HTTP and migration application integration

**Files:**

- Modify: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/support/query/FilteredQueryArgumentResolver.java`
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/support/objectauthorization/ObjectAuthorizationPredicateArgumentResolver.java`
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/adapter/out/objectauthorization/SpringMvcObjectAuthorizationTargetResolver.java`
- Modify: `server/apps/migration/src/main/java/io/taskmigo/migration/infrastructure/config/AuthorizationObjectSchemaConfiguration.java`
- Test: `server/apps/web/src/test/java/io/taskmigo/web/adapter/in/http/support/objectauthorization/ObjectAuthorizationPredicateArgumentResolverTest.java`
- Test: `server/apps/web/src/test/java/io/taskmigo/web/adapter/out/objectauthorization/SpringMvcObjectAuthorizationTargetResolverTest.java`
- Test: relevant filtered-query resolver and migration context tests.

**Interfaces:**

- Consumes: static application bindings from Task 5 and target-resolution contracts from Task 3.
- Produces: Spring generic application binding to `ObjectAuthorizationPredicate<T>` and filtered-query parameters without treating `Class<T>` as semantic identity.

- [ ] **Step 1: Update tests first for resource/binding selection**

  Assert controllers select application bindings by their declared integration type while semantic compatibility is enforced by resource type/fingerprint, and duplicate/missing bindings fail startup or request resolution clearly.

- [ ] **Step 2: Run focused web tests and confirm compilation/assertion failure**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :apps:web:test --tests '*ObjectAuthorizationPredicateArgumentResolverTest' --tests '*SpringMvcObjectAuthorizationTargetResolverTest'`

  Expected: FAIL until adapters use the new binding contracts.

- [ ] **Step 3: Migrate HTTP target resolution, argument resolvers, and migration wiring**

  Keep Java generic types only for adapter selection. Pass the associated resource schema/binding to compiler and authorization services.

- [ ] **Step 4: Run web and migration tests sequentially**

  Run, in order:

  1. `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :apps:web:test`
  2. `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :apps:migration:test`

  Expected: PASS for both.

- [ ] **Step 5: Commit application integration migration**

  Commit message: `Wire resource bindings into applications`

### Task 7: Benchmarks, architecture, and removed-contract audit

**Files:**

- Modify: `server/benchmarks/authorization/src/jmh/java/io/taskmigo/benchmarks/authorization/{EmbeddedLanguageCompilerBenchmark,EmbeddedLanguageRuntimeBenchmark,EmbeddedLanguageSchemaBenchmark}.java`
- Modify: architecture tests under `server/testing/architecture/src/test/java/` only if public package boundaries change.
- Test: relevant module architecture tests and compile tasks.

**Interfaces:**

- Consumes: final public contracts from Tasks 1–6.
- Produces: compile-valid benchmark fixtures and architecture enforcement with no references to removed schema APIs.

- [ ] **Step 1: Add/update architecture assertions for semantic and execution ownership**

  Enforce that Language has no query/JPA dependencies, resource schemas contain no persistence types, and applications/modules respect existing dependency direction.

- [ ] **Step 2: Migrate benchmark fixtures without running benchmarks**

  Represent existing benchmark roots using code-backed `ResourceSchema` and `CompilerEnvironment`; retain equivalent field types and runtime data.

- [ ] **Step 3: Audit all source references to removed contracts and raw-path translation**

  Run: `rg -n 'EnvironmentSchema|QuerySchema|ObjectAuthorizationSchema|schemaIdentity|bind\([^)]*(path|String)' server --glob '*.java'`

  Expected: no obsolete API references or execution fallback; any legitimate textual references are reviewed explicitly.

- [ ] **Step 4: Compile benchmark and architecture sources without executing JMH**

  Run: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon :benchmarks:authorization:compileJmhJava :testing:architecture:test`

  Expected: PASS.

- [ ] **Step 5: Commit benchmarks and architecture guardrails**

  Commit message: `Enforce resource schema architecture`

### Task 8: Final review, local verification, PR, and CI

**Files:**

- Modify: `.github/pull_request_template.md` only if the target branch template itself requires a task-scoped correction; otherwise read it and update PR metadata only.
- Modify: implementation files from Tasks 1–7 only for blocker or required integration fixes found by review/verification.

**Interfaces:**

- Consumes: complete coherent migration.
- Produces: reviewer-ready history, current PR metadata, and green required checks.

- [ ] **Step 1: Perform the bounded post-implementation adversarial review and direct-consumer scan**

  Re-read the final diff, verify every issue acceptance criterion, inspect serializers/visitors/switches/binders/Spring generic resolution/cache identity/generated artifacts, classify findings, and fix only blockers or required integration gaps in their owning commits.

- [ ] **Step 2: Run repository formatting check**

  Run from repository root: `npm run format:check`

  Expected: PASS.

- [ ] **Step 3: Run the complete server build sequentially**

  Run from `server`: `GRADLE_USER_HOME=/tmp/taskmigo-gradle-home ./gradlew --no-daemon build`

  Expected: PASS, including Checkstyle and all tests. Do not run JMH benchmarks.

- [ ] **Step 4: Run module architecture verification and diff hygiene**

  Run from `server`: `python3 scripts/verify-module-architecture.py`

  Run from repository root: `git diff --check origin/next...HEAD`

  Expected: PASS for both.

- [ ] **Step 5: Read target-branch contribution guidance and PR template, then finalize focused commits**

  Preserve one coherent commit per independently reviewable problem and rewrite owning commits for any verification repairs rather than appending repair-only commits.

- [ ] **Step 6: Push `enhancement/237`, create or update the PR, and include reviewer context, causal architecture problem, solution/prevention, verification, checklist, and `Closes #237`**

  Use immutable final commit links in the Root Cause Analysis after implementation stabilizes. Re-read the PR body to verify the closing directive remains present.

- [ ] **Step 7: Monitor CI by current head SHA until every required check passes**

  Inspect all completed failures in one snapshot, use Qodana job-summary JSON and formatter-emitted diffs as primary diagnostics, batch task-related repairs into owning commits, and stop only when required checks are green or a genuine external blocker is documented.
