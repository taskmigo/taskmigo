# Spring-Native Application Transactions Design

## Status

Approved architectural direction for #170. The written design is committed for final review before implementation planning.

This design treats the Taskmigo server as Spring-native at the application-runtime layer. It intentionally does not use `taskmigo/specification` as a constraint for this decision.

## Decision

Taskmigo will remove the application-owned `*TransactionRunner` abstraction pattern from reusable server modules and express application transaction boundaries directly with Spring transaction annotations.

The architectural boundary becomes:

```text
domain
  pure Java business model and domain policy
  no Spring dependency

application contracts
  use-case interfaces and published contracts
  no Spring dependency

application implementations
  orchestration and transaction boundaries
  may use Spring transaction annotations only

persistence / integration / configuration
  full framework-specific implementation as required
```

The key policy is:

> Domain remains framework-neutral. Application implementations may depend on Spring's declarative transaction annotations because Taskmigo's server runtime is intentionally Spring-native.

Taskmigo will not create a shared replacement `TransactionRunner`. Removing duplicated runners means removing the abstraction, not relocating it to `foundation`.

## Motivation

The current codebase has accumulated multiple transaction-runner abstractions whose primary purpose is to hide Spring transaction mechanics from application services.

Current examples include:

- Identity `TransactionRunner` + `SpringTransactionRunner`;
- Access Control `TransactionRunner` + `SpringTransactionRunner`;
- EAV `EavTransactionRunner` + `SpringEavTransactionRunner`;
- Ticket `TicketTransactionRunner` + `SpringTicketTransactionRunner`;
- Audit `AuditTransactionRunner` + `SpringAuditTransactionRunner`.

Identity, Access Control, EAV, and Ticket all implement substantially the same ordinary read/write behavior with `TransactionTemplate`. Audit uses the same pattern with a meaningful variant: read-only queries plus `MANDATORY` write participation for synchronous audit changes.

The runner pattern creates several costs:

- duplicated interfaces and Spring adapters;
- transaction plumbing in application constructors;
- callback wrappers around otherwise linear use-case orchestration;
- test-only direct/fake transaction runners that do not test real transaction behavior;
- framework-neutrality at the application implementation layer despite the server being intentionally Spring-native;
- an extra abstraction that does not represent domain language or provide meaningful framework portability.

The abstraction also does not eliminate the need to understand Spring semantics. Propagation, isolation, read-only behavior, proxy boundaries, rollback, and specialized transactions still have to be reasoned about at each use case.

## Goals

- Remove duplicated `*TransactionRunner` contracts and Spring runner implementations from ordinary application flows.
- Make transaction semantics visible at the application use-case implementation that owns the orchestration.
- Preserve domain independence from Spring.
- Preserve published application contracts as framework-neutral interfaces.
- Preserve current rollback, propagation, read-only, isolation, and cross-context atomicity behavior.
- Keep specialized transaction semantics explicit rather than forcing them through a generic abstraction.
- Reduce constructor dependencies and unit-test plumbing.
- Enforce controlled Spring coupling mechanically so allowing `@Transactional` does not turn application code into unrestricted framework code.

## Non-goals

- Do not move JPA, Spring Data, HTTP, servlet, Spring Security, or persistence implementation APIs into application code.
- Do not add Spring annotations to domain packages.
- Do not add Spring transaction annotations to published application API interfaces merely to make proxying easier.
- Do not replace the runners with custom transaction annotations, a transaction DSL, a shared Unit of Work, or a new foundation transaction API.
- Do not introduce AspectJ transaction weaving.
- Do not globally change proxy mode merely to preserve `final` application classes.
- Do not redesign persistence repositories or business behavior.
- Do not rewrite Migration's SERIALIZABLE/retry transaction orchestration as part of this change.

## Scope

The implementation should cover the current reusable modules that own application transaction-runner abstractions:

- Identity;
- Access Control;
- Audit;
- EAV;
- Ticket.

This scope is broader than the original Identity/Access Control wording of #170 because the same architectural pattern now exists in additional modules. Leaving those runners behind would preserve two competing transaction architectures after this decision.

Migration remains outside this runner-removal scope because its installation flow owns materially different SERIALIZABLE/retry behavior and currently uses programmatic Spring transaction mechanics directly.

## Target architecture

### Domain layer

Domain code remains strictly framework-neutral.

It must not depend on:

- `org.springframework..`;
- `jakarta.persistence..`;
- application configuration;
- persistence implementations;
- executable applications.

This is the strongest framework-independence boundary and remains unchanged.

### Published application contracts

Published use-case interfaces remain framework-neutral.

For example, a contract such as `GroupService`, `AuditAppendService`, or `TicketService` must not need Spring annotations in order to describe what the application can do.

Transaction metadata belongs to the implementation of a use case, not to the cross-module/public contract.

### Application implementations

Concrete application-service implementations may use only Spring's declarative transaction metadata:

```text
org.springframework.transaction.annotation.Transactional
org.springframework.transaction.annotation.Propagation
org.springframework.transaction.annotation.Isolation
```

Application implementations must not use:

```text
PlatformTransactionManager
TransactionTemplate
TransactionDefinition
Spring Data
JPA / ORM APIs
Spring MVC / Web
Spring Security
Spring application context lookups
```

The allowed Spring dependency is deliberately narrow: transaction declaration, not general framework ownership.

## Transaction declaration strategy

### Ordinary mixed read/write services

For an application service with both mutations and queries, prefer a write transaction as the class default and override query methods as read-only:

```java
@Transactional
class DefaultGroupService implements GroupService {

    @Transactional(readOnly = true)
    @Override
    public OffsetPage<GroupInfo> list(...) {
        return groups.list(...);
    }

    @Override
    public UUID create(...) {
        ...
    }
}
```

Default Spring propagation is `REQUIRED`, matching the ordinary runner behavior currently implemented with default `TransactionTemplate` instances.

Class-level defaulting is preferred when it accurately describes the service because it avoids silently missing an overloaded or newly added mutating public method.

### Read-only services

A service whose public use cases are all queries may use class-level read-only metadata:

```java
@Transactional(readOnly = true)
class DefaultSomethingQueryService implements SomethingQueryService {
    ...
}
```

### Method-specific semantics

Use method-level metadata when a method materially differs from its class default.

Do not introduce custom annotations such as `@ReadUseCase` or `@WriteUseCase`. Spring's transaction metadata is the selected runtime contract.

## Proxyability

Transaction-bearing application implementations must be explicitly proxyable under normal Spring proxy-based transaction management.

The implementation must not rely on an implicit global JDK-proxy setting or AspectJ weaving.

Therefore:

- remove `final` from concrete application classes that carry `@Transactional` when class-based proxying may be used;
- do not make transactional methods `final`;
- continue wiring services through normal Spring bean creation;
- do not change published service interfaces only to satisfy transaction proxying.

Application services currently created through `@Bean` configuration remain valid Spring-managed beans and can receive transactional advice after becoming proxyable.

## Self-invocation rule

A method must not depend on a self-call to establish different transaction semantics.

For example, this is invalid as a transaction boundary design:

```java
public void outer() {
    this.requiresNewInner();
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
void requiresNewInner() {
    ...
}
```

The self-call does not cross the Spring proxy boundary.

The migration should follow these rules:

- every externally callable use-case method that needs an ordinary transaction receives effective transaction metadata itself, directly or through its class default;
- overloaded public methods must all have effective transaction metadata, so one overload calling another does not accidentally lose the boundary;
- a genuinely different nested transaction boundary must live on a separate Spring-managed collaborator rather than relying on self-invocation.

## Boundary placement

The target transaction boundary is the externally invoked application use case, not an inner persistence operation.

Today some methods perform pure validation or normalization immediately before entering `transactions.write(...)`. Moving the transaction to the public use-case method may begin the transaction slightly earlier and include those pure checks.

That broadening is intentional when all of the following hold:

- the pre-transaction work has no external side effect;
- business behavior and thrown errors remain unchanged;
- no additional independent transaction is created;
- rollback semantics are unchanged.

If a current method contains multiple intentionally independent transaction scopes, implementation must preserve those scopes explicitly rather than collapse them into one annotation. Such a case should use a separate proxied collaborator or remain programmatic only when declarative Spring transactions cannot represent the existing behavior cleanly.

## Module-specific transaction mapping

### Identity

Remove:

- `io.taskmigo.identity.application.TransactionRunner`;
- Identity `SpringTransactionRunner`;
- all constructor parameters/fields used only for the runner.

Replace current `transactions.read(...)` and `transactions.write(...)` ownership with transaction metadata on the corresponding concrete application service methods/classes.

Cross-context calls from Identity into published Access Control services continue to participate in the same physical transaction under `REQUIRED` propagation.

### Access Control

Remove:

- `io.taskmigo.authorization.transaction.TransactionRunner`;
- Access Control `SpringTransactionRunner`;
- runner wiring and constructor dependencies.

Ordinary Role, Statement, Subject Grant, provisioning, and related application operations declare their transaction semantics directly.

### EAV

Remove:

- `EavTransactionRunner`;
- `SpringEavTransactionRunner`;
- direct/fake transaction runners used only by application unit tests.

`EavSchemaServiceImpl` and `EavInstanceServiceImpl` declare ordinary read/write transaction semantics directly.

### Ticket

Remove:

- `TicketTransactionRunner`;
- `SpringTicketTransactionRunner`;
- runner wiring from Ticket configuration and application constructors.

Ticket and Ticket Type application implementations declare their transaction semantics directly.

### Audit

Audit has a specialized but declarative mapping.

Current synchronous append/privacy writes require an already active caller-owned transaction. Preserve that with:

```java
@Transactional(propagation = Propagation.MANDATORY)
```

on the relevant concrete application methods.

Current Audit queries map to:

```java
@Transactional(readOnly = true)
```

Remove `AuditTransactionRunner` and `SpringAuditTransactionRunner` after those semantics are represented directly.

The existing `taskmigo.audit.enabled` conditional wiring remains unchanged; removing the runner must not cause Audit services or persistence beans to become active when Audit is disabled.

## Specialized transaction boundaries

Not every `@Transactional` in the codebase should be normalized to the ordinary application policy.

### Effective authorization resolution

`JpaEffectiveStatementResolver` currently owns a specialized persistence consistency boundary:

```java
@Transactional(
    readOnly = true,
    isolation = Isolation.REPEATABLE_READ,
    propagation = Propagation.REQUIRES_NEW
)
```

Preserve it as-is unless implementation evidence proves a behavior-equivalent simplification. It is not an ordinary application `REQUIRED` transaction.

### Identity effective-subject resolution

`IdentityEffectiveSubjectResolver` currently uses read-only transaction semantics at the integration boundary. Preserve behaviorally equivalent read-only transaction handling. This design does not require moving that boundary into an application service.

### Migration

`MigrationInstaller` owns SERIALIZABLE transaction execution and retry behavior. Preserve its programmatic transaction handling and post-commit behavior.

The rule is not "all TransactionTemplate usage is forbidden." The rule is "ordinary application transactions do not need a custom runner abstraction." Programmatic transaction control remains valid where the runtime behavior genuinely requires it.

## Cross-context atomicity

Default `@Transactional` application methods use `Propagation.REQUIRED`.

Therefore a call chain such as:

```text
Identity application use case
    -> Access Control application service
    -> repositories in both contexts
```

must use one physical transaction when invoked through Spring-managed service proxies.

The migration must prove that failures in the downstream context roll back participating writes in both contexts.

Do not add `REQUIRES_NEW` to ordinary application services to make annotations appear locally self-contained. Independent transactions are allowed only where the existing semantics intentionally require them.

## Rollback and error behavior

Ordinary runner callbacks currently accept `Supplier`/`Runnable`, so application work uses unchecked exception propagation. Spring's default declarative rollback policy for runtime exceptions and errors is behaviorally aligned with those ordinary flows.

The migration must not catch and convert exceptions merely to make transaction annotations work.

If inventory finds a method whose current runner usage depends on non-default rollback behavior, that method must declare the required `rollbackFor`/`noRollbackFor` semantics explicitly or retain a dedicated boundary. Do not change rollback semantics silently.

Current `Objects.requireNonNull` guards inside runner implementations are infrastructure artifacts, not a reason to keep the runners. Application contracts that promise non-null results continue to enforce that contract through existing nullness/static checks and local validation where required. If any concrete flow currently relies on the runner's runtime null rejection, preserve that guard at the owning use case rather than introducing a replacement global runner.

## Architecture enforcement

The shared `OnionArchitectureRules` currently treats all `org.springframework..` dependencies from application packages as forbidden. Update that rule to encode the new boundary precisely.

After this change:

- domain packages still reject all Spring dependencies;
- application packages may depend on `org.springframework.transaction.annotation..` only;
- application packages still reject every other Spring package;
- application packages explicitly reject programmatic transaction mechanics such as `org.springframework.transaction.support..` and `org.springframework.transaction.PlatformTransactionManager`;
- persistence/JPA containment rules remain unchanged;
- executable-application dependency rules remain unchanged.

Add regression fixtures/tests proving both sides of the whitelist:

- an application class using `@Transactional` is accepted;
- an application class using `TransactionTemplate` is rejected;
- an application class using an unrelated Spring annotation/API is rejected;
- a domain class using `@Transactional` is rejected.

Published contract-specific architecture rules remain framework-neutral and must not be weakened merely because application implementations can use transaction metadata.

## Testing strategy

### Application unit tests

Remove direct/fake transaction-runner fixtures that existed only to execute callbacks inline.

Application unit tests continue to instantiate service implementations directly. In that mode `@Transactional` is inert, which is expected: those tests verify orchestration and business behavior, not Spring transaction mechanics.

Do not add Spring test contexts to ordinary application unit tests solely because the production class carries `@Transactional`.

### Transaction integration tests

Transaction behavior must be verified through Spring-managed beans and the real database boundary.

At minimum cover:

- ordinary write method starts/participates in a `REQUIRED` transaction;
- ordinary read method executes with read-only transaction semantics;
- nested ordinary application-service calls join the existing transaction;
- downstream failure rolls back cross-context participating writes;
- Audit append/privacy fails without an active transaction and joins an active caller transaction;
- specialized `JpaEffectiveStatementResolver` semantics remain `REQUIRES_NEW + REPEATABLE_READ + readOnly`;
- Migration SERIALIZABLE/retry/post-commit behavior remains unchanged.

Prefer behavior/integration assertions over tests that merely reflect on annotation presence.

### Architecture tests

Update shared architecture-rule tests first so the intended coupling policy is executable and cannot drift.

Context-specific Onion/architecture tests must continue to pass after runner packages/classes are removed.

## Implementation shape

The implementation should be a focused architecture migration, not five unrelated module refactors.

Recommended order:

1. Update shared Onion architecture enforcement to allow only Spring transaction annotations in application implementations, with regression fixtures.
2. Migrate one representative ordinary module and prove proxy/transaction behavior with integration coverage.
3. Migrate the remaining ordinary runner modules using the same pattern: Identity, Access Control, EAV, Ticket.
4. Migrate Audit with explicit `MANDATORY` write semantics and read-only query semantics.
5. Delete obsolete runner contracts, Spring runner implementations, runner wiring, and unit-test fakes.
6. Re-scan server production code for remaining application transaction-runner abstractions and classify any survivor as either an intentional specialized boundary or an incomplete migration.
7. Verify specialized resolver and Migration transaction invariants.
8. Update repository architecture documentation and #170 so they describe the Spring-native application transaction model rather than the removed runner architecture.

## Acceptance criteria

- No ordinary application `TransactionRunner`, `*TransactionRunner`, or equivalent callback transaction abstraction remains in Identity, Access Control, EAV, Ticket, or Audit.
- No ordinary `Spring*TransactionRunner` adapter remains for those modules.
- Concrete application implementations express ordinary transaction boundaries with Spring `@Transactional` metadata.
- Published application contracts remain free of Spring transaction annotations.
- Domain packages remain completely Spring-neutral.
- Application packages cannot use Spring APIs except `org.springframework.transaction.annotation..`.
- Ordinary writes preserve `REQUIRED` propagation and rollback behavior.
- Ordinary reads preserve `REQUIRED + readOnly` behavior.
- Cross-context participating writes remain atomic.
- Audit synchronous append/privacy writes preserve `MANDATORY` participation semantics.
- `JpaEffectiveStatementResolver` preserves `REQUIRES_NEW + REPEATABLE_READ + readOnly` semantics.
- `IdentityEffectiveSubjectResolver` preserves behaviorally equivalent read-only semantics.
- Migration SERIALIZABLE/retry/post-commit behavior is unchanged.
- Application unit tests no longer need direct/fake runner implementations.
- Architecture tests enforce the controlled Spring transaction-annotation whitelist.
- Required server verification and CI checks pass.

## Impact on #170

#170 should be rewritten from "consolidate duplicated Spring transaction-runner infrastructure" to a Spring-native application transaction migration.

The issue should no longer preserve context-owned runner interfaces or propose a shared runner implementation. Its target outcome is removal of the runner pattern wherever it represents ordinary application transaction execution, while preserving the specialized transaction cases documented above.
