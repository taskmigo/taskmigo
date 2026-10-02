# User Retention and Purge Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement issue #233: database-backed User retention configuration, retained/read-only User lifecycle, policy-enforced deletion/mutation restrictions, and reliable purge that preserves audit history.

**Architecture:** Keep User lifecycle ownership inside the Identity module. Store the initial user-manageable configuration key `retention.user` in the database behind a small configuration application port that can grow by key/category later; expose it through the web adapter. Apply existing Object Authorization predicates to the persisted locked User row for mutation decisions, revoke all effective User-owned access at retention time, and let the worker call an Identity purge port whose database locking makes repeated/multi-instance execution safe.

**Tech Stack:** Java 26, Spring Boot 4, Spring Data JPA, Spring Modulith, PostgreSQL/Flyway, JUnit 5, AssertJ, Testcontainers.

**Spec:** GitHub issue #233 is the implementation contract for this work. The external Taskmigo specification repository is not available through the connected repository installation in this execution environment, so specification-tag linkage must be resolved before final PR metadata is marked complete.

## Global Constraints

- Modify the existing `server/modules/database/src/main/resources/db/migration/V1__schema.sql`; do not add another Flyway migration while the application is on major version 1.
- Do not use raw SQL from application code or native repository queries.
- User lifecycle policy restrictions must flow through the existing Request/Object Authorization pipeline; controller code must not duplicate `system` or `RETAINED` business-policy checks.
- `retention.user` accepts ISO-8601 durations equivalent to a non-negative whole number of 24-hour days; `P0D` means immediate purge.
- Retention changes must not reset `retainedAt`.
- Audit rows must survive User purge.
- Verification commands/checks must run sequentially.
- Every Spring test added follows the repository spring-boot-testing conventions; new public Java API documentation uses `///` Javadoc.

## Review Focus

- Equivalent whole-day durations such as `P1D` and `PT24H` normalize consistently while `PT1H`, `PT36H`, negative values, and unknown configuration properties fail.
- Policy evaluation for DELETE/PATCH observes the locked persisted User state, preventing concurrent lifecycle mutation from bypassing `RETAINED`/system restrictions.
- Existing bearer tokens cannot retain effective access after retention because direct grants and memberships are removed and authentication no longer considers the User active.
- Changing retention duration changes purge eligibility without changing `retainedAt`; concurrent/repeated purge executions remain safe.
- Physical purge removes User/profile/access rows while audit rows remain queryable and no live User foreign key blocks deletion.

---

### Task 1: Persist user-manageable retention configuration

**Files:**

- Modify: `server/modules/database/src/main/resources/db/migration/V1__schema.sql`
- Create/modify under: `server/modules/identity/src/main/java/io/taskmigo/identity/configuration/**`
- Test: `server/modules/identity/src/test/java/io/taskmigo/identity/configuration/**`

**Interfaces:**

- Produces: `ConfigurationService.get()`, `ConfigurationService.update(...)`, and a validated/canonical User retention duration available to User lifecycle and purge services.
- Default: `P30D`.

- [ ] Write tests for whole-day duration parsing/normalization, invalid values, persisted default, and partial update.

- [ ] Run the targeted tests and confirm they fail before implementation.

- [ ] Add the generic key/value configuration table/default row to `V1__schema.sql` and implement the Identity configuration model/repository/service using Spring Data.

- [ ] Run targeted tests until green.

- [ ] Commit the coherent configuration-domain chunk.

### Task 2: Add retained User lifecycle state and read model

**Files:**

- Modify: User domain/entity/projection/resource-schema files under `server/modules/identity/src/main/java/io/taskmigo/identity/user/**`
- Modify: `server/modules/database/src/main/resources/db/migration/V1__schema.sql`
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/api/v0/auth/user/UserController.java`
- Test: User domain/persistence/API integration tests.

**Interfaces:**

- Produces: persisted `UserStatus.RETAINED`, nullable `retainedAt`, `UserInfo.status()`, `UserInfo.retainedAt()`, and domain guards preventing managed/profile mutation of retained Users.

- [ ] Write failing tests for ACTIVE/RETAINED projections, authentication disabled for retained Users, and managed reconciliation refusing to reactivate/mutate retained state.

- [ ] Add `RETAINED` + `retained_at`, update aggregate restore/retain/read-only semantics, entity mapping, query/object authorization schemas, and API response.

- [ ] Run targeted tests until green.

- [ ] Commit the lifecycle/read-model chunk.

### Task 3: Implement policy-enforced DELETE and retained mutation denial

**Files:**

- Modify: User application service/ports/persistence binders.
- Modify: membership/grant integration only where necessary to clear existing access.
- Modify: `server/apps/web/.../UserController.java`
- Modify: `server/apps/migration/src/main/resources/migration/statements.yaml` and `roles.yaml` as needed for built-in deny policies.
- Modify: migration target metadata if necessary for mutation routes.
- Test: real-PostgreSQL User API/policy integration tests.

**Interfaces:**

- Consumes: validated retention config and retained User lifecycle.
- Produces: `DELETE /api/v0/users/{userId}`; mutation methods accept/apply an `ObjectAuthorizationPredicate<UserInfo>` against the locked target before writing.

- [ ] Write failing policy integration tests for system User deletion, repeated delete of RETAINED User, retained statement mutation, positive retained transition, and `P0D` immediate purge.

- [ ] Extend Object Authorization route/schema coverage to DELETE/PATCH User targets and add built-in deny statements for `username == "system"` and `status == "RETAINED"` on User mutations.

- [ ] In the User application transaction, lock target, apply the JPA-bound object predicate to that row, clear roles/statements/group memberships, then either retain or purge based on current configuration.

- [ ] Ensure mutation denial uses existing authorization failure behavior rather than domain-validation behavior.

- [ ] Run targeted integration tests until green.

- [ ] Commit the lifecycle mutation/policy chunk.

### Task 4: Expose GET/PATCH configuration API

**Files:**

- Create: web configuration controller/DTOs under `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/api/v0/configuration/**`
- Modify: API integration test client/support.
- Test: configuration API PostgreSQL integration tests.

**Interfaces:**

- Consumes: `ConfigurationService`.
- Produces: `GET /api/v0/configuration` and partial `PATCH /api/v0/configuration` with the same response shape.

- [ ] Write failing integration tests for GET default, PATCH runtime update, omitted fields, invalid durations, unknown properties, persistence across application contexts, and authorization.

- [ ] Implement strict JSON DTOs and controller using existing response/error conventions.

- [ ] Run targeted tests until green.

- [ ] Commit the configuration HTTP chunk.

### Task 5: Add multi-instance-safe retention purge worker

**Files:**

- Add/modify Identity purge inbound port/service and JPA repository methods.
- Modify: `server/apps/worker/build.gradle.kts`
- Create: worker scheduled driving adapter under `server/apps/worker/src/main/java/io/taskmigo/worker/adapter/in/**`
- Test: Identity purge integration tests and worker wiring tests.

**Interfaces:**

- Consumes: current configuration on every purge execution.
- Produces: idempotent `purgeExpiredUsers(Instant now)` behavior; worker invokes it on a fixed schedule, while per-User pessimistic locking prevents duplicate concurrent deletion.

- [ ] Write failing tests for before/after deadline, changed retention policy, active User preservation, repeated purge, and two executor instances sharing one database.

- [ ] Implement eligible retained-user lookup using Spring Data derived/specification APIs, re-lock each candidate before delete, and delete only if still eligible under the current configuration.

- [ ] Wire a scheduled worker adapter and required module dependencies without in-memory ownership assumptions.

- [ ] Run targeted tests until green.

- [ ] Commit the purge chunk.

### Task 6: Prove audit survival, Unknown-user presentation, docs, and full verification

**Files:**

- Modify audit presentation only if the current API resolves a live User display value.
- Modify/add integration tests proving audit history survives purge.
- Regenerate: `server/apps/web/src/main/resources/static/api/docs/openapi.yaml`.
- Update test client/e2e SDK fixtures if generated contract consumers require it.

**Interfaces:**

- Consumes: physical purge from Task 5.
- Produces: surviving immutable audit queries and `Unknown user` fallback only where a live User display value is required.

- [ ] Write failing integration test that creates User audit history, purges the User, and queries the audit API successfully.

- [ ] Add the minimum presentation fallback required by the current audit response contract; do not copy purgeable profile PII into audit storage.

- [ ] Regenerate OpenAPI and verify configuration endpoints plus User lifecycle fields/statuses.

- [ ] Run repository formatting and full server build sequentially, or—when local execution is unavailable—push and use GitHub Actions as the verification source of truth.

- [ ] Perform the bounded adversarial post-implementation review, inspect the full diff, fix blockers/required integration gaps once, and re-run affected verification.

- [ ] Update PR title/body/checklist from the final diff and keep only items proven by verification checked.
