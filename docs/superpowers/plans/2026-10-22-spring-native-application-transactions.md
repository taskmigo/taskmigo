# Spring-Native Application Transactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the custom application `*TransactionRunner` pattern from Identity, Access Control, EAV, Ticket, and Audit, and express the same transaction semantics directly with Spring declarative transaction metadata on concrete application implementations.

**Architecture:** Domain packages and published application contracts remain framework-neutral. Concrete application implementations may depend only on `org.springframework.transaction.annotation..`; ordinary transaction runners disappear instead of being moved to Foundation, while specialized persistence/Migration transaction boundaries remain unchanged.

**Tech Stack:** Java 26, Gradle 9.7.1, Spring Boot 4, Spring transaction annotations, Spring Modulith, ArchUnit, JUnit 5, AssertJ, PostgreSQL/Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-22-spring-native-application-transactions-design.md`

## Global Constraints

- Preserve current observable behavior, rollback, propagation, read-only, isolation, and cross-context atomicity semantics.
- Do not use `taskmigo/specification` to choose or constrain this architecture; this is a maintainer-approved, behavior-preserving architecture migration.
- Domain packages remain free of all `org.springframework..` and `jakarta.persistence..` dependencies.
- Published application contracts remain free of Spring transaction annotations.
- Concrete application implementations may depend only on `org.springframework.transaction.annotation.Transactional`, `Propagation`, and `Isolation` from Spring.
- Do not introduce a shared replacement `TransactionRunner`, custom transaction annotation, Unit of Work, transaction DSL, AspectJ weaving, or a global proxy-mode change.
- Transaction-bearing concrete classes/methods must be proxyable; remove `final` where required rather than relying on proxy configuration changes.
- Ordinary transactions remain `Propagation.REQUIRED`; ordinary reads remain `REQUIRED + readOnly=true`.
- Audit synchronous append/privacy writes remain `Propagation.MANDATORY`.
- `JpaEffectiveStatementResolver` keeps `REQUIRES_NEW + REPEATABLE_READ + readOnly=true`.
- `IdentityEffectiveSubjectResolver` keeps behaviorally equivalent read-only transaction handling.
- `MigrationInstaller` keeps its programmatic SERIALIZABLE/retry/post-commit behavior.
- Keep application unit tests as plain unit tests; do not add Spring contexts solely because production implementations carry `@Transactional`.
- New/changed Spring tests follow `.agents/skills/spring-boot-testing/SKILL.md`: `@DisplayName`, `shouldExpectedBehaviorWhenCondition`, Javadoc Given/Expect, Arrange/Act/Assert, AssertJ.
- Run verification sequentially, not in parallel.

## Review Focus

1. **Proxyability and self-invocation:** overloaded/public use-case methods must receive effective transaction metadata even when one overload calls another; module architecture tests and Spring-managed integration calls must prove no transaction boundary disappears.
2. **Cross-context `REQUIRED` participation:** Identity -> Access Control -> Audit work must stay in one physical transaction; `AuditTransactionIntegrationTest` must still prove downstream audit failure rolls back the owning User mutation.
3. **Audit `MANDATORY`:** direct append/privacy calls without an owning transaction must fail with `IllegalTransactionStateException`, while calls from a business transaction must commit/rollback with the caller.
4. **Read-only semantics:** methods formerly wrapped by `transactions.read(...)` must have effective `readOnly=true`; architecture/transaction metadata tests must cover representative production services and module integration tests must stay green.
5. **Specialized boundaries:** `JpaEffectiveStatementResolver`, `IdentityEffectiveSubjectResolver`, and Migration must not be normalized into the ordinary policy; targeted tests plus final source scans must prove their current semantics remain intact.

---

## File Structure

### Shared architecture enforcement

- Modify: `server/testing/architecture/src/main/java/io/taskmigo/architecture/OnionArchitectureRules.java`
- Modify: `server/testing/architecture/src/test/java/io/taskmigo/architecture/OnionArchitectureRulesTest.java`
- Create: `server/testing/architecture/src/test/java/io/taskmigo/architecture/onionfixtures/application/ApplicationTransactionalService.java`
- Create: `server/testing/architecture/src/test/java/io/taskmigo/architecture/onionfixtures/application/ApplicationTransactionTemplateViolation.java`
- Create: `server/testing/architecture/src/test/java/io/taskmigo/architecture/onionfixtures/domain/DomainTransactionalViolation.java`

### Identity

- Delete: `server/modules/identity/src/main/java/io/taskmigo/identity/application/TransactionRunner.java`
- Delete: `server/modules/identity/src/main/java/io/taskmigo/identity/transaction/SpringTransactionRunner.java`
- Modify application implementations currently depending on the runner, including:
  - `server/modules/identity/src/main/java/io/taskmigo/identity/group/application/DefaultGroupService.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/DefaultUserService.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/DefaultUserRetentionService.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/DefaultUserSessionLifecycleService.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/user/application/UserRegistrationApplicationService.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/membership/application/DefaultMembershipService.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/configuration/application/DefaultConfigurationService.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/provisioning/application/DefaultGroupProvisioningService.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/provisioning/application/DefaultIdentityProvisioningService.java`
- Modify wiring that currently injects the runner:
  - `server/modules/identity/src/main/java/io/taskmigo/identity/group/configuration/GroupApplicationConfiguration.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/user/configuration/UserApplicationConfiguration.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/membership/configuration/MembershipApplicationConfiguration.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/configuration/configuration/ConfigurationApplicationConfiguration.java`
  - `server/modules/identity/src/main/java/io/taskmigo/identity/provisioning/configuration/ProvisioningApplicationConfiguration.java`
- Modify: `server/modules/identity/src/test/java/io/taskmigo/identity/IdentityPackageArchitectureTest.java`
- Update affected application unit tests under `server/modules/identity/src/test/java/io/taskmigo/identity/**/application/**` that currently construct services with a `TransactionRunner`.

### Access Control

- Delete: `server/modules/authorization/src/main/java/io/taskmigo/authorization/transaction/TransactionRunner.java`
- Delete: `server/modules/authorization/src/main/java/io/taskmigo/authorization/transaction/configuration/SpringTransactionRunner.java`
- Modify application implementations currently depending on the runner, including:
  - `server/modules/authorization/src/main/java/io/taskmigo/authorization/role/application/DefaultRoleService.java`
  - `server/modules/authorization/src/main/java/io/taskmigo/authorization/role/application/DefaultRoleAuthorizationService.java`
  - `server/modules/authorization/src/main/java/io/taskmigo/authorization/statement/application/DefaultStatementService.java`
  - `server/modules/authorization/src/main/java/io/taskmigo/authorization/subject/application/DefaultSubjectGrantAssignmentService.java`
  - `server/modules/authorization/src/main/java/io/taskmigo/authorization/subject/application/DefaultSubjectGrantQueryService.java`
  - `server/modules/authorization/src/main/java/io/taskmigo/authorization/subject/application/DefaultSubjectRoleQueryService.java`
  - `server/modules/authorization/src/main/java/io/taskmigo/authorization/provisioning/application/service/DefaultAuthorizationProvisioningService.java`
- Modify runner wiring in Role, Statement, Subject, and Provisioning configuration classes.
- Modify: `server/modules/authorization/src/test/java/io/taskmigo/authorization/AccessControlOnionArchitectureTest.java`
- Update affected unit tests in the corresponding application test packages.

### EAV

- Delete: `server/modules/eav/src/main/java/io/taskmigo/eav/schema/application/EavTransactionRunner.java`
- Delete: `server/modules/eav/src/main/java/io/taskmigo/eav/schema/configuration/SpringEavTransactionRunner.java`
- Modify: `server/modules/eav/src/main/java/io/taskmigo/eav/schema/application/EavSchemaServiceImpl.java`
- Modify: `server/modules/eav/src/main/java/io/taskmigo/eav/value/application/EavInstanceServiceImpl.java`
- Modify: `server/modules/eav/src/main/java/io/taskmigo/eav/schema/configuration/EavSchemaConfiguration.java`
- Modify: `server/modules/eav/src/main/java/io/taskmigo/eav/value/configuration/EavValueConfiguration.java`
- Modify: `server/modules/eav/src/test/java/io/taskmigo/eav/schema/application/EavSchemaServiceTest.java`
- Modify: `server/modules/eav/src/test/java/io/taskmigo/eav/value/application/EavInstanceServiceTest.java`
- Modify: `server/modules/eav/src/test/java/io/taskmigo/eav/EavOnionArchitectureTest.java`

### Ticket

- Delete: `server/modules/ticket/src/main/java/io/taskmigo/ticket/application/TicketTransactionRunner.java`
- Delete: `server/modules/ticket/src/main/java/io/taskmigo/ticket/configuration/SpringTicketTransactionRunner.java`
- Modify: `server/modules/ticket/src/main/java/io/taskmigo/ticket/application/TicketServiceImpl.java`
- Modify: `server/modules/ticket/src/main/java/io/taskmigo/ticket/type/application/TicketTypeServiceImpl.java`
- Modify: `server/modules/ticket/src/main/java/io/taskmigo/ticket/configuration/TicketConfiguration.java`
- Modify: `server/modules/ticket/src/test/java/io/taskmigo/ticket/application/TicketServiceTest.java`
- Modify: `server/modules/ticket/src/test/java/io/taskmigo/ticket/type/application/TicketTypeServiceTest.java`
- Modify: `server/modules/ticket/src/test/java/io/taskmigo/ticket/TicketOnionArchitectureTest.java`

### Audit

- Delete: `server/modules/audit/src/main/java/io/taskmigo/audit/application/AuditTransactionRunner.java`
- Delete: `server/modules/audit/src/main/java/io/taskmigo/audit/transaction/SpringAuditTransactionRunner.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/application/DefaultAuditAppendService.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/application/DefaultAuditPrivacyService.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/application/DefaultAuditQueryService.java`
- Modify: `server/modules/audit/src/main/java/io/taskmigo/audit/configuration/AuditApplicationConfiguration.java`
- Modify: `server/modules/audit/src/test/java/io/taskmigo/audit/application/DefaultAuditAppendServiceTest.java`
- Modify: `server/modules/audit/src/test/java/io/taskmigo/audit/AuditOnionArchitectureTest.java`
- Modify: `server/apps/web/src/test/java/io/taskmigo/web/AuditTransactionIntegrationTest.java`

### Architecture documentation / specialized verification

- Modify: `server/ARCHITECTURE.md`
- Verify unchanged: `server/modules/authorization/src/main/java/io/taskmigo/authorization/request/persistence/JpaEffectiveStatementResolver.java`
- Verify unchanged: `server/modules/identity/src/main/java/io/taskmigo/identity/integration/accesscontrol/IdentityEffectiveSubjectResolver.java`
- Verify unchanged: `server/apps/migration/src/main/java/io/taskmigo/migration/installation/MigrationInstaller.java`
- Verify/update tests only when needed to pin existing semantics:
  - `server/modules/authorization/src/test/java/io/taskmigo/authorization/request/persistence/EffectiveStatementResolverTest.java`
  - `server/apps/migration/src/test/java/io/taskmigo/migration/MigrationIntegrationTest.java`

---

### Task 1: Allow only declarative Spring transaction metadata in application code

**Files:**
- Modify: `server/testing/architecture/src/main/java/io/taskmigo/architecture/OnionArchitectureRules.java`
- Modify: `server/testing/architecture/src/test/java/io/taskmigo/architecture/OnionArchitectureRulesTest.java`
- Create the three fixture classes listed in File Structure.

**Interfaces:**
- Consumes: existing `OnionArchitectureRules.Context` and `applicationRule(Context)` behavior.
- Produces: application rule that permits dependencies on `org.springframework.transaction.annotation..` only; all other Spring application dependencies remain rejected.

- [ ] **Step 1: Write failing architecture tests**

Add tests named:
- `shouldAllowTransactionalAnnotationWhenApplicationRuleIsChecked`
- `shouldRejectTransactionTemplateWhenApplicationRuleIsChecked`
- `shouldRejectTransactionalAnnotationWhenDomainRuleIsChecked`

Assertions:
- `ApplicationTransactionalService` passes `applicationRule`.
- `ApplicationTransactionTemplateViolation` fails `applicationRule` with `AssertionError`.
- `DomainTransactionalViolation` fails `domainRule` with `AssertionError`.
- Keep the existing unrelated `ApplicationFrameworkViolation` rejection test.

- [ ] **Step 2: Run the architecture tests and verify RED**

Run:
```bash
cd server
./gradlew --no-daemon :testing:architecture:test --tests io.taskmigo.architecture.OnionArchitectureRulesTest
```
Expected: the new allowed-annotation test fails because the current rule rejects all `org.springframework..` application dependencies.

- [ ] **Step 3: Implement the narrow whitelist**

Change the application dependency rule so application packages may depend on `org.springframework.transaction.annotation..`, but still reject:
- `org.springframework.transaction.support..`;
- `org.springframework.transaction.PlatformTransactionManager` / other programmatic transaction types;
- every unrelated `org.springframework..` package;
- the existing persistence/configuration/executable-app forbidden packages.

Do not weaken `domainRule`, persistence containment, module isolation, or tactical test isolation.

- [ ] **Step 4: Run architecture tests and verify GREEN**

Run the same Gradle command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/testing/architecture
git commit -m "Allow declarative transactions in application code"
```

### Task 2: Migrate Identity to declarative application transactions

**Files:** all Identity files listed in File Structure plus affected Identity application unit tests.

**Interfaces:**
- Consumes: Task 1 application-layer Spring annotation whitelist.
- Produces: Identity application services with direct declarative transaction metadata and no Identity `TransactionRunner` infrastructure.

- [ ] **Step 1: Add a failing Identity architecture guard**

In `IdentityPackageArchitectureTest`, add `shouldRejectLegacyTransactionRunnerWhenIdentityPackagesAreInspected` that rejects production classes in `io.taskmigo.identity.transaction..` and the legacy `io.taskmigo.identity.application.TransactionRunner` type.

- [ ] **Step 2: Run the Identity architecture test and verify RED**

```bash
cd server
./gradlew --no-daemon :modules:identity:test --tests io.taskmigo.identity.IdentityPackageArchitectureTest
```
Expected: FAIL because the runner contract/adapter still exist.

- [ ] **Step 3: Translate every Identity runner scope to annotation metadata**

For each current `transactions.write(...)`, make the externally callable owning use-case method effectively `@Transactional` (`REQUIRED`). For each current `transactions.read(...)`, make the owning public use-case method effectively `@Transactional(readOnly = true)`.

Use a class-level write default on mixed services when all public mutators share ordinary semantics; override query methods with `readOnly=true`. Remove `final` from transactional concrete classes and ensure transactional public methods are not final. Preserve overloaded public methods through effective class/method metadata rather than relying on self-invocation to create a boundary.

Remove runner fields/constructor parameters and unwrap callback bodies into ordinary linear method bodies. Preserve any runtime non-null guard locally only if a concrete flow actually relied on the old runner's `Objects.requireNonNull` behavior.

- [ ] **Step 4: Remove Identity runner wiring and update unit tests**

Delete both Identity runner files. Remove runner parameters from the five Identity application configuration classes. Update affected unit-test constructors and delete inline/direct fake runner fixtures; keep those tests context-free.

- [ ] **Step 5: Run Identity tests**

```bash
cd server
./gradlew --no-daemon :modules:identity:test
```
Expected: PASS.

- [ ] **Step 6: Prove the Spring-managed write boundary still participates in the real cross-context transaction**

Run:
```bash
./gradlew --no-daemon :apps:web:test --tests io.taskmigo.web.AuditTransactionIntegrationTest
```
Expected: PASS, including synchronous audit commit and rollback-on-audit-failure scenarios. These tests fail if the User application mutation is no longer inside an owning Spring transaction.

- [ ] **Step 7: Commit**

```bash
git add server/modules/identity server/apps/web/src/test/java/io/taskmigo/web/AuditTransactionIntegrationTest.java
git commit -m "Use declarative transactions in Identity"
```

### Task 3: Migrate Access Control to declarative application transactions

**Files:** all Access Control files listed in File Structure plus affected application unit tests.

**Interfaces:**
- Consumes: Task 1 whitelist; Identity may call Access Control published services under an existing `REQUIRED` transaction from Task 2.
- Produces: Access Control application services with direct transaction metadata and no Access Control runner package.

- [ ] **Step 1: Add a failing Access Control architecture guard**

Update `AccessControlOnionArchitectureTest` so the context no longer treats `io.taskmigo.authorization.transaction` as an application package and add `shouldRejectLegacyTransactionRunnerWhenAccessControlPackagesAreInspected` rejecting `io.taskmigo.authorization.transaction..`.

- [ ] **Step 2: Verify RED**

```bash
cd server
./gradlew --no-daemon :modules:authorization:test --tests io.taskmigo.authorization.AccessControlOnionArchitectureTest
```
Expected: FAIL while the transaction package still exists.

- [ ] **Step 3: Translate runner-backed services**

Apply the same exact mapping as Identity:
- former `read(...)` scope -> effective `@Transactional(readOnly = true)`;
- former ordinary `write(...)` scope -> effective `@Transactional` with default `REQUIRED`;
- remove callback wrappers, runner fields/constructor arguments, and `final` where proxyability requires it.

Do not alter `JpaEffectiveStatementResolver` or other specialized persistence-level transaction declarations.

- [ ] **Step 4: Remove runner wiring/tests**

Delete the Access Control runner contract and Spring runner. Remove runner arguments from Role/Statement/Subject/Provisioning configuration beans and update affected unit-test construction.

- [ ] **Step 5: Run Access Control tests**

```bash
cd server
./gradlew --no-daemon :modules:authorization:test
```
Expected: PASS.

- [ ] **Step 6: Re-run cross-context integration coverage**

```bash
./gradlew --no-daemon :apps:web:test --tests io.taskmigo.web.AuditTransactionIntegrationTest --tests io.taskmigo.RoleHierarchyIntegrationTest
```
Expected: PASS. Identity and Access Control ordinary services must join one `REQUIRED` transaction; no new `REQUIRES_NEW` boundary is introduced.

- [ ] **Step 7: Commit**

```bash
git add server/modules/authorization
git commit -m "Use declarative transactions in Access Control"
```

### Task 4: Migrate EAV ordinary transactions

**Files:** all EAV files listed in File Structure.

**Interfaces:**
- Consumes: Task 1 whitelist.
- Produces: `EavSchemaServiceImpl` and `EavInstanceServiceImpl` with declarative read/write boundaries and no EAV transaction runner.

- [ ] **Step 1: Add a failing EAV architecture guard**

In `EavOnionArchitectureTest`, add `shouldRejectLegacyTransactionRunnerWhenEavPackagesAreInspected` rejecting `EavTransactionRunner` and `SpringEavTransactionRunner`.

- [ ] **Step 2: Verify RED**

```bash
cd server
./gradlew --no-daemon :modules:eav:test --tests io.taskmigo.eav.EavOnionArchitectureTest
```
Expected: FAIL while the two runner classes exist.

- [ ] **Step 3: Annotate EAV application implementations and remove the runner**

Translate each former read/write callback scope on `EavSchemaServiceImpl` and `EavInstanceServiceImpl` to effective Spring transaction metadata. Make the concrete classes proxyable, remove runner constructor state, update `EavSchemaConfiguration` / `EavValueConfiguration`, then delete `EavTransactionRunner` and `SpringEavTransactionRunner`.

- [ ] **Step 4: Remove unit-test direct runners**

Update `EavSchemaServiceTest` and `EavInstanceServiceTest` to instantiate services without `DirectTransactionRunner`; delete the test helper classes/imports while leaving tests as plain unit tests.

- [ ] **Step 5: Run EAV tests**

```bash
cd server
./gradlew --no-daemon :modules:eav:test
```
Expected: PASS, including existing PostgreSQL-backed repository integration tests.

- [ ] **Step 6: Commit**

```bash
git add server/modules/eav
git commit -m "Use declarative transactions in EAV"
```

### Task 5: Migrate Ticket ordinary transactions

**Files:** all Ticket files listed in File Structure.

**Interfaces:**
- Consumes: Task 1 whitelist and EAV published services from Task 4.
- Produces: Ticket/Ticket Type services with direct transaction metadata and no Ticket transaction runner.

- [ ] **Step 1: Add a failing Ticket architecture guard**

In `TicketOnionArchitectureTest`, add `shouldRejectLegacyTransactionRunnerWhenTicketPackagesAreInspected` rejecting `TicketTransactionRunner` and `SpringTicketTransactionRunner`.

- [ ] **Step 2: Verify RED**

```bash
cd server
./gradlew --no-daemon :modules:ticket:test --tests io.taskmigo.ticket.TicketOnionArchitectureTest
```
Expected: FAIL while runner classes exist.

- [ ] **Step 3: Annotate Ticket application implementations and remove the runner**

Translate `TicketServiceImpl` and `TicketTypeServiceImpl` runner scopes using the same ordinary mapping. Make implementations proxyable, remove runner state from constructors and `TicketConfiguration`, then delete both runner classes.

- [ ] **Step 4: Update unit tests**

Update `TicketServiceTest` and `TicketTypeServiceTest` construction to remove runner fixtures/arguments. Keep unit tests Spring-context-free.

- [ ] **Step 5: Run Ticket tests**

```bash
cd server
./gradlew --no-daemon :modules:ticket:test
```
Expected: PASS, including Ticket persistence/history/concurrency integration coverage.

- [ ] **Step 6: Commit**

```bash
git add server/modules/ticket
git commit -m "Use declarative transactions in Ticket"
```

### Task 6: Migrate Audit while preserving caller-owned `MANDATORY` writes

**Files:** all Audit files listed in File Structure plus `AuditTransactionIntegrationTest`.

**Interfaces:**
- Consumes: Task 1 whitelist and ordinary owning transactions from Identity/Access Control.
- Produces: Audit append/privacy methods with `MANDATORY`, Audit queries with `readOnly=true`, and no Audit runner infrastructure.

- [ ] **Step 1: Add RED architecture and integration assertions**

In `AuditOnionArchitectureTest`, remove `io.taskmigo.audit..transaction..` from the outer configuration package list and add `shouldRejectLegacyTransactionRunnerWhenAuditPackagesAreInspected` rejecting that package / `AuditTransactionRunner`.

In `AuditTransactionIntegrationTest`, add `shouldRequireExistingTransactionWhenAuditPrivacyScrubIsCalledDirectly`, calling the published `AuditPrivacyService` outside a transaction and asserting `IllegalTransactionStateException`.

- [ ] **Step 2: Verify RED before migration**

```bash
cd server
./gradlew --no-daemon :modules:audit:test --tests io.taskmigo.audit.AuditOnionArchitectureTest
```
Expected: FAIL because Audit runner infrastructure still exists.

- [ ] **Step 3: Replace Audit runner semantics directly**

Apply:
- `DefaultAuditAppendService.append(...)` -> `@Transactional(propagation = Propagation.MANDATORY)`;
- `DefaultAuditPrivacyService.scrubUser(...)` -> `@Transactional(propagation = Propagation.MANDATORY)`;
- `DefaultAuditQueryService` query boundary -> `@Transactional(readOnly = true)`.

Make concrete services proxyable, remove runner fields/constructor arguments, update `AuditApplicationConfiguration`, preserve `@ConditionalOnProperty(taskmigo.audit.enabled=true)` behavior, then delete `AuditTransactionRunner` and `SpringAuditTransactionRunner`.

- [ ] **Step 4: Simplify Audit unit tests**

Update `DefaultAuditAppendServiceTest` to construct the service without the direct runner helper. Do not attempt to unit-test Spring propagation there; integration coverage owns that behavior.

- [ ] **Step 5: Run Audit module tests and real transaction integration tests**

```bash
cd server
./gradlew --no-daemon :modules:audit:test
./gradlew --no-daemon :apps:web:test --tests io.taskmigo.web.AuditTransactionIntegrationTest
```
Expected: both PASS. Specifically retain direct-call `MANDATORY`, synchronous commit, and rollback-on-audit-failure behavior.

- [ ] **Step 6: Commit**

```bash
git add server/modules/audit server/apps/web/src/test/java/io/taskmigo/web/AuditTransactionIntegrationTest.java
git commit -m "Use declarative transactions in Audit"
```

### Task 7: Pin specialized transaction boundaries and update server architecture documentation

**Files:**
- Modify: `server/ARCHITECTURE.md`
- Verify targeted specialized files/tests listed in File Structure.

**Interfaces:**
- Consumes: completed ordinary module migrations.
- Produces: repository documentation and regression evidence matching the Spring-native application model without changing specialized transaction mechanics.

- [ ] **Step 1: Verify specialized source semantics before changing docs**

Confirm by source/test inspection that:
- `JpaEffectiveStatementResolver.resolve(...)` still has `readOnly=true`, `Isolation.REPEATABLE_READ`, `Propagation.REQUIRES_NEW`;
- `IdentityEffectiveSubjectResolver` still has behaviorally equivalent read-only transaction handling;
- `MigrationInstaller` still configures SERIALIZABLE programmatic transaction handling/retries and post-commit behavior.

If any changed unintentionally in Tasks 1-6, fix the regression before proceeding; do not redesign them.

- [ ] **Step 2: Run specialized regression tests**

```bash
cd server
./gradlew --no-daemon :modules:authorization:test --tests io.taskmigo.authorization.request.persistence.EffectiveStatementResolverTest
./gradlew --no-daemon :apps:migration:test --tests io.taskmigo.migration.MigrationIntegrationTest
```
Expected: PASS.

- [ ] **Step 3: Update `server/ARCHITECTURE.md`**

Change the Application section and architecture-enforcement section to state:
- domain remains framework-neutral;
- published contracts remain framework-neutral;
- concrete application implementations may use Spring declarative transaction annotations;
- application code still cannot use JPA, Spring Data, configuration/composition, executable apps, or other Spring APIs;
- Foundation must not receive a replacement transaction abstraction;
- specialized programmatic transaction control remains valid when semantics require it.

Remove stale statements that require all ordinary application code to be Spring-neutral or describe transaction runners as the normal inversion mechanism.

- [ ] **Step 4: Commit**

```bash
git add server/ARCHITECTURE.md
git commit -m "Document Spring-native application transactions"
```

### Task 8: Repository-wide cleanup, final verification, and tracker alignment

**Files:** no new production design; only fix incomplete migration findings, then update #170 metadata/body to the implemented scope.

**Interfaces:**
- Consumes: Tasks 1-7.
- Produces: one coherent transaction architecture with no ordinary runner survivors.

- [ ] **Step 1: Scan for incomplete migration artifacts**

Run from repository root:
```bash
rg -n "TransactionRunner|EavTransactionRunner|TicketTransactionRunner|AuditTransactionRunner|Spring.*TransactionRunner" server/modules
rg -n "TransactionTemplate|PlatformTransactionManager|TransactionDefinition" server/modules/*/src/main/java
rg -n "org\.springframework" server/modules/*/src/main/java --glob "**/application/**/*.java"
```

Expected:
- no ordinary runner contracts/adapters survive in Identity, Access Control, EAV, Ticket, or Audit;
- programmatic transaction types appear only in intentional specialized/non-application locations;
- Spring imports in application implementations are limited to `org.springframework.transaction.annotation..`.

- [ ] **Step 2: Run focused module verification sequentially**

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
Expected: PASS.

If Formatting CI later reports a patch artifact, apply the generated `formatting.patch` as a whole per `CONTRIBUTING.md` rather than manually reproducing formatter output.

- [ ] **Step 4: Update GitHub issue #170 to match the shipped architecture**

Rewrite stale wording so #170 describes:
- Spring-native concrete application transaction boundaries;
- removal, not consolidation, of ordinary `*TransactionRunner` infrastructure across Identity, Access Control, EAV, Ticket, and Audit;
- controlled Spring annotation whitelist;
- Audit `MANDATORY` preservation;
- specialized resolver/Migration invariants.

Do not mention `taskmigo/specification` as an implementation dependency for this decision.

- [ ] **Step 5: Commit any final repository-only cleanup**

```bash
git add -A
git commit -m "Complete Spring-native transaction migration"
```

Skip this commit if the scan/verification required no repository changes after Task 7; do not create an empty commit.
