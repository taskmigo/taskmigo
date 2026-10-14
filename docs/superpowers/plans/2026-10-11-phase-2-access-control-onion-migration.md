# Phase 2 Access Control Onion Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Phase 2 of #252 for Role, Statement, Subject, Request Authorization, Object Authorization, Provisioning, and shared Access Control internals without changing authorization behavior, transaction semantics, public contracts, or optimized query performance.

**Architecture:** Treat the current branch as an in-progress migration snapshot, not as a greenfield rewrite. Production code for the Phase 2 capabilities is already largely in the target feature-first Onion packages; finish each capability by migrating its stale tests and consumers, proving the slice independently, and removing the obsolete package path in the same slice. Execute capability PRs sequentially so later slices consume already-stable provider APIs.

**Tech Stack:** Java 26, Gradle 9.7.1, Spring Modulith, ArchUnit, Spring Data JPA, JUnit 5, PostgreSQL-backed integration tests.

**Spec:** GitHub issue #252 and `server/ARCHITECTURE.md`.

## Global Constraints

- Preserve observable business, HTTP/API, security, authorization-policy, transaction, persistence, and query behavior.
- Preserve optimized request/object authorization and SQL/query paths; this is structural migration, not a policy-engine redesign.
- Keep `server/apps/web`, `server/apps/worker`, and `server/apps/migration` as the executable application roots.
- Do not perform Phase 4 app package simplification; app changes in Phase 2 are limited to imports/tests required by moved Access Control contracts.
- No compatibility aliases or parallel old/new package trees; Taskmigo is v0.
- Domain stays framework-neutral. Application must not depend on concrete JPA/Spring Data implementations. Persistence/configuration depend inward.
- Cross-context consumers may use only deliberately published provider-owned contracts; never expose persistence or implementations to make compilation pass.
- Keep transaction cleanup #170 out of scope; preserve current atomicity and specialized transaction behavior.
- Do not add `package-info.java` to retired `application.service`, `application.port.*`, `adapter.*`, or `composition` packages merely to silence checks; eliminate those packages.
- **Follow #252 PR strategy:** one coherent capability per PR, including that capability's affected consumers. Open/merge them sequentially, not six PRs at once. Shared Access Control internals get a final cleanup PR after the capability PRs.
- **Pipeline repair is deferred.** CI may provide verification evidence, but do not modify workflow/pipeline configuration until Phase 2 code migration is complete.

## Current-State Baseline

At the current PR #253 snapshot, the main source trees for Role, Statement, Subject, Request Authorization, Object Authorization, and Provisioning already largely use `application`, `application.api`, `application.internal` where needed, `domain`, `persistence`, and `configuration`. The known incomplete areas are mostly stale tests and consumers:

- Role tests: `role/application/service/**`, `role/adapter/out/persistence/**`.
- Statement tests: `statement/application/service/**`, `statement/adapter/out/persistence/**`.
- Subject tests: `subject/application/service/**`.
- Request tests: `request/application/service/**`, `request/adapter/out/persistence/**`.
- Object tests: `object/application/service/**`.
- Provisioning tests: `provisioning/application/service/**`.
- Identity Subject consumers have already been migrated to `subject.application.api` in commit `efdbda81`.
- Web/Migration still contain stale Access Control imports from old `application.port.*` contracts.

Before implementation, preserve the Phase 0/1 work from PR #253 but do not merge all Phase 2 capabilities into that PR as one umbrella change. Extract/replay the Phase 2 diffs into the sequential capability branches required by #252; the Subject consumer fix belongs with the Subject slice.

## Review Focus

- Stale consumers outside `modules/authorization` must not survive just because the provider module compiles.
- Moved tests must follow the responsibility they exercise without weakening tactical-test isolation.
- `application.api`, Subject resolution, and Object target contracts must remain independent from persistence/configuration/private implementations/framework types.
- Request/Object authorization must not regress into in-memory filtering, N+1 access, or changed SQL/predicate semantics.
- Provisioning must preserve reconciliation idempotency, role/statement relationships, migration-install behavior, and transaction atomicity.

---

### Task 1: Establish the Phase 2 verification gate

**Files:**

- Verify/modify: `server/modules/authorization/src/test/java/io/taskmigo/authorization/AccessControlOnionArchitectureTest.java`.
- Verify/modify: `server/modules/authorization/src/test/java/io/taskmigo/authorization/AccessControlPackageArchitectureTest.java`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/**/package-info.java`.

**Interfaces:**

- Consumes: Phase 0 `io.taskmigo.architecture.OnionArchitectureRules`.
- Produces: regression gates for inward dependency direction and retired Access Control package vocabulary.

- [ ] **Step 1:** Run `cd server && ./gradlew :modules:access-control:compileJava :modules:access-control:compileTestJava`; capture the current stale-package failures as the red baseline.
- [ ] **Step 2:** Ensure `AccessControlPackageArchitectureTest` rejects production packages `io.taskmigo.authorization.adapter..`, `..application.port..`, `..application.service..`, `..composition..`, and unjustified `..infrastructure..`.
- [ ] **Step 3:** Ensure `AccessControlOnionArchitectureTest` classifies `..application..`, `..persistence..`, and `..configuration..`/`..transaction..` correctly without Phase 2 allowlists.
- [ ] **Step 4:** Run `./gradlew :modules:access-control:test --tests 'io.taskmigo.authorization.AccessControl*ArchitectureTest'`; expected PASS once source compilation succeeds.
- [ ] **Step 5:** Commit only if the gates need changes: `test(authorization): tighten Phase 2 architecture gates`.

### Task 2: Role capability PR

**Files:**

- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/role/application/**`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/role/persistence/**`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/role/configuration/**`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/role/application/service/*.java` -> `.../role/application/*.java`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/role/adapter/out/persistence/*.java` -> `.../role/persistence/*.java`.

**Interfaces:**

- Produces: `role.application.api.RoleService`, `RoleAuthorizationService`; private mutation API remains under `role.application.internal`; repositories remain application-owned.

- [ ] **Step 1:** Run the Role tests before edits and confirm the red failures are package/import failures, not intended behavior changes.
- [ ] **Step 2:** Move `DefaultRoleAuthorizationServiceTest`, `DefaultRoleCommandServiceTest`, and `DefaultRoleServiceTest` to `role.application`.
- [ ] **Step 3:** Move `JpaRoleCommandRepositoryTest`, `JpaRoleEffectiveStatementRepositoryTest`, `RoleHierarchyClosureWriterTest`, and `RoleOperationQuerySchemaTest` to `role.persistence`.
- [ ] **Step 4:** Search the whole server for old Role imports under `role.application.port`, `role.application.service`, or `role.adapter`; update every Role consumer in this same slice.
- [ ] **Step 5:** Run `./gradlew :modules:access-control:test --tests 'io.taskmigo.authorization.role.*'`; expected PASS.
- [ ] **Step 6:** Commit `refactor(authorization): complete Role Onion slice`, open the Role PR, review/merge it before Task 3.

### Task 3: Statement capability PR

**Files:**

- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/statement/application/**`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/statement/persistence/**`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/statement/application/service/*.java` -> `.../statement/application/*.java`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/statement/adapter/out/persistence/*.java` -> `.../statement/persistence/*.java`.
- Update imports only as needed: `server/apps/web/src/test/java/io/taskmigo/authorization/statement/adapter/out/persistence/StatementTimestampIntegrationTest.java`.

**Interfaces:**

- Produces: `statement.application.api.StatementService`; mutation API remains private under `statement.application.internal`.

- [ ] **Step 1:** Run existing Statement tests as the behavior baseline.
- [ ] **Step 2:** Move `DefaultStatementCommandServiceTest` and `DefaultStatementServiceTest` to `statement.application`.
- [ ] **Step 3:** Move `JpaStatementCommandRepositoryTest` and `StatementOperationQuerySchemaTest` to `statement.persistence`.
- [ ] **Step 4:** Sweep all old Statement imports in server modules/apps/tests; preserve target matching, timestamp semantics, and query shape.
- [ ] **Step 5:** Run `./gradlew :modules:access-control:test --tests 'io.taskmigo.authorization.statement.*'` plus the existing web Statement timestamp integration test; expected PASS.
- [ ] **Step 6:** Commit `refactor(authorization): complete Statement Onion slice`, open/review/merge the Statement PR before Task 4.

### Task 4: Subject capability PR

**Files:**

- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/subject/application/**`.
- Verify published contract: `server/modules/authorization/src/main/java/io/taskmigo/authorization/subject/application/resolution/**`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/subject/application/service/DefaultSubjectGrantAssignmentServiceTest.java` -> `.../subject/application/DefaultSubjectGrantAssignmentServiceTest.java`.
- Update/replay Subject consumers from `server/modules/identity/src/main/java/io/taskmigo/identity/**`, including the changes currently represented by `efdbda81`.

**Interfaces:**

- Produces: `SubjectGrantAssignmentService`, `SubjectGrantQueryService`, and `SubjectRoleQueryService` from `subject.application.api`; resolution remains deliberately published without implementation leakage.

- [ ] **Step 1:** Keep existing Subject grant/resolution tests as the red/behavior baseline.
- [ ] **Step 2:** Move `DefaultSubjectGrantAssignmentServiceTest` to `subject.application` and update only package/import references.
- [ ] **Step 3:** Sweep `subject.application.port.*` and `subject.application.service` across the whole server; update all Subject consumers in this PR.
- [ ] **Step 4:** Run `./gradlew :modules:access-control:test --tests 'io.taskmigo.authorization.subject.*' :modules:identity:compileJava :modules:identity:compileTestJava`; expected PASS.
- [ ] **Step 5:** Commit `refactor(authorization): complete Subject Onion slice`, open/review/merge the Subject PR before Task 5.

### Task 5: Request Authorization capability PR

**Files:**

- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/application/**`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/domain/**`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/persistence/JpaEffectiveStatementResolver.java`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/request/application/service/*.java` -> `.../request/application/*.java`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/request/adapter/out/persistence/EffectiveStatementResolverTest.java` -> `.../request/persistence/EffectiveStatementResolverTest.java`.

**Interfaces:**

- Produces: `request.application.api.RequestAuthorization`; `EffectiveStatementResolver` remains application-owned and is implemented by persistence.

- [ ] **Step 1:** Run Request tests and preserve decision/result typing, snapshot, artifact, and domain-decider assertions.
- [ ] **Step 2:** Move `RequestAuthorizationResultTypeTest`, `RequestAuthorizationServiceTest`, and `StatementArtifactFactoryTest` to `request.application`.
- [ ] **Step 3:** Move `EffectiveStatementResolverTest` to `request.persistence` and point it at the current persistence implementation/contracts without changing query semantics.
- [ ] **Step 4:** Sweep old Request imports across the whole server and fix Request consumers in this PR.
- [ ] **Step 5:** Run `./gradlew :modules:access-control:test --tests 'io.taskmigo.authorization.request.*'`; expected PASS.
- [ ] **Step 6:** Commit `refactor(authorization): complete Request Authorization Onion slice`, open/review/merge before Task 6.

### Task 6: Object Authorization capability PR

**Files:**

- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/application/**`.
- Verify published contract: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/application/target/**`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/object/domain/**`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/object/application/service/*.java` -> `.../object/application/*.java`.

**Interfaces:**

- Produces: `object.application.api.ObjectAuthorization`; target-resolution contract remains published while persistence/private implementations remain hidden.

- [ ] **Step 1:** Run existing Object tests, especially `Enhancement237ObjectAuthorizationContractTest`, as the behavior baseline.
- [ ] **Step 2:** Move `Enhancement237ObjectAuthorizationContractTest` and `ObjectAuthorizationServiceTest` to `object.application`.
- [ ] **Step 3:** Sweep old Object imports across the whole server and fix Object consumers in this PR.
- [ ] **Step 4:** Verify the migration did not introduce in-memory post-filtering, bypass predicate/expression translation, or add N+1 access.
- [ ] **Step 5:** Run `./gradlew :modules:access-control:test --tests 'io.taskmigo.authorization.object.*'`; expected PASS.
- [ ] **Step 6:** Commit `refactor(authorization): complete Object Authorization Onion slice`, open/review/merge before Task 7.

### Task 7: Provisioning capability PR

**Files:**

- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/provisioning/application/DefaultAuthorizationProvisioningService.java`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/provisioning/application/api/AuthorizationProvisioningService.java`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/provisioning/configuration/AuthorizationProvisioningApplicationConfiguration.java`.
- Move: `server/modules/authorization/src/test/java/io/taskmigo/authorization/provisioning/application/service/DefaultAuthorizationProvisioningServiceTest.java` -> `.../provisioning/application/DefaultAuthorizationProvisioningServiceTest.java`.
- Update imports: `server/apps/migration/src/main/java/io/taskmigo/migration/application/service/MigrationInstaller.java`.
- Update imports: `server/apps/migration/src/main/java/io/taskmigo/migration/application/service/ManagedResourceReconciler.java`.
- Update imports: `server/apps/migration/src/test/java/io/taskmigo/migration/MigrationIntegrationTest.java`.
- Update imports: `server/apps/web/src/test/java/io/taskmigo/PostgresTestConfiguration.java`.

**Interfaces:**

- Produces: `provisioning.application.api.AuthorizationProvisioningService`; consumes final Role/Statement application contracts and the Access Control transaction abstraction.

- [ ] **Step 1:** Run Provisioning tests and retain reconciliation/idempotency/error assertions.
- [ ] **Step 2:** Move `DefaultAuthorizationProvisioningServiceTest` to `provisioning.application`; replace old Role/Statement/transaction imports with final Onion locations.
- [ ] **Step 3:** Update Migration/Web consumers to `provisioning.application.api.AuthorizationProvisioningService` without Phase 4 package restructuring.
- [ ] **Step 4:** Run `./gradlew :modules:access-control:test --tests 'io.taskmigo.authorization.provisioning.*' :apps:migration:compileJava :apps:migration:compileTestJava :apps:web:compileTestJava`; expected PASS.
- [ ] **Step 5:** Run the existing Migration integration test; verify installation/provisioning, retry/transaction, and managed-resource reconciliation semantics are unchanged.
- [ ] **Step 6:** Commit `refactor(authorization): complete Provisioning Onion slice`, open/review/merge before Task 8.

### Task 8: Shared Access Control internals cleanup PR

**Files:**

- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/application/TransactionRunner.java`.
- Verify: `server/modules/authorization/src/main/java/io/taskmigo/authorization/persistence/query/**`.
- Modify as required: `server/modules/authorization/src/test/java/io/taskmigo/authorization/AccessControlOnionArchitectureTest.java`.
- Modify as required: `server/modules/authorization/src/test/java/io/taskmigo/authorization/AccessControlPackageArchitectureTest.java`.
- Update stale named-interface/package docs under `server/modules/authorization/src/main/java/io/taskmigo/authorization/**/package-info.java` only where needed.

**Interfaces:**

- Produces: one coherent Access Control module with no temporary Phase 2 Hexagonal exceptions; transaction abstraction remains application-owned pending #170.

- [ ] **Step 1:** Remove transitional architecture exceptions/allowlists that referred to migrated Access Control packages.
- [ ] **Step 2:** Verify shared internals have a real owner; keep `TransactionRunner` application-owned and shared query mechanics in Access Control persistence only where genuinely shared.
- [ ] **Step 3:** Verify Spring Modulith named interfaces expose only deliberate root contracts, `application.api`, Subject resolution, and Object target contracts; do not publish repositories, persistence, configuration, or implementations.
- [ ] **Step 4:** Run `./gradlew :modules:access-control:test --tests 'io.taskmigo.authorization.AccessControl*ArchitectureTest'`; expected PASS with no Phase 2 exception.
- [ ] **Step 5:** Commit `refactor(authorization): normalize shared Access Control internals`, open/review/merge the cleanup PR.

### Task 9: Phase 2 repository-wide zero-reference gate

**Files:**

- Inspect only unless a missed reference is found: `server/modules/**`, `server/apps/**` Java source/tests.
- Return any discovered stale reference to its owning capability PR rather than creating a cross-capability compatibility commit.

**Interfaces:**

- Consumes: all merged Phase 2 capability contracts.
- Produces: proof that the repository has no live old Access Control package dependency.

- [ ] **Step 1:** Run `git grep -nE 'io\.taskmigo\.authorization\..*(application\.port|application\.service|adapter\.|composition\.)' -- server`.
- [ ] **Step 2:** Expected: zero live Java imports/package declarations. If a match exists, fix it in the owning capability slice, not by adding a shim.
- [ ] **Step 3:** Run `cd server && ./gradlew :modules:access-control:compileJava :modules:access-control:compileTestJava :modules:identity:compileJava :modules:identity:compileTestJava :apps:web:compileJava :apps:web:compileTestJava :apps:worker:compileJava :apps:worker:compileTestJava :apps:migration:compileJava :apps:migration:compileTestJava`; expected PASS.
- [ ] **Step 4:** Run `./gradlew :modules:access-control:test`; expected PASS.

### Task 10: Final Phase 2 review

**Files:**

- Update this plan's checkboxes as slices complete.
- Update #252 progress/checklist after all Phase 2 PRs merge.
- Do not modify `.github/workflows/**` or pipeline configuration in this task.

**Interfaces:**

- Produces: verified completion evidence for Phase 2, ready for the separate pipeline-repair pass if infrastructure-only failures remain.

- [ ] **Step 1:** Verify `ApplicationModules.verify()` coverage and Access Control ArchUnit tests pass on the integrated `next` state.
- [ ] **Step 2:** Compare the integrated Phase 2 diff against the pre-Phase-2 baseline; confirm no HTTP contract, DB schema, authorization semantics, transaction semantics, or query strategy changed.
- [ ] **Step 3:** Run adversarial self-review for dependency-direction leaks, accidental public exposure, stale test packages, transaction regressions, and Request/Object performance regressions.
- [ ] **Step 4:** Re-run the retired-package grep; expected zero live Access Control Hexagonal-only references.
- [ ] **Step 5:** Mark Phase 2 complete in #252 only after all capability PRs and the shared-internals PR are merged and code verification is green.
- [ ] **Step 6:** Only then start a separate pipeline/workflow repair pass for any remaining infrastructure-only failures.

## Execution Order

```text
Architecture gate
    |
    v
Role -> Statement -> Subject -> Request Authorization -> Object Authorization -> Provisioning
                                                                            |
                                                                            v
                                                        Shared Access Control internals
                                                                            |
                                                                            v
                                                   repository zero-reference gate
                                                                            |
                                                                            v
                                                              final Phase 2 review
```

Open and merge one capability PR at a time. This keeps #252's coherent-PR rule, makes failures attributable to one capability, avoids consumer churn against unstable APIs, and prevents the current incomplete branch from turning into a repository-wide rename PR.

## Definition of Done for Phase 2

- [ ] Role, Statement, Subject, Request Authorization, Object Authorization, Provisioning, and shared Access Control internals use feature-first Onion responsibility packages.
- [ ] Every capability PR removes its obsolete path and updates all affected consumers in the same PR.
- [ ] No live Access Control production/test package or import requires `application.port.*`, `application.service`, `adapter.*`, or `composition` vocabulary.
- [ ] No compatibility aliases or duplicate old/new package trees remain.
- [ ] Access Control domain/application dependencies point inward and published contracts remain framework-neutral.
- [ ] Cross-context consumers use only deliberate provider-owned contracts.
- [ ] Spring Modulith and ArchUnit verify the final Access Control graph without Phase 2 transitional exceptions.
- [ ] Existing authorization behavior, policy semantics, transaction semantics, and optimized query/predicate paths are unchanged.
- [ ] Access Control and directly affected Identity/Web/Migration/Worker compile/tests pass as code.
- [ ] Pipeline/workflow repair remains a separate follow-up after Phase 2 code completion.
