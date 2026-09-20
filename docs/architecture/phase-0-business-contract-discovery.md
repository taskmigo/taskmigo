# Phase 0 - Business contract discovery ledger

## Scope and source of truth

This document was created for Phase 0 of [issue #117](https://github.com/taskmigo/taskmigo/issues/117) and is revalidated as the minimum business-invariant ledger for Phase 0 of [issue #141](https://github.com/taskmigo/taskmigo/issues/141). It records the business behavior that architectural refactors must preserve while separating persistence/framework mechanics that may be replaced.

Current Hexagonal/Onion migration revalidation baseline: `next` at commit 74b83399077479b3177b260d1d48b5e0aae0abb8, the reviewed implementation baseline recorded by issue #141. The last executable-behavior consolidation baseline remains 584c7a806a115137d9320bee2b5120c743c99073 (the merge result of PR #138); the only repository delta from that commit through 74b83399077479b3177b260d1d48b5e0aae0abb8 is the ledger baseline metadata update in PR #139, so no runtime, client, schema, or test behavior changed after the existing safety net was consolidated. The Phase 8 failure/foundation cleanup baseline was e6eff3592793371e6a9849decfcc2cafff2b3815 (the merge result of PR #137), the Phase 0 refresh baseline was 5196391c97b1acda71c6f9cf68200f1232d85aa3 (the merge result of PR #120), and the original discovery baseline was 13c32739893833f266034dc2af9d35f2be55bf66 (the merge result of PR #116). Specification comparison baseline remains taskmigo/specification at 8d9e838f8839eb97ca9aadd30bae1983669f22a6, which is also the current `next` head during this revalidation.

Evidence precedence follows issue #117: current executable behavior and integration/E2E tests; recent merged PR decisions; active PRs targeting next; current API/database/provisioning invariants; then specification/history. When sources disagree, Phase 0 records the conflict instead of freezing one side in a characterization test.

Classifications:

- **CONFIRMED BUSINESS** - preserve the observable rule through later refactors.
- **IMPLEMENTATION DETAIL** - may be replaced if confirmed behavior and required boundaries remain intact.
- **SUPERSEDED** - historical behavior/direction replaced by newer evidence; do not preserve it.
- **UNRESOLVED** - evidence conflicts or the requirement is specification-only; do not choose a side in Phase 0.

## Issue #141 Phase 0 revalidation

The ledger was revalidated against the exact `next` baseline recorded by issue #141 before any Hexagonal/Onion structural work. The result is deliberately conservative:

- All **CONFIRMED BUSINESS** rows below still describe behavior that later phases must preserve.
- All **IMPLEMENTATION DETAIL** rows remain replaceable and MUST NOT become compatibility constraints merely because the current Clean Architecture implementation uses them.
- The resolved historical mismatches remain resolved; no new implementation/specification conflict was discovered.
- M-003 remains **UNRESOLVED** and is not a characterization target for this migration.
- The comparison from the last executable-behavior consolidation commit to the issue #141 baseline contains no executable-code delta, so there is no new behavior to freeze before the structural migration starts.

### Characterization coverage audit

The Phase 1 safety net from PR #119 remains present on the issue #141 baseline and covers the gaps that were identified after the original ledger was written. In particular, the current suite characterizes Request Authorization default deny and deny precedence, Object Authorization filtering before pagination, User normalization/reserved-system behavior, managed deletion, OAuth client secret rotation, and system-User deletion restrictions.

The broader existing regression suite continues to cover hierarchy direction/cycle rollback, assignment replacement and deduplication, registration rollback, bounded effective-state resolution, authentication, filtering/pagination contracts, migration credential idempotency, SERIALIZABLE reconciliation/retry behavior, and post-commit change logging.

No missing characterization test was identified by this revalidation. Phase 0 therefore does not add duplicate tests solely to create a new migration-specific test layer. If a later package/project move exposes an uncharacterized confirmed invariant, that invariant MUST receive the cheapest meaningful characterization test before the affected behavior is moved.

### Architecture decision handoff

The package and dependency conventions for the new architecture are frozen separately in [ADR-001: DDD with Onion and Hexagonal architecture](adr-001-ddd-onion-hexagonal.md). Phase 1 may redesign the physical library/project graph, but it must use that ADR and this ledger together: the ADR defines allowed dependency direction; this ledger defines behavior that may not change.

## Business invariant and behavior matrix

<!-- prettier-ignore -->
| Area | Rule | Classification | Primary evidence |
| --- | --- | --- | --- |
| User | Runtime creation requires non-blank username, firstName, and lastName; accepted values are trimmed. | **CONFIRMED BUSINESS** | DefaultUserService; REST validation |
| User | Username "system" is reserved from normal runtime creation. | **CONFIRMED BUSINESS** | DefaultUserService; SystemUser; provisioning |
| User | Emails are optional, deduplicated, trimmed, lowercased, globally unique, and persisted in normalized form. | **CONFIRMED BUSINESS** | DefaultUserService.normalizeEmails; V1 constraints |
| User | New runtime Users are active and have no interactive password credential by default. | **CONFIRMED BUSINESS** | UserState/UserEntity; authentication adapter |
| User | Registration validates all initial Role/Group references before persistence and is atomic with initial grants/memberships. | **CONFIRMED BUSINESS** | UserRegistrationApplicationService; UserApiIntegrationTest; PR #108 |
| User | Historical `UserStore.UserState`, direct store mutation, and JPA entity shape were implementation details and are no longer the User mutation boundary. | **IMPLEMENTATION DETAIL** | PR #124 |
| Group | Group code is the stable machine-readable identity used by managed reconciliation; code and display name are required and trimmed, display name is independently mutable, description is optional, and child IDs are set-like and must exist. | **CONFIRMED BUSINESS** | DefaultGroupService; GroupApiIntegrationTest; MigrationIntegrationTest |
| Group | Group hierarchy is directed parent-to-child and must remain acyclic, including self-links; rejected mutations leave prior state intact. | **CONFIRMED BUSINESS** | GroupHierarchy; GroupHierarchyIntegrationTest; PR #110 |
| Membership | Membership is set-like and requires an existing Group and User. | **CONFIRMED BUSINESS** | DefaultGroupService.addMember; V1 PK/FKs |
| Membership | Membership in a parent Group contributes authorization from that Group and all reachable child Groups; child membership does not inherit ancestors. | **CONFIRMED BUSINESS** | IdentityEffectiveSubjectResolver; UserApiIntegrationTest |
| Group | Closure tables, hierarchy loading/locking, and closure rebuilds are persistence strategies hidden behind hierarchy ports. | **IMPLEMENTATION DETAIL** | PR #125; Group persistence adapters |
| Role | Role code is the stable machine-readable identity used by managed reconciliation, matches [a-zA-Z0-9_ -]{6,255}, and is globally unique; display name is required and independently mutable. | **CONFIRMED BUSINESS** | AuthorizationName.requiredRole; DefaultAuthorizationProvisioningService; MigrationIntegrationTest; V1 unique constraint |
| Role | Child Role IDs are set-like, must exist, and the parent-to-child Role hierarchy must be acyclic. | **CONFIRMED BUSINESS** | DefaultRoleService; RoleHierarchy; integration tests |
| Role | Effective Roles include a directly assigned Role plus reachable child Roles; ancestors are not inherited from child assignments. | **CONFIRMED BUSINESS** | DefaultRoleService; UserApiIntegrationTest |
| Role | Replacing Role Statements requires every Statement to exist and deduplicates IDs. | **CONFIRMED BUSINESS** | DefaultRoleAuthorizationService; StatementService |
| Role | Closure-table representation and Role hierarchy loading/locking are persistence details hidden behind hierarchy ports. | **IMPLEMENTATION DETAIL** | PR #129; HierarchyClosureWriter |
| Statement | Canonical shape is code, description, effect, scope, target.api.method, target.api.path, and policy. | **CONFIRMED BUSINESS** | REST contract; V1; current spec STMT-001 |
| Statement | Code matches [a-zA-Z0-9_-]{6,255} and is unique; effect/scope/policy are required; policy is non-blank. | **CONFIRMED BUSINESS** | AuthorizationName; StatementPolicyValidator; V1 |
| Statement | Target method is exact or wildcard; runtime method matching is case-sensitive. Target path uses full-match regex semantics against the request path without query string. | **CONFIRMED BUSINESS** | StatementExecutionArtifact.matches; current spec STMT-006 |
| Statement | Persisted updated_at strictly advances and may identify the exact Statement revision for derived-artifact reuse, but is not part of the canonical Statement contract. | **CONFIRMED BUSINESS** | V1 trigger; StatementEntity; PR #91; spec STATE-001 |
| Statement | Create/update enforces canonical structural invariants only; policy compilation, target regex semantics, binding/types, and Object queryability validation are deferred until authorization runtime. | **CONFIRMED BUSINESS** | Phase 4 resolution of M-001/M-005; current spec POLICY-003/STMT-002/STMT-006 |
| Statement | Historical conditions[] representation, RE2/J-only target regex, and cache-invalidation requirements from issue #50 are not current contracts. | **SUPERSEDED** | Current Language policy model, Java Pattern, DB-authoritative resolver; M-002 |
| Subject grants | Direct subject Role/Statement assignment uses replacement semantics; empty input clears the set; referenced IDs must exist. | **CONFIRMED BUSINESS** | DefaultSubjectGrantService; JpaSubjectGrantOperations; API tests |
| Subject grants | Access Control owns Role/Statement/grant state; Identity owns User/Group/membership/hierarchy and exposes opaque effective subjects. | **CONFIRMED BUSINESS** | PR #100; SubjectRef; EffectiveSubjectResolver |
| Effective state | Effective Statements are the deduplicated union of direct subject Statements and Statements reachable through direct/inherited Roles across all effective User/Group subjects. | **CONFIRMED BUSINESS** | DatabaseEffectiveStatementResolver; resolver tests; User API test |
| Effective state | Resolution is DB-authoritative per operation, bounded, avoids unrelated graph loading/N+1 behavior, and cannot depend on cross-request cache correctness. | **CONFIRMED BUSINESS** | EffectiveStatementResolver integration test; spec PERF-001..005 |
| Subject grants | Generic binding-table layout and binding-row UUIDs are persistence details. | **IMPLEMENTATION DETAIL** | V1 subject binding tables; JpaSubjectGrantOperations |
| Request authorization | Default deny: any matching true DENY wins; otherwise any matching true ALLOW grants; otherwise deny. | **CONFIRMED BUSINESS** | RequestAuthorizationService; tests; spec REQ-001 |
| Request authorization | Policy/authorization execution failures and non-Boolean results fail closed. | **CONFIRMED BUSINESS** | RequestAuthorizationService tests; spec TECH-004 |
| Request authorization | Request policy inputs are typed and limited to approved principal/request data: principal ID/username plus request method/path/path variables. | **CONFIRMED BUSINESS** | AuthorizationPrincipal; AuthorizationRequest; AuthorizationSnapshot |
| Request authorization | One operation resolves one effective Statement snapshot; its opaque context is reused by Object Authorization without a second resolution. | **CONFIRMED BUSINESS** | RequestAuthorizationResult; ObjectAuthorizationServiceTest; SNAPSHOT-001 |
| Request authorization | Protected API access requires JWT auth, supported principal_type, and valid user_id; there is no system-user API bypass in the manager. | **CONFIRMED BUSINESS** | RequestAuthorizationManager |
| Request authorization | Servlet request attributes used to reuse the already-computed decision/context are adapter mechanics, not authoritative cross-request caching. | **IMPLEMENTATION DETAIL** | RequestAuthorizationManager |
| Object authorization | Object visibility is ANY(ALLOW) AND NOT ANY(DENY), with default deny. | **CONFIRMED BUSINESS** | ObjectAuthorizationService; tests; spec OBJ-005 |
| Object authorization | Policies partially evaluate known principal/request values while object fields remain symbolic under the supplied ObjectAuthorizationSchema. | **CONFIRMED BUSINESS** | ObjectAuthorizationService; tests; spec OBJ-001/002 |
| Object authorization | Concrete/residual non-Boolean results and unsupported symbolic paths/operators fail closed before unrestricted data access. | **CONFIRMED BUSINESS** | ObjectAuthorizationServiceTest; spec OBJ-001/004 |
| Object authorization | Predicates remain opaque/persistence-neutral; resource adapters own schema-to-database binding. | **CONFIRMED BUSINESS** | ObjectAuthorizationPredicate/binders; PR #114; AUTH-API-004/006 |
| Object authorization | Object Authorization executes in the database before pagination and may not fall back to unrestricted fetch plus JVM filtering. | **CONFIRMED BUSINESS** | User/Group/Role/Statement JPA list adapters; spec OBJ-004 |
| Object authorization | Object-target applicability and policy semantic validation are deferred until a matching Statement participates in authorization; creation/provisioning enforce structural invariants only. | **CONFIRMED BUSINESS** | PR #127; M-001/M-005 resolution |
| Provisioning | Managed Statements, Roles, Groups, and Users reconcile idempotently by stable code/username. Active definitions add or update desired state; `absent: true` deletes existing managed state and is a no-op when that resource is already missing. | **CONFIRMED BUSINESS** | ManagedResourceReconciler; provisioning services; MigrationIntegrationTest |
| Provisioning | Managed deletion runs dependents-first (Users, Groups, Roles, Statements), while active reconciliation runs dependencies-first (Statements, Roles, Groups, Users) so references remain valid. | **CONFIRMED BUSINESS** | ManagedResourceReconciler; MigrationIntegrationTest |
| Provisioning | The system User requires an initial credential on first creation, preserves an existing password hash on later reconciliation, and cannot be removed through managed reconciliation. | **CONFIRMED BUSINESS** | DefaultIdentityProvisioningService; DefaultIdentityProvisioningServiceTest; MigrationIntegrationTest |
| Provisioning | Migration accepts raw User password input, resolves placeholders, hashes the raw value before the Identity boundary, and treats the resulting hash as initial-only: an existing hash is never overwritten by later managed input. | **CONFIRMED BUSINESS** | MigrationResourceLoader; ManagedResourceReconciler; DefaultIdentityProvisioningService |
| Provisioning | Identity and Authorization expose provider-owned provisioning contracts for managed User, Group, Statement, and Role lifecycle; migration consumes those boundaries instead of broad runtime mutation services. | **IMPLEMENTATION DETAIL** | PRs #124/#125/#127/#129; ManagedResourceReconciler |
| Provisioning | Historical persistence-shaped `*Store.*State` mutation surfaces have been removed; managed provisioning reuses the same canonical domain/application command paths as runtime behavior. | **IMPLEMENTATION DETAIL** | PRs #124/#125/#127/#129 |
| Migration | Managed-resource reconciliation and OAuth client reconciliation execute in one SERIALIZABLE transaction with bounded retry for transient/data-integrity failures. | **CONFIRMED BUSINESS** | MigrationRunner; MigrationIntegrationTest |
| Migration | Change events are published only after a successful commit; unchanged resources are not emitted, rolled-back mutations emit no change event, and change events contain resource identity/action rather than credentials. | **CONFIRMED BUSINESS** | MigrationRunner; MigrationChangeLogger; MigrationIntegrationTest |
| Migration | Managed OAuth client secrets use desired-state rotation semantics: an unchanged raw secret preserves the existing encoded value, while a changed raw secret is re-encoded and updates the client. This intentionally differs from initial-only User password semantics. | **CONFIRMED BUSINESS** | InternalRegisteredClientFactory; InternalClientReconciler; MigrationIntegrationTest |
| Migration | Executable/module naming and the concrete OAuth persistence adapter/ownership mechanism are not business semantics. | **IMPLEMENTATION DETAIL** | PR #120; migration composition |
| Authentication | Interactive authentication uses persisted Identity User credentials; missing User or missing password hash cannot authenticate interactively. | **CONFIRMED BUSINESS** | AuthorizationServerConfiguration; InteractiveAuthenticationIntegrationTest |
| Authentication | Non-active persisted Users are disabled in the Spring Security principal. | **CONFIRMED BUSINESS** | AuthenticationInfo.active mapping |
| Authentication | The persisted system account uses the same User model; ROLE_SYSTEM is adapter-level while protected API requests still use normal Statement-based Request Authorization. | **CONFIRMED BUSINESS** | auth integration test; AuthorizationServerConfiguration; RequestAuthorizationManager |
| Authentication | API access tokens expose the principal identity required by Request Authorization; client-credentials tokens associate the system User when available. | **CONFIRMED BUSINESS** | access-token claim customizer |
| Authentication | Spring Security implementation classes, password encoder wiring, and filter-chain composition are adapter details. | **IMPLEMENTATION DETAIL** | web adapter; PR #114/#116 direction |
| Filtering | Missing/blank filterBy means an always-true client filter. | **CONFIRMED BUSINESS** | FilterByCompiler |
| Filtering | Non-blank filterBy compiles against an explicit resource QuerySchema; only registered API-visible paths have query meaning. | **CONFIRMED BUSINESS** | FilteredQueryArgumentResolver; FilterByCompiler; Query spec |
| Filtering | filterBy must resolve to Bool; invalid source/schema usage maps to HTTP 400 INVALID_FILTER. | **CONFIRMED BUSINESS** | FilterByCompiler; ApiV0ExceptionHandler |
| Filtering | Client filter and Object Authorization are AND-composed in persistence before pagination. | **CONFIRMED BUSINESS** | four JPA list adapters; Query QRY-005 |
| Filtering | Compiler/environment caches are implementation details if schema/source identity compatibility remains correct. | **IMPLEMENTATION DETAIL** | FilterByCompiler; Query PERF-002 |
| Pagination | v0 offset pagination is one-based with defaults page=1 and pageSize=20; both minimum 1; maximum page size 100. | **CONFIRMED BUSINESS** | OffsetPageRequest; API integration tests |
| Pagination | Metadata exposes type offset, current page, page size, total matching items, and total pages. | **CONFIRMED BUSINESS** | ApiResponse.OffsetPagination; API tests |
| Pagination | User/Group/Role/Statement lists use deterministic ID ordering before offset pagination. | **CONFIRMED BUSINESS** | four JPA list adapters |
| Pagination | Filtering/Object Authorization affects rows and totals before page slicing; hidden/unmatched rows must not inflate pagination metadata. | **CONFIRMED BUSINESS** | DB specifications; current Query/Authorization specs |
| Pagination | JavaBean accessor shape on OffsetPageRequest is a transport implementation detail. | **IMPLEMENTATION DETAIL** | PR #96 |
| Errors | Domain/application failures use semantic INVALID_INPUT, NOT_FOUND, and CONFLICT categories; HTTP vocabulary is adapter-owned. | **CONFIRMED BUSINESS** | DomainFailureType; bounded-context exceptions |
| Errors | v0 maps INVALID_INPUT/NOT_FOUND/CONFLICT to HTTP 400/404/409 while preserving stable DOMAIN_BAD_REQUEST, DOMAIN_NOT_FOUND, and DOMAIN_CONFLICT error codes. | **CONFIRMED BUSINESS** | DomainExceptionHandler |
| Errors | Bean validation maps to HTTP 422 VALIDATION_ERROR; malformed body maps to HTTP 400 MALFORMED_REQUEST. | **CONFIRMED BUSINESS** | ApiV0ExceptionHandler |
| Errors | Invalid filterBy maps to HTTP 400 INVALID_FILTER; authenticated access denial maps to HTTP 403 FORBIDDEN. | **CONFIRMED BUSINESS** | ApiV0ExceptionHandler |
| Errors | Unexpected exceptions are redacted behind HTTP 500 INTERNAL_ERROR; raw internal details are not public contract. | **CONFIRMED BUSINESS** | ApiV0ExceptionHandler.handleUnexpected |
| Errors | Exact human-readable exception strings are diagnostics unless a business rule specifically depends on the reason. | **IMPLEMENTATION DETAIL** | stable machine categories/codes are the stronger boundary |

## Mismatch ledger

<!-- prettier-ignore -->
| ID | Conflict | Classification | Phase 0 disposition |
| --- | --- | --- | --- |
| M-001 | Baseline Statement creation/provisioning compiled and validated policy plus Object-target applicability while POLICY-003/STMT-002 required runtime semantic validation. | **SUPERSEDED by Phase 4** | PR #127 resolved the conflict in favor of structural validation at mutation time and deferred semantic validation at authorization runtime. |
| M-002 | Historical issue #50 freezes conditions[], RE2/J-only regex, and cache-invalidation assumptions; current implementation/spec use one Language policy, Java Pattern, DB-authoritative state each operation, and only derived-artifact reuse. | **SUPERSEDED**, except shared security invariants | Keep default deny, deny precedence, fail closed, no bypass, DB-before-pagination, and deterministic effective-state behavior; do not restore old representation/engine/cache contracts. |
| M-003 | Current Authorization spec requires persisted Request/Object Authorization Logs and GET /api/v0/authorization/logs, but baseline next has no corresponding persistence/runtime/API implementation. | **UNRESOLVED** | Treat as specification-only planned behavior, not a Phase 0 characterization target or existing contract. |
| M-004 | PR #120 replaced bootstrap with migration and changed OAuth persistence/composition details, but executable names and concrete persistence adapters are not business semantics. | **IMPLEMENTATION DETAIL** | Preserve the confirmed atomic reconciliation, credential lifecycle, deletion, and V1 data invariants while allowing composition/module names and adapters to change. |
| M-005 | Historical provisioning reused Statement semantic validation and therefore shared M-001's timing conflict. | **SUPERSEDED by Phase 4** | PR #127 made provisioning reuse the canonical structural command path and deferred semantic validation consistently. |
| M-006 | Managed Group reconciliation historically lived on the broad runtime GroupService while other managed resources used provisioning contracts. | **SUPERSEDED by Phase 3** | PR #125 moved managed Group lifecycle behind the Identity-owned `GroupProvisioningService` while preserving reconciliation semantics. |

## Evidence index

Original Phase 0 executable evidence (`13c3273`):

- [V1 database schema](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/database/src/main/resources/db/migration/V1__schema.sql)
- [DefaultUserService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/identity/src/main/java/io/taskmigo/identity/user/internal/DefaultUserService.java) and [UserRegistrationApplicationService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/identity/src/main/java/io/taskmigo/identity/user/UserRegistrationApplicationService.java)
- [DefaultGroupService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/identity/src/main/java/io/taskmigo/identity/group/internal/DefaultGroupService.java) and [GroupHierarchy](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/identity/src/main/java/io/taskmigo/identity/group/hierarchy/GroupHierarchy.java)
- [DefaultRoleService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/role/internal/DefaultRoleService.java), [DefaultSubjectGrantService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/subject/internal/DefaultSubjectGrantService.java), and [StatementPolicyValidator](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/statement/StatementPolicyValidator.java)
- [DatabaseEffectiveStatementResolver](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/persistence/request/DatabaseEffectiveStatementResolver.java), [RequestAuthorizationService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/request/RequestAuthorizationService.java), and [ObjectAuthorizationService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/request/ObjectAuthorizationService.java)
- [FilterByCompiler](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/query/src/main/java/io/taskmigo/query/FilterByCompiler.java), [OffsetPageRequest](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/web/src/main/java/io/taskmigo/rest/api/v0/support/pagination/OffsetPageRequest.java), and [ApiV0ExceptionHandler](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/web/src/main/java/io/taskmigo/rest/api/v0/support/response/ApiV0ExceptionHandler.java)
- [BootstrapIntegrationTest](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/bootstrap/src/test/java/io/taskmigo/bootstrap/BootstrapIntegrationTest.java), [UserApiIntegrationTest](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/web/src/test/java/io/taskmigo/rest/api/v0/auth/user/UserApiIntegrationTest.java), and [GroupHierarchyIntegrationTest](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/web/src/test/java/io/taskmigo/GroupHierarchyIntegrationTest.java)

PR #120 delta executable evidence (`5196391`):

- [ManagedResourceReconciler](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/apps/migration/src/main/java/io/taskmigo/migration/ManagedResourceReconciler.java), [MigrationRunner](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/apps/migration/src/main/java/io/taskmigo/migration/MigrationRunner.java), and [MigrationIntegrationTest](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/apps/migration/src/test/java/io/taskmigo/migration/MigrationIntegrationTest.java)
- [MigrationResourceLoader](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/apps/migration/src/main/java/io/taskmigo/migration/MigrationResourceLoader.java), [DefaultIdentityProvisioningService](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/modules/identity/src/main/java/io/taskmigo/identity/provisioning/internal/DefaultIdentityProvisioningService.java), and [DefaultIdentityProvisioningServiceTest](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/modules/identity/src/test/java/io/taskmigo/identity/provisioning/internal/DefaultIdentityProvisioningServiceTest.java)
- [DefaultGroupService](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/modules/identity/src/main/java/io/taskmigo/identity/group/internal/DefaultGroupService.java) and [DefaultAuthorizationProvisioningService](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/modules/authorization/src/main/java/io/taskmigo/authorization/provisioning/internal/DefaultAuthorizationProvisioningService.java)
- [InternalRegisteredClientFactory](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/apps/migration/src/main/java/io/taskmigo/migration/InternalRegisteredClientFactory.java) and [InternalClientReconciler](https://github.com/taskmigo/taskmigo/blob/5196391c97b1acda71c6f9cf68200f1232d85aa3/server/apps/migration/src/main/java/io/taskmigo/migration/InternalClientReconciler.java)

Decision history and active direction:

- [PR #100](https://github.com/taskmigo/taskmigo/pull/100): Access Control bounded-context ownership.
- [PR #108](https://github.com/taskmigo/taskmigo/pull/108): application-owned transaction/cross-service orchestration.
- [PR #110](https://github.com/taskmigo/taskmigo/pull/110): Group hierarchy rules moved into the domain.
- [PR #111](https://github.com/taskmigo/taskmigo/pull/111): application services separated from JPA adapters.
- [PR #112](https://github.com/taskmigo/taskmigo/pull/112): dedicated provisioning contracts.
- [PR #114](https://github.com/taskmigo/taskmigo/pull/114), [#115](https://github.com/taskmigo/taskmigo/pull/115), and [#116](https://github.com/taskmigo/taskmigo/pull/116): framework-neutral contracts and enforced module/package boundaries.
- [PR #96](https://github.com/taskmigo/taskmigo/pull/96): pagination HTTP behavior preserved while Java accessor mechanics changed.
- [PR #120](https://github.com/taskmigo/taskmigo/pull/120): merged migration-managed-resource, credential, deletion, transaction, and OAuth reconciliation behavior captured by the delta above.
- [PR #119](https://github.com/taskmigo/taskmigo/pull/119): merged Phase 1 behavioral safety net used by the subsequent vertical slices.
- Historical [PR #99](https://github.com/taskmigo/taskmigo/pull/99): older migration/OAuth persistence direction superseded by the merged #120 behavior.
- Historical [issue #50](https://github.com/taskmigo/taskmigo/issues/50): supporting evidence only where it agrees with newer executable contracts.
- Current Authorization and Query Filtering specification snapshot at [taskmigo/specification@8d9e838](https://github.com/taskmigo/specification/tree/8d9e838f8839eb97ca9aadd30bae1983669f22a6).

## Guardrails for Phase 1+

1. Add characterization tests only for **CONFIRMED BUSINESS** rows. Resolve **UNRESOLVED** rows explicitly first.
2. Replace **IMPLEMENTATION DETAIL** mechanisms freely, including anemic state surrogates, graph-wide loading, closure rebuild strategy, JPA entity shape, servlet request attributes, and migration executable naming.
3. Do not reintroduce **SUPERSEDED** representation/engine/cache contracts through compatibility code.
4. Preserve atomic registration/provisioning workflows, dependency-safe managed deletion/reconciliation, initial-only User credentials, rotatable managed OAuth client secrets, post-commit-only change events, hierarchy direction and acyclicity, replacement grant semantics, DB-authoritative authorization resolution, fail-closed decisions, DB-side filtering/Object Authorization, deterministic pagination, and stable domain/API error categories.
5. Treat M-001/M-005/M-006 as resolved historical mismatches and preserve the decisions merged in #127 and #125. Resolve M-003 as an explicit product/specification scope decision before implementing Authorization Logs.
