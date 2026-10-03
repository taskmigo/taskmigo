# PR #234 User Tombstone Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct PR #234 so its final behavior strictly implements the current Feature #233 User retention and durable `TOMBSTONE` contract.

**Architecture:** Keep the stable User row/id as the historical reference anchor. Separate normal User-management authorization from deletion lifecycle internals: Web/managed provisioning share one deletion-lifecycle service, while Web immediate deletion and Worker expiry share one atomic tombstone operation. Privacy/session/access cleanup is expressed through dedicated ports so the tombstone transaction can scrub OAuth state and audit PII without bypassing normal mutation rules.

**Tech Stack:** Java 26, Spring Boot/Spring Modulith, Spring Data JPA/JDBC, PostgreSQL, Spring Authorization Server, Gradle, JUnit 5, AssertJ, Mockito, Playwright/TypeScript.

**Spec:** GitHub issue #233, "Add User data retention and purge lifecycle" — current issue body is the source of truth for this plan.

## Global Constraints

- Breaking changes are allowed; do not add compatibility shims, synthetic usernames/emails, hashes, or encrypted PII reconstruction values.
- Add only `RETAINED` and `TOMBSTONE`; do not redefine `SUSPENDED`, `DISABLED`, or other existing live-state semantics.
- `TOMBSTONE` is persisted and terminal. Keep only stable User id plus allowed lifecycle metadata.
- `retained_at` is set only when entering `RETAINED`; direct `P0D` deletion must not synthesize a retention transition.
- `tombstoned_at` is set once when entering `TOMBSTONE`.
- DELETE immediately revokes OAuth/session state plus direct roles, direct statements, and group memberships.
- Web immediate deletion and Worker expiry must call the same tombstone application/domain operation.
- Tombstoning is one atomic database transaction including cleanup, historical audit scrub, tombstone audit append, and User state transition.
- Retention Worker runs hourly, uses PostgreSQL skip-locked/equivalent row claiming, retries on later runs, and continues after one User fails.
- Audit access-removal/tombstone fields specified by #233 are sensitive markers; removed values/IDs must not be persisted in those events.
- Historical audit actor username/profile PII is scrubbed while actor/entity ids, timestamps, and change occurrence survive.
- Normal User APIs expose `RETAINED` but exclude `TOMBSTONE`.
- Configuration remains PostgreSQL-backed, default `P30D`, partial PATCH, whole non-negative 24-hour days only.
- Follow the user's execution order: **implementation first, tests second, CI repair last**.

## Current PR #234 Blocking Findings

1. Production code uses `PURGED`; #233 requires persisted `TOMBSTONE`.
2. `tombstoned_at` / `tombstonedAt` is missing; current P0D path incorrectly calls `retain()` before purge and therefore sets `retainedAt`.
3. Physical-delete methods still exist on User command ports/repositories even though the feature requires durable tombstones.
4. `UserResourceSchemas` does not expose `status`, while migration policy evaluates `object.status`; the current object policy is therefore not a valid end-state contract.
5. Mutation statements use `method: "*"` on `/api/v0/users/.*`, which is broader than the mutation surface and can interfere with future GET/detail routes.
6. DELETE does not revoke `oauth2_authorization` / `oauth2_authorization_consent` rows by username before PII removal.
7. Retention/tombstone cleanup is duplicated between Web, Worker, and managed provisioning rather than sharing one tombstone operation.
8. Worker discovery is not skip-locked; one tombstone exception aborts the run instead of continuing with other eligible Users.
9. Tombstone processing cannot defensively remove stale group memberships because normal `MembershipService` correctly rejects retained targets.
10. Audit persistence currently removed `actor_username`, while #233 requires that value before deletion and explicit scrubbing to `Unknown user` at tombstone.
11. Retention DELETE currently writes raw role/statement/group ids to audit; #233 requires sensitive markers with no removed IDs.
12. No historical audit PII scrub exists, and Worker tombstoning does not append the required system-actor tombstone event.
13. `AuditController` currently declares the `audits` field twice, a direct compile blocker.
14. Existing tests still assert physical deletion / old constructors / `AuditActor.username()` contracts, so the branch is internally inconsistent.
15. Configuration has domain parsing tests but lacks the required real-PostgreSQL API/authorization/persistence coverage.

## Review Focus

- Direct `P0D` deletion: `TOMBSTONE` + `tombstonedAt`, while `retainedAt` remains null.
- Username/email reuse after tombstone: the old values must not remain in User, email, OAuth, audit-presentation, or synthetic-identity storage.
- Self-tombstone: appending the tombstone audit event must not reintroduce the deleted User's actor username after historical scrub.
- Worker concurrency/failure: one row is processed by one Worker at a time; one failing User does not block later candidates and remains retryable next run.
- Existing live statuses: non-system `SUSPENDED`/`DISABLED` targets can be deleted without this feature inventing new status semantics.

---

## Phase 1 — Correct production implementation

### Task 1: Rebase the User aggregate and schema on TOMBSTONE semantics

**Files:**

- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/UserStatus.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/domain/User.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/adapter/out/persistence/UserEntity.java`
- Modify: `server/modules/database/src/main/resources/db/migration/V1__schema.sql`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/port/out/UserCommandRepository.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/port/in/internal/UserCommandService.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/service/DefaultUserCommandService.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/adapter/out/persistence/JpaUserCommandRepository.java`

**Interfaces:**

- Produce: `UserStatus.TOMBSTONE`; remove `PURGED`.
- Produce: `User.tombstone(Instant tombstonedAt) -> boolean`.
- Produce: `User.tombstonedAt() -> @Nullable Instant`.
- Remove: physical `delete(User)` from User command service/repository ports.

- [ ] Rename the persisted terminal status from `PURGED` to `TOMBSTONE` everywhere in production code.
- [ ] Add `tombstonedAt` to `User` and `UserEntity`, and add `tombstoned_at timestamptz(3)` to `users`.
- [ ] Encode invariants exactly:
  - live non-deleted: identity/profile present, `retainedAt == null`, `tombstonedAt == null`;
  - `RETAINED`: identity/profile present, `retainedAt != null`, `tombstonedAt == null`;
  - `TOMBSTONE`: username/name/password absent, email collection empty, `tombstonedAt != null`, `retainedAt` may be null (direct P0D) or preserved (expired retention).
- [ ] Make `tombstone(at)` transition directly from any deletable live state or from `RETAINED`; it must never set/reset `retainedAt`.
- [ ] Keep `retain(at)` valid for all deletable non-system live states and preserve first-retention timestamp semantics.
- [ ] Change DB checks to allow nullable PII only for `TOMBSTONE`, require `tombstoned_at` for `TOMBSTONE`, and allow nullable `retained_at` on direct tombstones.
- [ ] Remove the physical User delete methods and JPA `deleteById` path so no lifecycle caller can bypass tombstoning.
- [ ] Run production compilation only:
      `cd server && ./gradlew :modules:identity:compileJava :modules:database:classes --no-daemon --console=plain`.
- [ ] Commit: `Model durable user tombstones`.

### Task 2: Correct User object-policy schema and mutation policy scope

**Files:**

- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/adapter/out/persistence/UserResourceSchemas.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/adapter/out/persistence/query/JpaObjectAuthorizationExpressionBinder.java`
- Modify: `server/apps/migration/src/main/resources/migration/statements.yaml`
- Modify: `server/apps/migration/src/main/resources/migration/roles.yaml`
- Modify: `server/apps/migration/src/main/java/io/taskmigo/migration/infrastructure/config/AuthorizationObjectSchemaConfiguration.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/adapter/out/persistence/JpaUserQueryRepository.java`

**Interfaces:**

- Object policy exposes `object.status` as a string-level policy field backed by `UserStatus` persistence.
- Normal User query/find/exists operations exclude `TOMBSTONE`.

- [ ] Add `status` to the User Object Authorization schema and map it to the persisted `UserStatus`.
- [ ] Add enum-from-string coercion in the JPA Object Authorization binder so `object.status == "RETAINED"` binds to `UserStatus.RETAINED` without weakening other typed comparisons.
- [ ] Replace broad `user_mutation_object_allow` / wildcard retained deny with mutation-specific statements:
  - DELETE object allow;
  - DELETE deny when `status == RETAINED`;
  - DELETE deny when `username == system`;
  - PATCH-statements object allow;
  - PATCH-statements deny when `status == RETAINED`.
- [ ] Do not create a wildcard object deny that could hide retained Users from a future GET/detail route.
- [ ] Keep `TOMBSTONE` out of object-authorized normal User queries; treat it as not a normal management target rather than a retained-policy target.
- [ ] Make `UserQueryRepository.exists` follow the same visible-user semantics so a tombstone cannot be treated as an ordinary existing User by management use cases.
- [ ] Compile Identity + Migration:
      `cd server && ./gradlew :modules:identity:compileJava :apps:migration:compileJava --no-daemon --console=plain`.
- [ ] Commit: `Align user lifecycle policies with tombstones`.

### Task 3: Add deletion-only access/session cleanup primitives

**Files:**

- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/port/out/UserSessionStore.java`
- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/adapter/out/persistence/JdbcUserSessionStore.java`
- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/membership/application/port/in/internal/MembershipCleanupService.java`
- Create/Modify: membership application service implementation and composition files
- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/service/UserAccessRevocation.java`
- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/service/UserAccessRevocationService.java`

**Interfaces:**

- `UserSessionStore.revoke(String username) -> boolean`: delete matching OAuth authorizations and consents.
- `MembershipCleanupService.removeAllForUser(UUID userId) -> boolean`: deletion-lifecycle cleanup that intentionally bypasses normal retained-user mutation checks.
- `UserAccessRevocationService.revoke(User user) -> UserAccessRevocation`: clear sessions, direct roles/statements/groups and report which categories changed, never return removed IDs.

- [ ] Implement JDBC deletion of every `oauth2_authorization` and `oauth2_authorization_consent` row whose `principal_name` equals the User's current username.
- [ ] Add a dedicated membership cleanup path backed by `MembershipRepository`; do not weaken `DefaultMembershipService.requireMutable()`.
- [ ] Implement shared access revocation that snapshots only category presence, revokes OAuth/session state before username can be nulled, and clears direct grants/memberships.
- [ ] Keep cleanup idempotent so retained deletion and later tombstone can safely run it again.
- [ ] Compile Identity:
      `cd server && ./gradlew :modules:identity:compileJava --no-daemon --console=plain`.
- [ ] Commit: `Add deletion lifecycle access revocation`.

### Task 4: Restore the #233 audit persistence/privacy contract

**Files:**

- Modify: `server/modules/database/src/main/resources/db/migration/V1__schema.sql`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/model/AuditActor.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/adapter/out/persistence/AuditLogEntity.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/adapter/out/persistence/JpaAuditLogRepository.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/adapter/out/persistence/JpaAuditLogStore.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/application/port/out/AuditLogStore.java`
- Create: `server/modules/audit/src/main/java/io/taskmigo/audit/application/port/in/privacy/AuditPrivacyService.java`
- Create: `server/modules/audit/src/main/java/io/taskmigo/audit/application/service/DefaultAuditPrivacyService.java`
- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/port/out/UserAuditScrubber.java`
- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/adapter/out/audit/DefaultUserAuditScrubber.java`
- Modify: audit/identity composition as required.

**Interfaces:**

- Restore `AuditActor(UUID id, String username)`.
- `AuditPrivacyService.scrubUser(UUID userId)`: caller must already own the mutation transaction.
- `UserAuditScrubber.scrub(UUID userId)`: Identity-facing adapter to the audit privacy boundary.

- [ ] Restore `audit_logs.actor_username VARCHAR(100) NOT NULL` and persist actor username on normal audit append.
- [ ] Implement actor scrub: all rows with `actor_id = userId` become `actor_username = "Unknown user"`.
- [ ] Implement target-User change scrub: for `entity_type = user AND entity_id = userId`, deserialize `changes_json` and replace raw `username`, `firstName`, `lastName`, and `emails` values with `AuditChange.sensitive(field)`; preserve all other event metadata/change occurrence.
- [ ] Keep audit privacy writes transaction-mandatory so tombstone cleanup participates in the Identity caller's transaction.
- [ ] Update `AuditController` later in Task 8 to use stored/scrubbed actor presentation; do not perform a live User lookup for audit actor username.
- [ ] Compile Audit + Identity:
      `cd server && ./gradlew :modules:audit:compileJava :modules:identity:compileJava --no-daemon --console=plain`.
- [ ] Commit: `Add user audit privacy scrubbing`.

### Task 5: Introduce one shared atomic tombstone operation

**Files:**

- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/port/in/internal/UserTombstoneService.java`
- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/service/DefaultUserTombstoneService.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/composition/UserApplicationConfiguration.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/service/UserAuditChanges.java` only if a small helper for sensitive markers improves reuse.

**Interfaces:**

- `UserTombstoneService.tombstone(User target, UserMutationActor actor, Instant tombstonedAt) -> void`.
- Caller owns one write transaction and a row lock for `target`.

- [ ] Snapshot only the local facts needed to emit sensitive markers before mutation; do not persist removed values.
- [ ] Revoke remaining OAuth/session/access state through Task 3 before losing username.
- [ ] Call `target.tombstone(tombstonedAt)`, save the aggregate, and ensure email collection rows are deleted by persistence.
- [ ] Scrub historical audit PII through Task 4.
- [ ] Append exactly one tombstone audit event in the same transaction:
  - sensitive: `username`, `firstName`, `lastName`, `emails`, `passwordHash`, `roleIds`, `statementIds`, `groupIds`, and session/token marker when represented;
  - visible: `status -> TOMBSTONE`, `tombstonedAt`;
  - never include removed values/IDs.
- [ ] If `actor.id == target.id`, write `Unknown user` as the new tombstone event's actor username so the event does not reintroduce PII after scrub.
- [ ] Preserve `retainedAt` if the User was retained; leave it null for direct live -> TOMBSTONE.
- [ ] Compile Identity + Audit:
      `cd server && ./gradlew :modules:identity:compileJava :modules:audit:compileJava --no-daemon --console=plain`.
- [ ] Commit: `Add atomic user tombstone operation`.

### Task 6: Route Web and managed deletion through one deletion lifecycle

**Files:**

- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/port/in/internal/UserDeletionLifecycleService.java`
- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/service/DefaultUserDeletionLifecycleService.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/service/DefaultUserService.java`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/provisioning/application/service/DefaultIdentityProvisioningService.java`
- Modify: corresponding composition classes.

**Interfaces:**

- `UserDeletionLifecycleService.delete(User target, UserMutationActor actor, Instant occurredAt) -> void`.
- Reads current `retention.user`; non-zero retains, zero delegates to Task 5.

- [ ] For retention > P0D: call shared access revocation, transition to `RETAINED`, save, and append one deletion audit event with visible `status`/`retainedAt` plus sensitive `roleIds`/`statementIds`/`groupIds` markers when changed.
- [ ] For P0D: call Task 5 directly; do not call `retain()` first.
- [ ] Keep Web's persisted-target object policy decision before lifecycle execution; system and repeated retained DELETE remain policy denials.
- [ ] Return NOT_FOUND/not-a-normal-target semantics for `TOMBSTONE` instead of trying to authorize/mutate it.
- [ ] Make managed deletion resolve the persisted system User as actor and use the same lifecycle service; managed reconciliation of a `RETAINED` User remains rejected by `requireMutable()`.
- [ ] Ensure DELETE accepts other existing live non-system statuses without defining new status transitions beyond deletion itself.
- [ ] Compile Web + Migration + Identity:
      `cd server && ./gradlew :modules:identity:compileJava :apps:web:compileJava :apps:migration:compileJava --no-daemon --console=plain`.
- [ ] Commit: `Unify user deletion lifecycle`.

### Task 7: Make retention expiry multi-worker safe and failure-isolated

**Files:**

- Create: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/port/in/internal/RetainedUserCandidate.java`
- Modify: `UserCommandRepository`, `UserCommandService`, `DefaultUserCommandService`, `JpaUserRepository`, `JpaUserCommandRepository`
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/service/DefaultUserRetentionService.java`
- Modify: `server/apps/worker/src/main/java/io/taskmigo/worker/adapter/in/retention/UserRetentionPurgeJob.java`

**Interfaces:**

- Candidate scan uses keyset order `(retainedAt, id)` so one failed id does not starve later eligible rows in the same run.
- `claimRetainedForUpdate(UUID id) -> Optional<User>` uses PostgreSQL `FOR UPDATE SKIP LOCKED`.
- Retention service calls Task 5 for the claimed User.

- [ ] Replace blocking per-id row lock for expiry with a PostgreSQL skip-locked claim.
- [ ] Revalidate `status == RETAINED` and `retainedAt <= cutoff` after claim and before tombstone.
- [ ] Iterate candidate pages by keyset; process each claimed User in its own transaction.
- [ ] Catch/log a per-User tombstone failure without PII, advance the current-run cursor, and continue; rollback leaves that User RETAINED for a later hourly run.
- [ ] Resolve the persisted system User and use it as Worker tombstone audit actor.
- [ ] Make the Worker schedule exactly hourly (`fixedDelay = 1`, `TimeUnit.HOURS`) unless an existing project convention requires an equivalent exact one-hour expression.
- [ ] Compile Worker + Identity:
      `cd server && ./gradlew :modules:identity:compileJava :apps:worker:compileJava --no-daemon --console=plain`.
- [ ] Commit: `Claim retained users safely across workers`.

### Task 8: Finish API/configuration/presentation cleanup and remove stale PURGED assumptions

**Files:**

- Modify: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/api/v0/audit/AuditController.java`
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/api/v0/auth/user/UserController.java`
- Modify: `server/apps/web/src/main/java/io/taskmigo/web/adapter/in/http/api/v0/configuration/ConfigurationController.java` only for contract defects discovered while compiling.
- Modify: `server/modules/identity/src/main/java/io/taskmigo/identity/configuration/RetentionDuration.java`
- Modify: `e2e/sdk/api/v0/users.ts`
- Remove/update any stale `PURGED`, "physical purge", and old live-lookup audit presentation references.

**Interfaces:**

- Audit actor presentation comes from persisted `actor_username`, which becomes `Unknown user` through Task 4 scrub.
- User API returns live states + `RETAINED`; repository filtering guarantees no `TOMBSTONE` response.
- Configuration stays `{ retention: { user: "P<n>D" } }` through existing API envelope conventions.

- [ ] Remove the duplicate `AuditController.audits` declaration and remove its `UserService` dependency/live actor lookup.
- [ ] Ensure User list/controller never constructs a response from a tombstone.
- [ ] Keep E2E User response schema limited to statuses reachable through `GET /users`; do not add `TOMBSTONE` merely because the persisted enum contains it.
- [ ] Update retention comments/messages from "physical purge" to tombstone terminology.
- [ ] Confirm default `P30D`, partial PATCH, unknown-property rejection, and whole-day canonicalization require no incompatible workaround.
- [ ] Compile all production modules:
      `cd server && ./gradlew compileJava --no-daemon --console=plain`.
- [ ] Commit: `Align APIs with user tombstone contract`.

## Phase 2 — Add/repair tests after implementation is stable

### Task 9: Repair unit tests to the new contracts

**Files:**

- Modify: `server/modules/identity/src/test/java/io/taskmigo/identity/user/domain/UserTest.java`
- Modify: `server/modules/identity/src/test/java/io/taskmigo/identity/user/application/service/DefaultUserServiceTest.java`
- Modify: `server/modules/identity/src/test/java/io/taskmigo/identity/user/application/service/DefaultUserRetentionServiceTest.java`
- Modify: `server/modules/identity/src/test/java/io/taskmigo/identity/provisioning/application/service/DefaultIdentityProvisioningServiceTest.java`
- Modify: `server/modules/identity/src/test/java/io/taskmigo/identity/membership/application/service/DefaultMembershipServiceTest.java`
- Modify: audit tests currently constructing `AuditActor`.
- Add focused tests for enum policy coercion and audit scrub service/store.

- [ ] Replace all physical-delete / `PURGED` expectations with `TOMBSTONE` state assertions.
- [ ] Cover direct live -> tombstone: `retainedAt == null`, `tombstonedAt == requested time`.
- [ ] Cover retained -> tombstone: original `retainedAt` unchanged, `tombstonedAt` set once.
- [ ] Cover terminal `TOMBSTONE` mutation rejection and retained mutation rejection.
- [ ] Cover SUSPENDED/DISABLED deletion eligibility without changing their unrelated semantics.
- [ ] Cover object-policy string `"RETAINED"` -> `UserStatus.RETAINED` persistence binding.
- [ ] Cover audit scrub conversion of raw profile fields to sensitive markers and actor username to `Unknown user`.
- [ ] Run:
      `cd server && ./gradlew :modules:identity:test :modules:audit:test :modules:access-control:test --no-daemon --console=plain`.
- [ ] Commit: `Test user tombstone domain contracts`.

### Task 10: Add real-PostgreSQL User lifecycle and audit integration coverage

**Files:**

- Create: `server/apps/web/src/test/java/io/taskmigo/web/adapter/in/http/api/v0/auth/user/UserDeletionLifecycleIntegrationTest.java`
- Extend only where appropriate: `UserApiIntegrationTest.java`, `AuditApiIntegrationTest.java`, `AuditTransactionIntegrationTest.java`
- Modify test API client helpers.

- [ ] Scenario: P30D DELETE -> RETAINED, retainedAt set once, roles/statements/groups removed, OAuth authorization/consent rows removed, authentication/old-token use denied.
- [ ] Scenario: repeated RETAINED DELETE -> policy 403 and retainedAt byte-for-byte unchanged.
- [ ] Scenario: system User DELETE -> policy 403.
- [ ] Scenario: P0D DELETE -> TOMBSTONE synchronously, tombstonedAt set, retainedAt null, all PII/email/access/session rows purged.
- [ ] Scenario: TOMBSTONE is absent from normal User list/targeting.
- [ ] Scenario: former username and email can immediately be reused after tombstone.
- [ ] Scenario: retained deletion audit has sensitive roleIds/statementIds/groupIds markers and contains no removed IDs.
- [ ] Scenario: tombstone event contains sensitive purge field markers + visible TOMBSTONE/tombstonedAt, never removed values.
- [ ] Scenario: historical actor username/profile audit PII is scrubbed but ids/timestamps/history survive and API returns `Unknown user`.
- [ ] Scenario: transaction failure during tombstone rolls back User/access/session/audit scrub together and leaves the User retryable.
- [ ] Run:
      `cd server && ./gradlew :apps:web:test --tests '*UserDeletionLifecycleIntegrationTest' --tests '*Audit*IntegrationTest' --no-daemon --console=plain`.
- [ ] Commit: `Cover user deletion lifecycle in PostgreSQL`.

### Task 11: Add configuration API PostgreSQL integration coverage

**Files:**

- Create: `server/apps/web/src/test/java/io/taskmigo/web/adapter/in/http/api/v0/configuration/ConfigurationApiIntegrationTest.java`

- [ ] GET with the DB row absent falls back to `P30D`.
- [ ] PATCH accepts `P0D`, `P1D`, `PT24H`, `P30D` and persists the effective canonical value.
- [ ] PATCH rejects `-P1D`, `PT1H`, `PT36H`, `P1DT12H`.
- [ ] PATCH is partial and rejects unknown root/nested properties.
- [ ] A later transaction/service read sees the updated DB value; no process-local cache is allowed.
- [ ] Unauthorized callers cannot GET/PATCH configuration.
- [ ] Updating retention does not modify existing Users' retainedAt.
- [ ] Run:
      `cd server && ./gradlew :apps:web:test --tests '*ConfigurationApiIntegrationTest' --no-daemon --console=plain`.
- [ ] Commit: `Test user retention configuration API`.

### Task 12: Add Worker concurrency, expiry, and retry integration coverage

**Files:**

- Create: `server/apps/web/src/test/java/io/taskmigo/UserRetentionWorkerIntegrationTest.java` or the nearest existing PostgreSQL integration-test package that can inject the Identity service without duplicating infrastructure.
- Create/Modify: `server/apps/worker/src/test/java/io/taskmigo/worker/adapter/in/retention/UserRetentionPurgeJobTest.java`.

- [ ] Expired RETAINED User becomes TOMBSTONE; unexpired/live User remains unchanged.
- [ ] Changing retention changes cutoff eligibility without touching retainedAt.
- [ ] Two concurrent purge-service invocations against one expired User produce one committed tombstone transition and one tombstone audit event.
- [ ] A deliberately failing tombstone candidate rolls back, later eligible candidates in the same run still complete, and the failed candidate succeeds on a later run.
- [ ] Worker-generated tombstone event actor is the persisted system User.
- [ ] Schedule test pins the job interval to one hour.
- [ ] Run:
      `cd server && ./gradlew :apps:web:test --tests '*UserRetentionWorkerIntegrationTest' :apps:worker:test --tests '*UserRetentionPurgeJobTest' --no-daemon --console=plain`.
- [ ] Commit: `Test multi-worker retention expiry`.

### Task 13: Verify surviving references and E2E contract

**Files:**

- Modify: existing audit/User E2E tests and SDK helpers as needed.
- Do not invent ticket/comment/approval production modules that do not exist in the repository.

- [ ] Prove the currently implemented historical record type (audit) survives tombstoning with the same User id and `Unknown user` presentation.
- [ ] Assert the tombstone row itself keeps the original UUID while all normal User API results exclude it.
- [ ] Run TypeScript contract check: `npm --prefix e2e run typecheck`.
- [ ] Regenerate OpenAPI and verify tracked snapshot:
      `cd server && ./gradlew :apps:web:generateOpenApi --no-daemon --console=plain && git diff --exit-code -- apps/web/src/main/resources/static/api/docs/openapi.yaml`.
- [ ] If OpenAPI legitimately changes, update the snapshot and rerun the diff check.
- [ ] Commit: `Align API contracts with tombstone lifecycle`.

## Phase 3 — Adversarial review, full verification, CI repair

### Task 14: Adversarial spec-to-diff review

**Files:** no planned production change unless the review finds a must-fix defect.

- [ ] Re-read current #233 and map every acceptance criterion to a production path and test.
- [ ] Search the branch for stale `PURGED`, physical User deletion, synthetic deleted identity, raw removed IDs in retention/tombstone audit, and User PII copied into tombstones.
- [ ] Search every current User mutation entrypoint (roles/statements/groups/profile/credential/status/provisioning) and confirm RETAINED cannot mutate.
- [ ] Verify only deletion-lifecycle cleanup bypasses normal retained mutation guards.
- [ ] Verify every tombstone path revokes sessions before username is nulled.
- [ ] Verify no code can restore a TOMBSTONE by id; username reuse creates a new User id.
- [ ] Apply every must-fix finding before moving to full CI.

### Task 15: Full local verification

- [ ] Formatting: `npm ci && npm run format:fix && git diff --exit-code`.
- [ ] Full server build: `cd server && ./gradlew --no-daemon --console=plain --continue build`.
- [ ] OpenAPI: `cd server && ./gradlew --no-daemon --console=plain :apps:web:generateOpenApi && git diff --exit-code -- apps/web/src/main/resources/static/api/docs/openapi.yaml`.
- [ ] E2E typecheck: `npm --prefix e2e run typecheck`.
- [ ] Only after the above is clean, push and inspect PR checks.

### Task 16: CI repair and PR finalization

- [ ] Repair CI in dependency order: Formatting/Server -> Kubernetes/E2E -> Qodana -> Authorization performance.
- [ ] For each failure, inspect the actual workflow log and fix the root cause; do not preemptively refactor unrelated code.
- [ ] Re-run failed checks until all required checks pass.
- [ ] Replace the stale PR #234 description with the actual TOMBSTONE architecture, verification performed, breaking changes, and a current link/reference to #233 consistent with the PR template.
- [ ] Remove or supersede the obsolete `docs/superpowers/plans/2026-10-02-user-retention-and-purge.md` so reviewers do not follow the old physical-purge/PURGED assumptions.
- [ ] Final Superpowers verification-before-completion + repository adversarial-self-review before claiming PR ready.
