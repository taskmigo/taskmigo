# Spring-Native Application Transactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the custom application `*TransactionRunner` pattern from Identity, Access Control, EAV, Ticket, and Audit, and express the same transaction semantics directly with Spring declarative transaction metadata on concrete application implementations.

**Architecture:** Domain packages and published application contracts remain framework-neutral. Concrete application implementations may depend only on `org.springframework.transaction.annotation..`; ordinary transaction runners disappear instead of being moved to Foundation. Specialized persistence and Migration transaction boundaries remain separate and behaviorally unchanged.

**Tech Stack:** Java 26, Gradle 9.7.1, Spring Boot 4, Spring transaction annotations, Spring Modulith, ArchUnit, JUnit 5, AssertJ, PostgreSQL/Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-22-spring-native-application-transactions-design.md`

## Global Constraints

- Preserve observable behavior, rollback, propagation, read-only, isolation, and cross-context atomicity semantics.
- Do not use `taskmigo/specification` to choose or constrain this architecture; this is a maintainer-approved, behavior-preserving architecture migration.
- Domain packages remain free of all `org.springframework..` and `jakarta.persistence..` dependencies.
- Published application contracts remain free of Spring transaction annotations.
- Concrete application implementations may depend only on `org.springframework.transaction.annotation.Transactional`, `Propagation`, and `Isolation` from Spring.
- Do not introduce a shared replacement `TransactionRunner`, custom transaction annotation, Unit of Work, transaction DSL, AspectJ weaving, or a global proxy-mode change.
- Transaction-bearing concrete classes/methods must be proxyable; remove `final` where required rather than relying on proxy configuration changes.
- Ordinary transactions remain `Propagation.REQUIRED`; ordinary reads remain `REQUIRED + readOnly=true`.
- Audit synchronous append/privacy writes remain `Propagation.MANDATORY`.
- `JpaEffectiveStatementResolver` keeps `REQUIRES_NEW + REPEATABLE_READ + readOnly=true`.
- `IdentityEffectiveSubjectResolver` keeps `REQUIRED + readOnly=true` on `resolve` and `expand`.
- `MigrationInstaller` keeps its programmatic SERIALIZABLE/retry/post-commit behavior.
- Keep application unit tests as plain unit tests; do not add Spring contexts solely because production implementations carry `@Transactional`.
- New/changed Spring tests follow `.agents/skills/spring-boot-testing/SKILL.md`: Javadoc Verifies/Given/Expect, `@DisplayName`, `shouldExpectedBehaviorWhenCondition`, Arrange/Act/Assert, AssertJ.
- Run verification sequentially, not in parallel.

## Review Focus

1. **Proxyability and self-invocation:** overloaded/public use-case methods must receive effective transaction metadata even when one overload calls another.
2. **Cross-context `REQUIRED` participation:** Identity -> Access Control -> Audit work must stay in one physical transaction; downstream failure must roll back all participating writes.
3. **Audit `MANDATORY`:** direct append/privacy calls without an owning transaction must fail; calls from a business transaction must commit/rollback with the caller.
4. **Read-only semantics:** representative production query methods must resolve to `REQUIRED + readOnly=true`, not merely compile with `@Transactional` available.
5. **Specialized boundaries:** `JpaEffectiveStatementResolver`, `IdentityEffectiveSubjectResolver`, and Migration must stay outside the ordinary transaction policy and have explicit regression ownership.

---

### Task 1: Allow only declarative Spring transaction metadata in application code

**Files:**
- Modify: `server/testing/architecture/src/main/java/io/taskmigo/architecture/OnionArchitectureRules.java`
- Modify: `server/testing/architecture/src/test/java/io/taskmigo/architecture/OnionArchitectureRulesTest.java`
- Create: `server/testing/architecture/src/test/java/io/taskmigo/architecture/onionfixtures/application/ApplicationTransactionalService.java`
- Create: `server/testing/architecture/src/test/java/io/taskmigo/architecture/onionfixtures/application/ApplicationTransactionTemplateViolation.java`
- Create: `server/testing/architecture/src/test/java/io/taskmigo/architecture/onionfixtures/domain/DomainTransactionalViolation.java`

**Produces:** application packages may depend on Spring transaction annotations only; all other Spring application dependencies remain rejected.

- [ ] **Step 1: Write RED architecture tests**

Add:
- `shouldAllowTransactionalAnnotationWhenApplicationRuleIsChecked`
- `shouldRejectTransactionTemplateWhenApplicationRuleIsChecked`
- `shouldRejectTransactionalAnnotationWhenDomainRuleIsChecked`

Keep the existing unrelated Spring framework violation test.

- [ ] **Step 2: Verify RED**

```bash
cd server
./gradlew --no-daemon :testing:architecture:test --tests io.taskmigo.architecture.OnionArchitectureRulesTest
```

Expected: the annotation-allowance test fails under the current blanket `org.springframework..` ban.

- [ ] **Step 3: Implement the narrow whitelist**

Allow `org.springframework.transaction.annotation..` from configured application packages while still rejecting:
- `org.springframework.transaction.support..`;
- `org.springframework.transaction.PlatformTransactionManager` and other programmatic transaction types;
- unrelated `org.springframework..` packages;
- current persistence/configuration/executable-app forbidden packages.

Do not weaken domain, persistence containment, module isolation, or tactical-test rules.

- [ ] **Step 4: Verify GREEN**

Run the same test command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/testing/architecture
git commit -m "Allow declarative transactions in application code"
```

### Task 2: Migrate Identity and pin ordinary transaction metadata

**Production files:**
- Delete: `server/modules/identity/src/main/java/io/taskmigo/identity/application/TransactionRunner.java`
- Delete: `server/modules/identity/src/main/java/io/taskmigo/identity/transaction/SpringTransactionRunner.java`
- Modify runner-backed application implementations, including:
  - `group/application/DefaultGroupService.java`
  - `user/application/DefaultUserService.java`
  - `user/application/DefaultUserRetentionService.java`
  - `user/application/DefaultUserSessionLifecycleService.java`
  - `user/application/UserRegistrationApplicationService.java`
  - `membership/application/DefaultMembershipService.java`
  - `configuration/application/DefaultConfigurationService.java`
  - `provisioning/application/DefaultGroupProvisioningService.java`
  - `provisioning/application/DefaultIdentityProvisioningService.java`
- Modify runner injection in Group/User/Membership/Configuration/Provisioning application configuration classes.

**Test files:**
- Modify: `server/modules/identity/src/test/java/io/taskmigo/identity/IdentityPackageArchitectureTest.java`
- Create: `server/modules/identity/src/test/java/io/taskmigo/identity/IdentityTransactionMetadataTest.java`
- Update affected Identity application unit tests that currently construct services with `TransactionRunner`.

**Produces:** no Identity transaction runner; ordinary Identity use cases own Spring declarative boundaries directly.

- [ ] **Step 1: Write RED legacy-runner and metadata tests**

In `IdentityPackageArchitectureTest`, add `shouldRejectLegacyTransactionRunnerWhenIdentityPackagesAreInspected` to reject `io.taskmigo.identity.transaction..` and the legacy runner type.

In `IdentityTransactionMetadataTest`, use Spring's `AnnotationTransactionAttributeSource` against production methods and add:
- `shouldExposeReadOnlyRequiredMetadataWhenGroupListIsInspected`
- `shouldExposeRequiredWriteMetadataWhenGroupCreateOverloadsAreInspected`

Assert `DefaultGroupService.list(...)` resolves to read-only + REQUIRED. Assert both public `create(...)` overloads resolve to write + REQUIRED, so self-invocation cannot hide the outer transaction contract.

- [ ] **Step 2: Verify RED**

```bash
cd server
./gradlew --no-daemon :modules:identity:test \
  --tests io.taskmigo.identity.IdentityPackageArchitectureTest \
  --tests io.taskmigo.identity.IdentityTransactionMetadataTest
```

Expected: runner guard fails and metadata tests fail before annotations are added.

- [ ] **Step 3: Translate every Identity runner scope**

For each former `transactions.write(...)`, make the externally callable owning use-case method effectively `@Transactional` with default REQUIRED. For each former `transactions.read(...)`, make the owning public use-case method effectively `@Transactional(readOnly = true)`.

Prefer a class-level write default on mixed services when accurate; override query methods with read-only metadata. Remove `final` from transaction-bearing concrete classes/methods as needed. Unwrap callback bodies into ordinary linear method bodies and remove runner state from constructors.

- [ ] **Step 4: Remove wiring/fakes**

Delete both Identity runner files. Remove runner parameters from application configuration beans. Update affected unit tests to instantiate services without inline/direct transaction runners; keep them context-free.

- [ ] **Step 5: Verify Identity**

```bash
cd server
./gradlew --no-daemon :modules:identity:test
```

Expected: PASS, including metadata and architecture guards.

- [ ] **Step 6: Prove the owning transaction is real under Spring**

```bash
./gradlew --no-daemon :apps:web:test --tests io.taskmigo.web.AuditTransactionIntegrationTest
```

Expected: PASS, including synchronous User + audit commit and rollback when audit persistence fails.

- [ ] **Step 7: Commit**

```bash
git add server/modules/identity
git commit -m "Use declarative transactions in Identity"
```

### Task 3: Migrate Access Control

**Production files:**
- Delete: `server/modules/authorization/src/main/java/io/taskmigo/authorization/transaction/TransactionRunner.java`
- Delete: `server/modules/authorization/src/main/java/io/taskmigo/authorization/transaction/configuration/SpringTransactionRunner.java`
- Modify runner-backed application implementations in Role, Statement, Subject Grant/Query, Role authorization, and Authorization provisioning.
- Modify Role/Statement/Subject/Provisioning configuration beans that inject the runner.

**Tests:**
- Modify: `server/modules/authorization/src/test/java/io/taskmigo/authorization/AccessControlOnionArchitectureTest.java`
- Update affected application unit tests.

- [ ] **Step 1: Write RED architecture guard**

Stop treating `io.taskmigo.authorization.transaction` as an application package and add `shouldRejectLegacyTransactionRunnerWhenAccessControlPackagesAreInspected` rejecting `io.taskmigo.authorization.transaction..`.

- [ ] **Step 2: Verify RED**

```bash
cd server
./gradlew --no-daemon :modules:authorization:test --tests io.taskmigo.authorization.AccessControlOnionArchitectureTest
```

- [ ] **Step 3: Translate runner-backed services**

Map former read scopes to `@Transactional(readOnly = true)` and ordinary write scopes to `@Transactional`/REQUIRED. Make transaction-bearing concrete classes proxyable. Remove runner fields, constructor parameters, callback wrappers, and wiring.

Do not alter `JpaEffectiveStatementResolver` in this task.

- [ ] **Step 4: Delete runner files and update unit tests**

Remove obsolete transaction package/wiring and test construction helpers.

- [ ] **Step 5: Verify Access Control and cross-context joining**

```bash
cd server
./gradlew --no-daemon :modules:authorization:test
./gradlew --no-daemon :apps:web:test \
  --tests io.taskmigo.web.AuditTransactionIntegrationTest \
  --tests io.taskmigo.RoleHierarchyIntegrationTest
```

Expected: PASS; no ordinary `REQUIRES_NEW` boundary is introduced.

- [ ] **Step 6: Commit**

```bash
git add server/modules/authorization
git commit -m "Use declarative transactions in Access Control"
```

### Task 4: Migrate EAV

**Files:**
- Delete: `server/modules/eav/src/main/java/io/taskmigo/eav/schema/application/EavTransactionRunner.java`
- Delete: `server/modules/eav/src/main/java/io/taskmigo/eav/schema/configuration/SpringEavTransactionRunner.java`
- Modify: `server/modules/eav/src/main/java/io/taskmigo/eav/schema/application/EavSchemaServiceImpl.java`
- Modify: `server/modules/eav/src/main/java/io/taskmigo/eav/value/application/EavInstanceServiceImpl.java`
- Modify EAV schema/value configuration classes.
- Modify: `server/modules/eav/src/test/java/io/taskmigo/eav/EavOnionArchitectureTest.java`
- Modify: `server/modules/eav/src/test/java/io/taskmigo/eav/schema/application/EavSchemaServiceTest.java`
- Modify: `server/modules/eav/src/test/java/io/taskmigo/eav/value/application/EavInstanceServiceTest.java`

- [ ] **Step 1: Add RED runner-removal guard**

Add `shouldRejectLegacyTransactionRunnerWhenEavPackagesAreInspected` rejecting both EAV runner types.

- [ ] **Step 2: Verify RED**

```bash
cd server
./gradlew --no-daemon :modules:eav:test --tests io.taskmigo.eav.EavOnionArchitectureTest
```

- [ ] **Step 3: Translate EAV transaction scopes**

Apply ordinary REQUIRED/read-only mapping to `EavSchemaServiceImpl` and `EavInstanceServiceImpl`; make classes proxyable; remove runner constructor state and configuration wiring; delete both runner files.

- [ ] **Step 4: Remove direct unit-test runners**

Delete `DirectTransactionRunner` helpers/imports from `EavSchemaServiceTest` and `EavInstanceServiceTest`; instantiate production classes directly.

- [ ] **Step 5: Verify EAV**

```bash
cd server
./gradlew --no-daemon :modules:eav:test
```

Expected: PASS, including existing PostgreSQL-backed integration tests.

- [ ] **Step 6: Commit**

```bash
git add server/modules/eav
git commit -m "Use declarative transactions in EAV"
```

### Task 5: Migrate Ticket

**Files:**
- Delete: `server/modules/ticket/src/main/java/io/taskmigo/ticket/application/TicketTransactionRunner.java`
- Delete: `server/modules/ticket/src/main/java/io/taskmigo/ticket/configuration/SpringTicketTransactionRunner.java`
- Modify: `server/modules/ticket/src/main/java/io/taskmigo/ticket/application/TicketServiceImpl.java`
- Modify: `server/modules/ticket/src/main/java/io/taskmigo/ticket/type/application/TicketTypeServiceImpl.java`
- Modify: `server/modules/ticket/src/main/java/io/taskmigo/ticket/configuration/TicketConfiguration.java`
- Modify: `server/modules/ticket/src/test/java/io/taskmigo/ticket/TicketOnionArchitectureTest.java`
- Modify: `server/modules/ticket/src/test/java/io/taskmigo/ticket/application/TicketServiceTest.java`
- Modify: `server/modules/ticket/src/test/java/io/taskmigo/ticket/type/application/TicketTypeServiceTest.java`

- [ ] **Step 1: Add RED runner-removal guard**

Add `shouldRejectLegacyTransactionRunnerWhenTicketPackagesAreInspected` rejecting both Ticket runner types.

- [ ] **Step 2: Verify RED**

```bash
cd server
./gradlew --no-daemon :modules:ticket:test --tests io.taskmigo.ticket.TicketOnionArchitectureTest
```

- [ ] **Step 3: Translate Ticket transaction scopes**

Apply ordinary REQUIRED/read-only mapping to Ticket and Ticket Type application implementations; make classes proxyable; remove runner state/wiring and delete both runner files.

- [ ] **Step 4: Update plain unit tests**

Remove runner fixtures/arguments from `TicketServiceTest` and `TicketTypeServiceTest` without adding Spring contexts.

- [ ] **Step 5: Verify Ticket**

```bash
cd server
./gradlew --no-daemon :modules:ticket:test
```

Expected: PASS, including persistence/history/concurrency integration coverage.

- [ ] **Step 6: Commit**

```bash
git add server/modules/ticket
git commit -m "Use declarative transactions in Ticket"
```

### Task 6: Migrate Audit while preserving caller-owned `MANDATORY` writes

**Files:**
- Delete: `server/modules/audit/src/main/java/io/taskmigo/audit/application/AuditTransactionRunner.java`
- Delete: `server/modules/audit/src/main/java/io/taskmigo/audit/transaction/SpringAuditTransactionRunner.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/application/DefaultAuditAppendService.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/application/DefaultAuditPrivacyService.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/application/DefaultAuditQueryService.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/configuration/AuditApplicationConfiguration.java`
- Modify: `server/modules/audit/src/test/java/io/taskmigo/audit/AuditOnionArchitectureTest.java`
- Modify: `server/modules/audit/src/test/java/io/taskmigo/audit/application/DefaultAuditAppendServiceTest.java`
- Modify: `server/apps/web/src/test/java/io/taskmigo/web/AuditTransactionIntegrationTest.java`

- [ ] **Step 1: Add RED runner guard and privacy `MANDATORY` behavior test**

Update `AuditOnionArchitectureTest` so `io.taskmigo.audit..transaction..` is no longer an allowed outer package and add `shouldRejectLegacyTransactionRunnerWhenAuditPackagesAreInspected`.

Inject `AuditPrivacyService` into `AuditTransactionIntegrationTest` and add `shouldRequireExistingTransactionWhenAuditPrivacyScrubIsCalledDirectly`; call `scrubUser` with no owning transaction and assert `IllegalTransactionStateException`.

- [ ] **Step 2: Verify RED architecture guard**

```bash
cd server
./gradlew --no-daemon :modules:audit:test --tests io.taskmigo.audit.AuditOnionArchitectureTest
```

- [ ] **Step 3: Translate Audit semantics exactly**

Apply:
- `DefaultAuditAppendService.append(...)` -> `@Transactional(propagation = Propagation.MANDATORY)`;
- `DefaultAuditPrivacyService.scrubUser(...)` -> `@Transactional(propagation = Propagation.MANDATORY)`;
- `DefaultAuditQueryService` -> `@Transactional(readOnly = true)`.

Make concrete services proxyable. Remove runner fields/constructor parameters. Preserve `taskmigo.audit.enabled` conditional wiring. Delete both runner files.

- [ ] **Step 4: Simplify Audit unit tests**

Remove direct-runner helpers from `DefaultAuditAppendServiceTest`; transaction propagation remains owned by integration tests.

- [ ] **Step 5: Verify module and real transaction behavior**

```bash
cd server
./gradlew --no-daemon :modules:audit:test
./gradlew --no-daemon :apps:web:test --tests io.taskmigo.web.AuditTransactionIntegrationTest
```

Expected: PASS for direct-call MANDATORY, synchronous commit, and rollback-on-audit-failure behavior.

- [ ] **Step 6: Commit**

```bash
git add server/modules/audit server/apps/web/src/test/java/io/taskmigo/web/AuditTransactionIntegrationTest.java
git commit -m "Use declarative transactions in Audit"
```

### Task 7: Pin specialized transaction boundaries and update architecture documentation

**Files:**
- Modify: `server/modules/authorization/src/test/java/io/taskmigo/authorization/request/persistence/EffectiveStatementResolverTest.java`
- Create: `server/modules/identity/src/test/java/io/taskmigo/identity/integration/accesscontrol/IdentityEffectiveSubjectResolverTransactionMetadataTest.java`
- Verify unchanged: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/persistence/JpaEffectiveStatementResolver.java`
- Verify unchanged: `server/modules/identity/src/main/java/io/taskmigo/identity/integration/accesscontrol/IdentityEffectiveSubjectResolver.java`
- Verify unchanged: `server/apps/migration/src/main/java/io/taskmigo/migration/installation/MigrationInstaller.java`
- Verify: `server/apps/migration/src/test/java/io/taskmigo/migration/MigrationIntegrationTest.java`
- Modify: `server/ARCHITECTURE.md`

- [ ] **Step 1: Add focused metadata regression tests**

In `EffectiveStatementResolverTest`, use `AnnotationTransactionAttributeSource` and add `shouldPreserveRepeatableReadRequiresNewMetadataWhenResolveIsInspected`. Assert `resolve(UUID)` is read-only, `PROPAGATION_REQUIRES_NEW`, and `ISOLATION_REPEATABLE_READ`.

Create `IdentityEffectiveSubjectResolverTransactionMetadataTest` with:
- `shouldPreserveReadOnlyRequiredMetadataWhenResolveIsInspected`
- `shouldPreserveReadOnlyRequiredMetadataWhenExpandIsInspected`

Assert both specialized integration methods remain read-only + REQUIRED.

- [ ] **Step 2: Verify specialized tests and Migration behavior**

```bash
cd server
./gradlew --no-daemon :modules:authorization:test \
  --tests io.taskmigo.authorization.request.persistence.EffectiveStatementResolverTest
./gradlew --no-daemon :modules:identity:test \
  --tests io.taskmigo.identity.integration.accesscontrol.IdentityEffectiveSubjectResolverTransactionMetadataTest
./gradlew --no-daemon :apps:migration:test --tests io.taskmigo.migration.MigrationIntegrationTest
```

Expected: PASS. If Tasks 1-6 changed these production boundaries, restore their prior semantics instead of normalizing them.

- [ ] **Step 3: Update `server/ARCHITECTURE.md`**

Document the new model:
- domain remains framework-neutral;
- published contracts remain framework-neutral;
- concrete application implementations may use Spring declarative transaction annotations only;
- application code still cannot use JPA, Spring Data, framework configuration, executable apps, or other Spring APIs;
- Foundation must not receive a replacement transaction abstraction;
- specialized programmatic transaction control remains valid where semantics genuinely require it.

Remove stale statements that require all ordinary application implementations to be Spring-neutral or present transaction runners as the normal inversion mechanism.

- [ ] **Step 4: Commit**

```bash
git add server/modules/authorization/src/test/java/io/taskmigo/authorization/request/persistence/EffectiveStatementResolverTest.java \
        server/modules/identity/src/test/java/io/taskmigo/identity/integration/accesscontrol \
        server/ARCHITECTURE.md
git commit -m "Document and pin Spring-native transaction boundaries"
```

### Task 8: Repository-wide cleanup, final verification, and issue #170 alignment

- [ ] **Step 1: Scan for incomplete migration artifacts**

From repository root:

```bash
rg -n "TransactionRunner|EavTransactionRunner|TicketTransactionRunner|AuditTransactionRunner|Spring.*TransactionRunner" server/modules
rg -n "TransactionTemplate|PlatformTransactionManager|TransactionDefinition" server/modules/*/src/main/java
rg -n "org\.springframework" server/modules/*/src/main/java --glob "**/application/**/*.java"
```

Expected:
- no ordinary runner contract/adapter remains in Identity, Access Control, EAV, Ticket, or Audit;
- programmatic transaction APIs remain only in intentional specialized/non-application locations;
- application implementation Spring imports are limited to `org.springframework.transaction.annotation..`.

- [ ] **Step 2: Run focused verification sequentially**

```bash
cd server
./gradlew --no-daemon :testing:architecture:test
./gradlew --no-daemon :modules:identity:test
./gradlew --no-daemon :modules:authorization:test
./gradlew --no-daemon :modules:eav:test
./gradlew --no-daemon :modules:ticket:test
./gradlew --no-daemon :modules:audit:test
./gradlew --no-daemon :apps:web:test --tests io.taskmigo.web.AuditTransactionIntegrationTest
./gradlew --no-daemon :apps:migration:test --tests io.taskmigo.migration.MigrationIntegrationTest
```

Expected: PASS.

- [ ] **Step 3: Run repository verification**

From repository root:

```bash
npm run format:check
cd server
./gradlew --no-daemon build
```

Expected: PASS. If Formatting CI later emits `formatting.patch`, apply that artifact as a whole per `CONTRIBUTING.md`.

- [ ] **Step 4: Update GitHub issue #170 after implementation matches the plan**

Rewrite stale tracker wording to describe:
- removal, not consolidation, of ordinary `*TransactionRunner` infrastructure across the five modules;
- Spring-native transaction boundaries on concrete application implementations;
- the narrow application Spring annotation whitelist;
- preserved Audit `MANDATORY` semantics;
- preserved specialized resolver/Migration invariants.

Do not mention `taskmigo/specification` as an implementation dependency for this decision.

- [ ] **Step 5: Commit any final repository-only cleanup**

```bash
git add -A
git commit -m "Complete Spring-native transaction migration"
```

Skip this commit if no repository files changed after Task 7; do not create an empty commit.
