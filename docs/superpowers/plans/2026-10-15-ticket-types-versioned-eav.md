# Ticket Types with Versioned Typed EAV Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement issue #236 with stable Ticket common fields, generic versioned typed EAV custom fields, historical schema pinning, database-side filtering/sorting, and Policy Engine object filtering.

**Architecture:** Add reusable `modules:eav` and a `modules:ticket` bounded context. Ticket persists only stable system-owned common fields plus an EAV schema-instance reference; Query/Policy expose one flat namespace and carry an immutable operation-scoped `QueryFieldContext` from Web validation through JPA binding so custom fields remain typed and database-side.

**Tech Stack:** Java 26, Gradle 9.7.1, Spring Boot, Spring Data JPA/Hibernate Criteria, PostgreSQL, Flyway, Spring Modulith, ArchUnit, JSpecify, Testcontainers, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-15-ticket-types-versioned-eav-design.md`

## Global Constraints

- Keep exactly `server/apps/web`, `server/apps/worker`, and `server/apps/migration` as executable app roots.
- Add `server/modules/eav` and `server/modules/ticket`; Onion responsibilities are packages, not Gradle subprojects.
- Feature modules do not own REST controllers; HTTP adapters stay in `server/apps/web`.
- EAV must not depend on Ticket and must not contain Ticket-specific table names, APIs, or query branches.
- Ticket common fields are `id`, `ticketTypeId`, `schemaInstanceId`, `title`, `status`, `createdBy`, `createdAt`, `updatedAt`, and internal optimistic-lock `version`.
- `status` is a system-owned non-blank string in #236; do not invent a Ticket status lifecycle or enum that is absent from the current product contract.
- Custom fields cannot collide with the Ticket common namespace.
- Supported custom types are exactly `STRING`, `NUMBER`, `BOOLEAN`, `DATE`, `DATETIME`, `UUID`, and `ENUM`.
- Optional missing custom values have no EAV row; explicit-null value rows are prohibited.
- Published versions are immutable; Ticket update/read always use the historical version pinned by its schema instance.
- Query/Policy use flat paths such as `status`, `severity`, and `object.severity`; never introduce `fields.*`.
- Filtering, supported sorting, and supported Object Authorization execute in persistence before pagination; no in-memory fallback.
- Ambiguous dynamic fields fail closed.
- No native/raw SQL, `JdbcTemplate`, `JdbcClient`, or native Spring Data `@Query`; use repositories, Specifications, and Criteria API.
- While application major version is 1, modify only `server/modules/database/src/main/resources/db/migration/V1__schema.sql`; do not add feature-specific Flyway versions.
- Every Java package containing Java source has `package-info.java` annotated with `@NullMarked`; new public Java contracts use concise `///` Markdown Javadoc where the contract needs explanation.
- Integration tests use PostgreSQL/Testcontainers, not H2.
- TDD for every behavior slice; each task ends in a commit and review gate.

## Review Focus

- PATCH custom value semantics: omitted key means unchanged, JSON `null` removes an optional row, and removing a required field fails; Task 4 owns these tests.
- Runtime scope ambiguity: a key ambiguous globally but compatible inside one Ticket Type succeeds when `ticketTypeId` scopes the list and fails closed unscoped; Tasks 7–8 own these tests.
- Query/authorization schema identity: validation and JPA binding must use the same immutable `QueryFieldContext` snapshot; Task 9 owns mismatch/same-context tests.
- Request-independent policy validation: route-time Object Authorization validation has no request context, so Ticket QuerySchemas must validate against the global-compatible runtime catalog and reject globally ambiguous keys; Tasks 8–9 own these tests.
- Concurrency/history: publish-vs-create must never validate one version and persist another, and retirement must never orphan historical Tickets; Task 10 owns the race/history corpus.

---

## File Map

### `server/modules/eav`

- `build.gradle.kts` — EAV dependencies/tests.
- `schema/domain/EavDataType.java` — finite custom type set.
- `schema/domain/EavSchemaStatus.java` — DRAFT/PUBLISHED/RETIRED.
- `schema/domain/EavFieldDefinition.java` — logical field identity/key/type/required/constraints.
- `schema/domain/EavSchemaVersion.java` — lifecycle + immutability.
- `schema/domain/EavValue.java` — typed value variants.
- `schema/domain/EavValueValidator.java` — raw value normalization/type/constraint validation.
- `schema/application/EavFieldSpec.java` — schema field application DTO.
- `schema/application/EavSchemaVersionInfo.java` — immutable schema projection.
- `schema/application/EavSchemaRepository.java` — persistence contract.
- `schema/application/EavSchemaService.java` / `EavSchemaServiceImpl.java` — generic schema lifecycle.
- `value/application/EavInstanceRepository.java` — schema-instance/value persistence contract.
- `value/application/EavInstanceService.java` / `EavInstanceServiceImpl.java` — create/get/patch pinned instances.
- `value/application/EavInstanceView.java` — instance/version/value projection.
- `query/EavQueryField.java` — persistence-neutral dynamic-field metadata.
- `query/EavQueryFieldCatalog.java` — compatible runtime field resolution.
- `query/EavJpaQueryFieldFactory.java` — generic Criteria binding over typed EAV value rows.
- `schema/persistence/*`, `value/persistence/*` — JPA implementation.
- matching `package-info.java` and domain/PostgreSQL tests.

### `server/modules/ticket`

- `build.gradle.kts` — bounded-context dependencies/tests.
- `type/domain/TicketType.java`, `TicketTypeStatus.java` — stable type identity/eligibility.
- `type/application/api/TicketTypeService.java`, `TicketTypeInfo.java` — Web-facing management API.
- `type/application/TicketTypeRepository.java`, `TicketTypeServiceImpl.java` — Ticket Type/template orchestration.
- `domain/Ticket.java` — common Ticket aggregate.
- `application/api/TicketService.java`, `TicketInfo.java`, `TicketPatch.java` — Ticket application API/projections.
- `application/TicketRepository.java`, `TicketQueryRepository.java`, `TicketServiceImpl.java` — application orchestration contracts.
- `application/TicketQueryFieldCatalog.java` — creates immutable runtime field-context snapshots.
- `persistence/TicketEntity.java`, `TicketTypeEntity.java`, repositories/adapters.
- `persistence/ListTicketsQuerySchema.java`, `ReadTicketQuerySchema.java`, `UpdateTicketQuerySchema.java` — static + runtime operation schemas.
- matching `package-info.java`, architecture/domain/PostgreSQL tests.

### Shared Query/JPA/Authorization/Web

- `modules/query/.../QueryFieldDescriptor.java` — sortability metadata.
- `modules/query/.../QuerySort.java` — generic typed sort request.
- `modules/query/.../FilteredQuery.java` — predicate + immutable runtime context + sorting.
- `modules/jpa-query/.../JpaQueryValueExpression.java` — generic dynamic Criteria value expression.
- `modules/jpa-query/.../QueryField.java`, `QuerySchema.java`, `JpaQueryExpressionBinder.java` — context-aware dynamic field binding.
- `modules/jpa-query/.../JpaQuerySortBinder.java` — missing-last typed ordering + stable tie breaker; no ORDER BY on count queries.
- `modules/authorization/.../ObjectAuthorization*` and JPA binders — carry/verify `QueryFieldContext` identity.
- `apps/web/.../query/QueryFieldContextSource.java`, `QueryFieldContextResolver.java` — per-operation request context SPI/cache.
- `apps/web/.../FilteredQueryArgumentResolver.java`, `ObjectAuthorizationPredicateArgumentResolver.java` — use the same cached context.
- `apps/web/api/v0/ticket/*` — Ticket/Ticket Type controllers + Ticket-specific context source.

---

### Task 1: Generic EAV domain

**Files:**

- Modify: `server/settings.gradle.kts`
- Create: `server/modules/eav/build.gradle.kts`
- Create: EAV domain files listed in File Map plus package-info files.
- Create: `server/modules/eav/src/test/java/io/taskmigo/eav/schema/domain/EavSchemaVersionTest.java`
- Create: `server/modules/eav/src/test/java/io/taskmigo/eav/schema/domain/EavValueValidatorTest.java`
- Create: `server/modules/eav/src/test/java/io/taskmigo/eav/EavOnionArchitectureTest.java`

**Interfaces:**

- `enum EavDataType { STRING, NUMBER, BOOLEAN, DATE, DATETIME, UUID, ENUM }`.
- `record EavFieldDefinition(UUID fieldId, String key, String label, EavDataType dataType, boolean required, Map<String,Object> constraints)`; non-blank key/label and only type-supported constraint names.
- `EavSchemaVersion.draft(UUID id, UUID definitionId, long version, List<EavFieldDefinition> fields)`, `publish(Instant)`, `retire(Instant)`.
- `Optional<EavValue> EavValueValidator.validate(EavFieldDefinition field, @Nullable Object raw)`; empty means missing optional value, required missing throws.

- [ ] **Step 1:** Write failing tests for exact type set, DRAFT editability, PUBLISHED immutability, lifecycle transitions, STRING/NUMBER/DATE/DATETIME constraints, ENUM stable keys, type mismatch, and required/missing behavior.
- [ ] **Step 2:** Run `cd server && ./gradlew :modules:eav:test --tests 'io.taskmigo.eav.schema.domain.*'`; expected FAIL because types do not exist.
- [ ] **Step 3:** Implement minimal pure domain types and `@NullMarked` package docs; no Spring/JPA imports in domain.
- [ ] **Step 4:** Add Onion architecture assertions with `testing:architecture`.
- [ ] **Step 5:** Run `./gradlew :modules:eav:test`; expected PASS.
- [ ] **Step 6:** Commit `Add generic typed EAV domain`.

### Task 2: EAV schema lifecycle and persistence

**Files:**

- Create: EAV schema application/persistence files from File Map.
- Modify: `server/modules/database/src/main/resources/db/migration/V1__schema.sql`
- Create: `EavSchemaServiceTest.java`
- Create: `EavSchemaPersistenceIntegrationTest.java`

**Interfaces:**

- `EavSchemaService`:
  - `UUID createDefinition(String key, String name)`
  - `EavSchemaVersionInfo createDraft(UUID definitionId)`; clone current definitions when current exists, allocate new physical definition IDs, preserve logical `fieldId`.
  - `EavSchemaVersionInfo replaceDraftFields(UUID definitionId, UUID versionId, List<EavFieldSpec> fields)`
  - `EavSchemaVersionInfo publish(UUID definitionId, UUID versionId)`
  - `EavSchemaVersionInfo retire(UUID definitionId, UUID versionId)`
  - `Optional<EavSchemaVersionInfo> current(UUID definitionId)`
  - `EavSchemaVersionInfo version(UUID definitionId, UUID versionId)`
- For a draft cloned from a published version, reusing an existing `fieldId` with a different key or data type is rejected; semantic type/key change requires a new `fieldId`. Label/required/constraints may change in the new version.
- Publish pessimistically locks the schema-definition row and atomically switches `current_version_id`.
- Retiring the current version atomically clears `current_version_id`; retiring history leaves current untouched.

- [ ] **Step 1:** Write failing tests for draft cloning, logical-ID preservation, same-ID key/type mutation rejection, new-ID semantic replacement, immutable published versions, current switching, retirement, and publish serialization.
- [ ] **Step 2:** Run `./gradlew :modules:eav:test --tests '*EavSchemaServiceTest'`; expected FAIL.
- [ ] **Step 3:** Add schema-definition/version/field-definition tables and composite FKs to `V1__schema.sql`, including `(schema_definition_id, version)` uniqueness and field-definition uniqueness needed by later value FKs.
- [ ] **Step 4:** Implement JPA persistence + transactional service using Spring Data locking; avoid `@Query` unless a derived/lock method cannot express the operation.
- [ ] **Step 5:** Add PostgreSQL assertions for FK/uniqueness/status failures and historical resolvability after retirement.
- [ ] **Step 6:** Run `./gradlew :modules:eav:test`; expected PASS.
- [ ] **Step 7:** Commit `Persist versioned EAV schemas`.

### Task 3: EAV schema instances and typed values

**Files:**

- Create: EAV value application/persistence files from File Map.
- Modify: `server/modules/database/src/main/resources/db/migration/V1__schema.sql`
- Create: `EavInstanceServiceTest.java`
- Create: `EavValuePersistenceIntegrationTest.java`

**Interfaces:**

- `EavInstanceService`:
  - `EavInstanceView create(UUID definitionId, UUID versionId, Map<String, ?> values)`
  - `EavInstanceView get(UUID instanceId)`
  - `EavInstanceView patch(UUID instanceId, Map<String, ?> changes)`
- `EavInstanceView` exposes `id`, `schemaDefinitionId`, `schemaVersionId`, immutable `Map<String,Object> values`.
- Create only accepts a PUBLISHED version. Patch/read use the instance's pinned version even when it later becomes RETIRED.
- PATCH: omitted key unchanged; map entry with `null` deletes an optional row; required deletion fails. Create-time null normalizes to missing.

- [ ] **Step 1:** Write failing service tests for typed values, unknown keys, required fields, constraints, create-time null normalization, optional deletion, required deletion rejection, and patch-after-retirement.
- [ ] **Step 2:** Run `./gradlew :modules:eav:test --tests '*EavInstanceServiceTest'`; expected FAIL.
- [ ] **Step 3:** Add `eav_schema_instances` and `eav_field_values` to `V1__schema.sql`; enforce FK `(schema_instance_id, schema_version_id)` and `(schema_version_id, field_id, data_type)` plus CHECK that exactly the matching typed value column is populated.
- [ ] **Step 4:** Implement persistence/service orchestration against the pinned version; never consult `current_version_id` on patch/read.
- [ ] **Step 5:** Add PostgreSQL tests rejecting cross-version fields, wrong `data_type`, explicit all-null rows, and accepting historical reads after retirement.
- [ ] **Step 6:** Run `./gradlew :modules:eav:test`; expected PASS.
- [ ] **Step 7:** Commit `Store typed EAV instance values`.

### Task 4: Ticket Type/template and Ticket application model

**Files:**

- Modify: `server/settings.gradle.kts`
- Create: `server/modules/ticket/build.gradle.kts`
- Create: Ticket Type and Ticket domain/application files from File Map plus package-info files.
- Create: `TicketTypeServiceTest.java`, `TicketServiceTest.java`, `TicketOnionArchitectureTest.java`.

**Interfaces:**

- `TicketTypeService`:
  - `UUID create(String key, String name)` creates ACTIVE type + dedicated EAV schema definition.
  - `TicketTypeInfo update(UUID id, String name, TicketTypeStatus status)` changes metadata only.
  - `EavSchemaVersionInfo createTemplate(UUID ticketTypeId)`
  - `EavSchemaVersionInfo replaceTemplate(UUID ticketTypeId, UUID versionId, List<EavFieldSpec> fields)`; rejects common-key collisions.
  - publish/retire/list/get template methods delegate through the owned EAV definition.
- `TicketInfo` fields: `UUID id`, `UUID ticketTypeId`, `UUID templateVersionId`, `String title`, `String status`, `UUID createdBy`, `Instant createdAt`, `Instant updatedAt`, `Map<String,Object> customFields`.
- `TicketService`:
  - `UUID create(UUID ticketTypeId, String title, String status, Map<String, ?> customFields, UUID actorId)`
  - `TicketInfo read(UUID ticketId, ObjectAuthorizationPredicate<TicketInfo> authorization, QueryFieldContext context)`
  - `TicketInfo update(UUID ticketId, TicketPatch patch, ObjectAuthorizationPredicate<TicketInfo> authorization, QueryFieldContext context)`
- `TicketPatch`: absent/nullable common value means unchanged; `customFields` map applies Task 3 PATCH semantics.

- [ ] **Step 1:** Write failing Ticket Type tests for stable identity, rename without data/schema reinterpretation, ACTIVE/RETIRED creation eligibility, template clone, and collision keys `id`, `ticketTypeId`, `title`, `status`, `createdBy`, `createdAt`, `updatedAt`.
- [ ] **Step 2:** Write failing Ticket tests for create pinned to current template, non-blank title/status, actor provenance, historical update, PATCH optional-null deletion, required-null rejection.
- [ ] **Step 3:** Run `./gradlew :modules:ticket:test`; expected FAIL.
- [ ] **Step 4:** Implement application/domain behavior only; no Spring MVC in feature module.
- [ ] **Step 5:** Add Ticket Onion tests.
- [ ] **Step 6:** Run `./gradlew :modules:ticket:test`; expected PASS for unit tests.
- [ ] **Step 7:** Commit `Add Ticket type and Ticket application model`.

### Task 5: Ticket persistence and common-field schema

**Files:**

- Create: Ticket JPA entities/repositories/adapters under `modules/ticket`.
- Modify: `server/modules/database/src/main/resources/db/migration/V1__schema.sql`
- Create: `TicketPersistenceIntegrationTest.java`, `TicketHistoricalSchemaIntegrationTest.java`.

**Interfaces:**

- `ticket_types`: `id`, immutable key, name, ACTIVE/RETIRED status, unique `schema_definition_id`, timestamps.
- `tickets`: `id`, `ticket_type_id`, unique `schema_instance_id`, title, non-blank system status, `created_by`, timestamps, JPA `@Version`.
- `TicketInfo.templateVersionId` resolves from the EAV schema instance; EAV persistence entities are not exposed through Ticket APIs.

- [ ] **Step 1:** Write failing PostgreSQL tests for FKs, unique schema instance, RETIRED type creation failure, historical version stability after later publish, and optimistic-lock conflict.
- [ ] **Step 2:** Run `./gradlew :modules:ticket:test --tests '*PersistenceIntegrationTest' --tests '*HistoricalSchemaIntegrationTest'`; expected FAIL.
- [ ] **Step 3:** Add `ticket_types`/`tickets` tables and indexes to V1 schema; use existing User FK convention for `created_by` where applicable.
- [ ] **Step 4:** Implement JPA adapters; application services own transaction boundaries.
- [ ] **Step 5:** Run `./gradlew :modules:ticket:test`; expected PASS.
- [ ] **Step 6:** Commit `Persist Ticket types and Tickets`.

### Task 6: Query context transport and generic sorting contracts

**Files:**

- Modify: `QueryFieldDescriptor.java`, `FilteredQuery.java`.
- Create: `QuerySort.java`.
- Add/modify Query tests.
- Create: `apps/web/.../query/QueryFieldContextSource.java`, `QueryFieldContextResolver.java`.
- Modify: `FilteredQueryArgumentResolver.java` + tests.

**Interfaces:**

- `QueryFieldDescriptor(..., Set<QueryOperator> operators, boolean sortable)`.
- `record QuerySort(QueryPath path, Direction direction)` where direction is ASC/DESC.
- `record FilteredQuery<Q>(QueryPredicate<Q> predicate, QueryFieldContext context, List<QuerySort> sorting)` with one-arg compatibility constructor using empty context/no sort.
- `QueryFieldContextSource`: `boolean supports(String operation)` and `QueryFieldContext resolve(String operation, NativeWebRequest request)`.
- `QueryFieldContextResolver.resolve(String operation, NativeWebRequest request)` selects zero/one source, defaults empty, rejects multiple, caches immutable context per request+operation.
- `FilteredQueryArgumentResolver` reads optional `sortBy`/`sortOrder`; direction defaults ASC when `sortBy` exists; direction-without-field, bad direction, unknown field, non-sortable field => client error.

- [ ] **Step 1:** Write failing Query tests for sortability, context-carrying FilteredQuery, deterministic identity changes when runtime field snapshot changes.
- [ ] **Step 2:** Write failing Web tests for cached context, no-source empty context, duplicate source, valid/default sort, malformed direction, non-sortable rejection.
- [ ] **Step 3:** Run `./gradlew :modules:query:test :apps:web:test --tests '*FilteredQueryArgumentResolverTest'`; expected FAIL.
- [ ] **Step 4:** Implement contracts/resolver while preserving existing endpoint behavior; existing fields remain non-sortable until explicitly enabled.
- [ ] **Step 5:** Run same tests; expected PASS.
- [ ] **Step 6:** Commit `Carry runtime query context and sorting`.

### Task 7: Dynamic JPA bindings and EAV runtime catalog

**Files:**

- Create: `JpaQueryValueExpression.java`, `JpaQuerySortBinder.java`.
- Modify: `QueryField.java`, `QuerySchema.java`, `JpaQueryExpressionBinder.java`.
- Add jpa-query tests.
- Create: `EavQueryField.java`, `EavQueryFieldCatalog.java`, `EavJpaQueryFieldFactory.java` + EAV query adapter/tests.

**Interfaces:**

- `JpaQueryValueExpression<E,V>.resolve(Root<E>, CriteriaQuery<?>, CriteriaBuilder) -> Expression<V>` and `javaType()`.
- `QueryField.dynamic(QueryPath path, TypeDescriptor type, boolean nullable, Class<V> javaType, JpaQueryValueExpression<E,V> expression)` plus immutable `.operators(...)` and `.sortable()`.
- `JpaQueryExpressionBinder.bind(QueryExpression, QuerySchema<E>, QueryFieldContext)`; existing overload delegates empty context.
- `JpaQuerySortBinder.apply(CriteriaQuery<?>, Root<E>, CriteriaBuilder, QuerySchema<E>, QueryFieldContext, List<QuerySort>, JpaPath<E,?> stableTieBreaker)`.
- Sort binder does not attach ORDER BY when Criteria result type is `Long`/count; otherwise each sort uses missing-last CASE ordering, typed value ordering, then stable `id ASC` once.
- EAV dynamic expression is a correlated scalar subquery keyed by schema-instance ID + field ID; no row evaluates null.
- EAV catalog exposes only keys whose logical identity/type/operator/sort semantics are compatible inside the requested scope; PUBLISHED and RETIRED history participate, DRAFT does not.

- [ ] **Step 1:** Write failing jpa-query tests for dynamic EQ/range/CONTAINS/null binding and context identity.
- [ ] **Step 2:** Add sort tests for ASC/DESC, missing-last, stable tie breaker, and explicit count-query assertion that no ORDER BY is emitted.
- [ ] **Step 3:** Write EAV catalog tests: same identity across versions, removed newer field remains available for history, same-ID type/key mutation impossible, new-ID STRING->ENUM ambiguous, same key/different identity across Ticket Types globally ambiguous.
- [ ] **Step 4:** Write PostgreSQL EAV query tests for numeric/date semantics, null/missing semantics, CONTAINS escaping, and sort ordering.
- [ ] **Step 5:** Run `./gradlew :modules:jpa-query:test :modules:eav:test`; expected FAIL.
- [ ] **Step 6:** Implement dynamic expressions/catalog/factory using Criteria; EAV entity implementation remains internal to EAV.
- [ ] **Step 7:** Run same tests; expected PASS.
- [ ] **Step 8:** Commit `Bind typed EAV fields through JPA queries`.

### Task 8: Ticket runtime QuerySchemas and list persistence

**Files:**

- Create: `TicketQueryFieldCatalog.java`, `ListTicketsQuerySchema.java`, `ReadTicketQuerySchema.java`, `UpdateTicketQuerySchema.java`.
- Create/modify Ticket query repository/binders.
- Modify: `TicketService.java`.
- Add Ticket query integration tests.

**Interfaces:**

- `TicketQueryFieldCatalog.global()`, `forType(UUID ticketTypeId)`, `forTicket(UUID ticketId)` return immutable `QueryFieldContext` snapshots containing resolved EAV query metadata.
- `TicketService.list(int page, int perPage, @Nullable UUID ticketTypeId, FilteredQuery<TicketInfo> query, ObjectAuthorizationPredicate<TicketInfo> authorization)`.
- Optional `ticketTypeId` is both a real DB scope filter and runtime-schema narrowing input. Unscoped list exposes only globally compatible custom fields.
- Operation IDs: `tickets.list`, `tickets.read`, `tickets.update`.
- Static fields: `id`, `ticketTypeId`, `title`, `status`, `createdBy`, `createdAt`, `updatedAt`; user-sortable common fields are title/status/createdAt/updatedAt, with id only as tie breaker.
- For route-time policy validation where `QueryFieldContext.empty()` is supplied, Ticket QuerySchemas resolve runtime fields from `TicketQueryFieldCatalog.global()`; this is the only request-independent dynamic catalog and globally ambiguous fields remain absent/fail closed.

- [ ] **Step 1:** Write query-schema tests for flat namespace, scoped/global catalogs, empty-context global fallback, and exact schema identity.
- [ ] **Step 2:** Write PostgreSQL list tests for numeric filter + pagination, date sort + pagination, missing-last, ID tie break, type-scoped custom success, unscoped ambiguous rejection, and historical compatible fields.
- [ ] **Step 3:** Run `./gradlew :modules:ticket:test --tests '*Query*'`; expected FAIL.
- [ ] **Step 4:** Implement schemas/catalog/list repository; combine `ticketTypeId` scope + client filter before sorting/pageable.
- [ ] **Step 5:** Run `./gradlew :modules:ticket:test`; expected PASS.
- [ ] **Step 6:** Commit `Query Tickets through typed custom fields`.

### Task 9: Runtime-context Object Authorization

**Files:**

- Modify: `ObjectAuthorization.java`, `ObjectAuthorizationService.java`, `ObjectAuthorizationExpressionValidator.java`, `ObjectAuthorizationPredicateModels.java`.
- Modify: authorization JPA object-expression/predicate binders and tests.
- Modify: Web `ObjectAuthorizationPredicateArgumentResolver.java` + tests.
- Add Ticket authorization integration tests.

**Interfaces:**

- Add `ObjectAuthorization.authorize(AuthorizationContext authorization, QuerySchemaView schema, QueryFieldContext queryContext)`; existing overload delegates empty context.
- Validator/model schema identity uses `schema.identity(queryContext)` and field lookup uses the same context.
- JPA authorization binder adds `bind(predicate, QueryFieldContext context)` and rejects schema-identity mismatch; old bind delegates empty context.
- `validatePolicy(policy, method, path)` remains request-independent and validates Ticket routes through Task 8's empty-context/global-compatible schema behavior.
- Web Object Authorization resolver obtains the exact cached context from Task 6's resolver, regardless of whether filter or authorization parameter resolves first.

- [ ] **Step 1:** Write authorization tests for dynamic equality/range/null paths, unsupported operator, globally ambiguous policy rejection during `validatePolicy`, request-scoped field success, and schema-identity mismatch failure.
- [ ] **Step 2:** Write Web resolver tests in both parameter-resolution orders; context source runs once and filter/auth schema identities match.
- [ ] **Step 3:** Write Ticket/PostgreSQL allow+deny custom-field integration with page size smaller than an unauthorized prefix; assert rows and counts are authorization-filtered before pagination.
- [ ] **Step 4:** Run `./gradlew :modules:authorization:test :apps:web:test :modules:ticket:test`; expected FAIL.
- [ ] **Step 5:** Implement context-aware authorization without adding language operators beyond the currently SQL-translatable object subset.
- [ ] **Step 6:** Run same command; expected PASS and no in-memory fallback path.
- [ ] **Step 7:** Commit `Authorize dynamic Ticket fields in persistence`.

### Task 10: Web APIs, request context source, concurrency/history corpus

**Files:**

- Modify: `server/apps/web/build.gradle.kts`
- Create: `apps/web/api/v0/ticket/TicketTypeController.java`, `TicketController.java`, `TicketQueryFieldContextSource.java`, package-info/DTO records.
- Modify: `ApiV0ExceptionHandler.java`.
- Add Web integration tests under `apps/web/src/test/java/io/taskmigo/web/api/v0/ticket/`.
- Extend EAV/Ticket PostgreSQL concurrency/history tests.

**Interfaces:**

- Management endpoints exactly follow design spec section 16.
- Ticket endpoints are POST/GET collection and GET/PATCH item; no DELETE in #236.
- `GET /api/v0/tickets` accepts optional `ticketTypeId`, `filterBy`, `sortBy`, `sortOrder`, and existing offset pagination.
- `TicketQueryFieldContextSource` supports `tickets.list/read/update`: list resolves type/global snapshot; read/update resolve exact historical context from `ticketId` URI variable.
- Unknown/nonexistent `ticketId` during context lookup must not throw a distinguishable existence error from the argument resolver; resolve a safe global/fail-closed context and let the authorized repository lookup determine the existing API's not-found/unauthorized result.
- `customFields` is API payload grouping only; Query/Policy namespace remains flat.
- Create/update actor ID comes from authenticated JWT using existing Web conventions.

- [ ] **Step 1:** Write management API tests for type create/update, draft edit, publish, retire, and Request Authorization denial with zero side effects.
- [ ] **Step 2:** Write Ticket API tests for create/read/update, bad type/constraint/unknown custom key, historical read after new publish, type-scoped filter, sort, and Object Authorization.
- [ ] **Step 3:** Add Review Focus tests: PATCH null optional deletion/required rejection; unscoped ambiguous custom field 400 vs type-scoped success; unknown ticket ID does not leak existence through context resolution.
- [ ] **Step 4:** Write concurrent publish-vs-create test with two transactions/latches; old or new version may win by lock ordering, but validation version must equal persisted schema-instance version.
- [ ] **Step 5:** Write double-publish serialization test and historical corpus: label rename, field removal, STRING -> new-ID ENUM, retire history; old Ticket values stay unchanged/readable.
- [ ] **Step 6:** Run `./gradlew :apps:web:test --tests 'io.taskmigo.web.api.v0.ticket.*' :modules:eav:test :modules:ticket:test`; expected FAIL.
- [ ] **Step 7:** Add Web dependency on `:modules:ticket`, implement controllers/context source/error mappings, and preserve `ApiResponseFactory` envelope conventions.
- [ ] **Step 8:** Run same targeted suites; expected PASS.
- [ ] **Step 9:** Run `./gradlew :apps:web:generateOpenApi`; expected PASS; update committed OpenAPI output only if repository convention produces a change.
- [ ] **Step 10:** Commit `Expose Ticket type and Ticket APIs`.

### Task 11: Full verification and adversarial review

**Files:**

- Modify only files required by defects found during verification/review.
- Do not weaken architecture/security tests to make the build pass.

**Interfaces:**

- No new production API.

- [ ] **Step 1:** Run targeted suite: `cd server && ./gradlew --no-daemon :modules:eav:test :modules:ticket:test :modules:query:test :modules:jpa-query:test :modules:authorization:test :apps:web:test`; expected PASS.
- [ ] **Step 2:** Run `cd .. && npm ci && npm run format:check`; expected PASS.
- [ ] **Step 3:** Run `cd server && ./gradlew --no-daemon build`; expected PASS.
- [ ] **Step 4:** Invoke adversarial-self-review against #236, approved spec, module boundaries, historical invariants, runtime ambiguity, count/sort behavior, authorization fail-closed semantics, and no raw SQL/in-memory fallback.
- [ ] **Step 5:** For each finding, first add/reproduce with the narrowest failing test, implement the fix, and rerun impacted suites.
- [ ] **Step 6:** Re-run `npm run format:check` and `cd server && ./gradlew --no-daemon build`; expected PASS.
- [ ] **Step 7:** Commit `Complete Ticket type schema feature` if fixes changed production/test code.

### Task 12: PR and CI gate

**Files:**

- Read/update PR description using `.github/pull_request_template.md`.
- No code changes unless CI/review exposes a real defect.

**Interfaces:**

- Produces one PR from `feature/236-ticket-types-versioned-schema` to `next`, referencing #236.

- [ ] **Step 1:** Update branch to latest `next` with force-with-lease only when required; rerun impacted verification after conflict resolution.
- [ ] **Step 2:** Open PR with plain-language title and template-compliant description summarizing EAV/Ticket boundaries, flat namespace, historical semantics, database-side query/auth, and verification evidence.
- [ ] **Step 3:** Inspect changed files/diff; confirm no unrelated files, no extra Flyway migration versions, and no Ticket-specific code in EAV query/persistence.
- [ ] **Step 4:** Inspect GitHub checks synchronously in-session; for failures, read job logs and fix root cause rather than blind rerun.
- [ ] **Step 5:** Invoke verification-before-completion before claiming #236 complete.
