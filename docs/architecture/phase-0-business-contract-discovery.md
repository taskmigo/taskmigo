# Phase 0 - Business contract discovery ledger

## Scope and source of truth

This document implements Phase 0 of [issue #117](https://github.com/taskmigo/taskmigo/issues/117). It records the business behavior that later Clean Architecture and rich-domain refactors must preserve, while separating persistence/framework mechanics that may be replaced.

Discovery baseline: next at commit 13c32739893833f266034dc2af9d35f2be55bf66 (the merge result of PR #116). Specification comparison baseline: taskmigo/specification at 8d9e838f8839eb97ca9aadd30bae1983669f22a6.

Evidence precedence follows issue #117: current executable behavior and integration/E2E tests; recent merged PR decisions; active PRs targeting next; current API/database/provisioning invariants; then specification/history. When sources disagree, Phase 0 records the conflict instead of freezing one side in a characterization test.

Classifications:

- **CONFIRMED BUSINESS** - preserve the observable rule through later refactors.
- **IMPLEMENTATION DETAIL** - may be replaced if confirmed behavior and required boundaries remain intact.
- **SUPERSEDED** - historical behavior/direction replaced by newer evidence; do not preserve it.
- **UNRESOLVED** - evidence conflicts or the requirement is specification-only; do not choose a side in Phase 0.

## Business invariant and behavior matrix

<!-- prettier-ignore -->
| Area | Rule | Classification | Primary evidence |
| --- | --- | --- | --- |
| User | Runtime creation requires non-blank username, firstName, and lastName; accepted values are trimmed. | **CONFIRMED BUSINESS** | DefaultUserService; REST validation |
| User | Username "system" is reserved from normal runtime creation. | **CONFIRMED BUSINESS** | DefaultUserService; SystemUser; provisioning |
| User | Emails are optional, deduplicated, trimmed, lowercased, globally unique, and persisted in normalized form. | **CONFIRMED BUSINESS** | DefaultUserService.normalizeEmails; V1 constraints |
| User | New runtime Users are active and have no interactive password credential by default. | **CONFIRMED BUSINESS** | UserState/UserEntity; authentication adapter |
| User | Registration validates all initial Role/Group references before persistence and is atomic with initial grants/memberships. | **CONFIRMED BUSINESS** | UserRegistrationApplicationService; UserApiIntegrationTest; PR #108 |
| User | UserStore.UserState, direct store mutation, and JPA entity shape are not the User business model. | **IMPLEMENTATION DETAIL** | PR #111; issue #117 target |
| Group | Group code and display name are required and trimmed; description is optional; child IDs are set-like and must exist. | **CONFIRMED BUSINESS** | DefaultGroupService; GroupApiIntegrationTest |
| Group | Group hierarchy is directed parent-to-child and must remain acyclic, including self-links; rejected mutations leave prior state intact. | **CONFIRMED BUSINESS** | GroupHierarchy; GroupHierarchyIntegrationTest; PR #110 |
| Membership | Membership is set-like and requires an existing Group and User. | **CONFIRMED BUSINESS** | DefaultGroupService.addMember; V1 PK/FKs |
| Membership | Membership in a parent Group contributes authorization from that Group and all reachable child Groups; child membership does not inherit ancestors. | **CONFIRMED BUSINESS** | IdentityEffectiveSubjectResolver; UserApiIntegrationTest |
| Group | Closure tables, full-graph loading, pessimistic whole-graph locks, and closure rebuilds are persistence strategies. | **IMPLEMENTATION DETAIL** | GroupStore/JpaGroupOperations; issue #117 |
| Role | Role code matches [a-zA-Z0-9_ -]{6,255} and is globally unique; display name is required. | **CONFIRMED BUSINESS** | AuthorizationName.requiredRole; V1 unique constraint |
| Role | Child Role IDs are set-like, must exist, and the parent-to-child Role hierarchy must be acyclic. | **CONFIRMED BUSINESS** | DefaultRoleService; RoleHierarchy; integration tests |
| Role | Effective Roles include a directly assigned Role plus reachable child Roles; ancestors are not inherited from child assignments. | **CONFIRMED BUSINESS** | DefaultRoleService; UserApiIntegrationTest |
| Role | Replacing Role Statements requires every Statement to exist and deduplicates IDs. | **CONFIRMED BUSINESS** | DefaultRoleAuthorizationService; StatementService |
| Role | Closure-table representation and full Role graph loading/locking are persistence details. | **IMPLEMENTATION DETAIL** | RoleStore; HierarchyClosureWriter |
| Statement | Canonical shape is code, description, effect, scope, target.api.method, target.api.path, and policy. | **CONFIRMED BUSINESS** | REST contract; V1; current spec STMT-001 |
| Statement | Code matches [a-zA-Z0-9_-]{6,255} and is unique; effect/scope/policy are required; policy is non-blank. | **CONFIRMED BUSINESS** | AuthorizationName; StatementPolicyValidator; V1 |
| Statement | Target method is exact or wildcard; runtime method matching is case-sensitive. Target path uses full-match regex semantics against the request path without query string. | **CONFIRMED BUSINESS** | StatementExecutionArtifact.matches; current spec STMT-006 |
| Statement | Persisted updated_at strictly advances and may identify the exact Statement revision for derived-artifact reuse, but is not part of the canonical Statement contract. | **CONFIRMED BUSINESS** | V1 trigger; StatementEntity; PR #91; spec STATE-001 |
| Statement | Create/update-time semantic validation versus deferred runtime semantic validation is disputed. | **UNRESOLVED** | Baseline StatementPolicyValidator/API tests vs current spec POLICY-003/STMT-002; M-001 |
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
| Object authorization | Object target applicability validation during Statement creation is part of the disputed validation-timing contract. | **UNRESOLVED** | StatementPolicyValidator vs current spec POLICY-003; M-001 |
| Provisioning | Managed Statements/Roles/Users reconcile idempotently by stable business identity and update existing managed state in place. | **CONFIRMED BUSINESS** | provisioning services; MigrationIntegrationTest |
| Provisioning | The system User is provisioning-owned, requires an initial credential on first creation, and later reconciliation preserves an existing password hash. | **CONFIRMED BUSINESS** | DefaultIdentityProvisioningService; migration tests |
| Provisioning | Built-in authorization and identity data is reconciled in dependency order so references are valid: Statements, Roles, Groups, then Users/grants. | **CONFIRMED BUSINESS** | AuthorizationStatementReconciler |
| Provisioning | Provisioning has separate bounded-context contracts from normal runtime User/Role/Statement services. | **CONFIRMED BUSINESS** | merged PR #112 |
| Provisioning | Direct use of UserStore/RoleStore/StatementStore and duplicated normalization are intermediate architecture. | **IMPLEMENTATION DETAIL** | baseline provisioning code; issue #117 Phase 8 |
| Migration | Executable/module naming and OAuth persistence ownership are not business semantics. | **IMPLEMENTATION DETAIL** | active PR #99 |
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
| Errors | Domain failures use transport-neutral BAD_REQUEST, NOT_FOUND, and CONFLICT categories. | **CONFIRMED BUSINESS** | DomainFailureType; bounded-context exceptions |
| Errors | v0 maps those categories to HTTP 400/404/409 and stable DOMAIN_BAD_REQUEST, DOMAIN_NOT_FOUND, and DOMAIN_CONFLICT error codes. | **CONFIRMED BUSINESS** | DomainExceptionHandler |
| Errors | Bean validation maps to HTTP 422 VALIDATION_ERROR; malformed body maps to HTTP 400 MALFORMED_REQUEST. | **CONFIRMED BUSINESS** | ApiV0ExceptionHandler |
| Errors | Invalid filterBy maps to HTTP 400 INVALID_FILTER; authenticated access denial maps to HTTP 403 FORBIDDEN. | **CONFIRMED BUSINESS** | ApiV0ExceptionHandler |
| Errors | Unexpected exceptions are redacted behind HTTP 500 INTERNAL_ERROR; raw internal details are not public contract. | **CONFIRMED BUSINESS** | ApiV0ExceptionHandler.handleUnexpected |
| Errors | Exact human-readable exception strings are diagnostics unless a business rule specifically depends on the reason. | **IMPLEMENTATION DETAIL** | stable machine categories/codes are the stronger boundary |

## Mismatch ledger

<!-- prettier-ignore -->
| ID | Conflict | Classification | Phase 0 disposition |
| --- | --- | --- | --- |
| M-001 | Baseline Statement creation/provisioning compiles and validates policy plus Object-target applicability; current spec POLICY-003/STMT-002 requires semantic validation only when a matching Statement participates in authorization. | **UNRESOLVED** | Do not add tests choosing either timing. Resolve explicitly before the Statement aggregate slice. |
| M-002 | Historical issue #50 freezes conditions[], RE2/J-only regex, and cache-invalidation assumptions; current implementation/spec use one Language policy, Java Pattern, DB-authoritative state each operation, and only derived-artifact reuse. | **SUPERSEDED**, except shared security invariants | Keep default deny, deny precedence, fail closed, no bypass, DB-before-pagination, and deterministic effective-state behavior; do not restore old representation/engine/cache contracts. |
| M-003 | Current Authorization spec requires persisted Request/Object Authorization Logs and GET /api/v0/authorization/logs, but baseline next has no corresponding persistence/runtime/API implementation. | **UNRESOLVED** | Treat as specification-only planned behavior, not a Phase 0 characterization target or existing contract. |
| M-004 | Installation executable naming is migration; OAuth persistence ownership remains an implementation detail. | **IMPLEMENTATION DETAIL** | Preserve reconciliation, system credential, and V1 invariants; allow composition/module names and adapters to change. |
| M-005 | Provisioning reuses StatementPolicyValidator, so M-001 also affects built-in reconciliation. | **UNRESOLVED** | Do not freeze migration-time semantic validation until M-001 is resolved. |

## Evidence index

Baseline executable evidence:

- [V1 database schema](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/database/src/main/resources/db/migration/V1__schema.sql)
- [DefaultUserService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/identity/src/main/java/io/taskmigo/identity/user/internal/DefaultUserService.java) and [UserRegistrationApplicationService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/identity/src/main/java/io/taskmigo/identity/user/UserRegistrationApplicationService.java)
- [DefaultGroupService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/identity/src/main/java/io/taskmigo/identity/group/internal/DefaultGroupService.java) and [GroupHierarchy](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/identity/src/main/java/io/taskmigo/identity/group/hierarchy/GroupHierarchy.java)
- [DefaultRoleService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/role/internal/DefaultRoleService.java), [DefaultSubjectGrantService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/subject/internal/DefaultSubjectGrantService.java), and [StatementPolicyValidator](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/statement/StatementPolicyValidator.java)
- [DatabaseEffectiveStatementResolver](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/persistence/request/DatabaseEffectiveStatementResolver.java), [RequestAuthorizationService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/request/RequestAuthorizationService.java), and [ObjectAuthorizationService](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/authorization/src/main/java/io/taskmigo/authorization/request/ObjectAuthorizationService.java)
- [FilterByCompiler](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/modules/query/src/main/java/io/taskmigo/query/FilterByCompiler.java), [OffsetPageRequest](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/web/src/main/java/io/taskmigo/rest/api/v0/support/pagination/OffsetPageRequest.java), and [ApiV0ExceptionHandler](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/web/src/main/java/io/taskmigo/rest/api/v0/support/response/ApiV0ExceptionHandler.java)
- [MigrationIntegrationTest](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/migration/src/test/java/io/taskmigo/migration/MigrationIntegrationTest.java), [UserApiIntegrationTest](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/web/src/test/java/io/taskmigo/rest/api/v0/auth/user/UserApiIntegrationTest.java), and [GroupHierarchyIntegrationTest](https://github.com/taskmigo/taskmigo/blob/13c32739893833f266034dc2af9d35f2be55bf66/server/apps/web/src/test/java/io/taskmigo/GroupHierarchyIntegrationTest.java)

Decision history and active direction:

- [PR #100](https://github.com/taskmigo/taskmigo/pull/100): Access Control bounded-context ownership.
- [PR #108](https://github.com/taskmigo/taskmigo/pull/108): application-owned transaction/cross-service orchestration.
- [PR #110](https://github.com/taskmigo/taskmigo/pull/110): Group hierarchy rules moved into the domain.
- [PR #111](https://github.com/taskmigo/taskmigo/pull/111): application services separated from JPA adapters.
- [PR #112](https://github.com/taskmigo/taskmigo/pull/112): dedicated provisioning contracts.
- [PR #114](https://github.com/taskmigo/taskmigo/pull/114), [#115](https://github.com/taskmigo/taskmigo/pull/115), and [#116](https://github.com/taskmigo/taskmigo/pull/116): framework-neutral contracts and enforced module/package boundaries.
- [PR #96](https://github.com/taskmigo/taskmigo/pull/96): pagination HTTP behavior preserved while Java accessor mechanics changed.
- Active [PR #98](https://github.com/taskmigo/taskmigo/pull/98): intended data-driven black-box authorization coverage without production behavior changes.
- Active [PR #99](https://github.com/taskmigo/taskmigo/pull/99): intended migration/OAuth persistence composition changes; direction only until merged.
- Historical [issue #50](https://github.com/taskmigo/taskmigo/issues/50): supporting evidence only where it agrees with newer executable contracts.
- Current Authorization and Query Filtering specification snapshot at [taskmigo/specification@8d9e838](https://github.com/taskmigo/specification/tree/8d9e838f8839eb97ca9aadd30bae1983669f22a6).

## Guardrails for Phase 1+

1. Add characterization tests only for **CONFIRMED BUSINESS** rows. Resolve **UNRESOLVED** rows explicitly first.
2. Replace **IMPLEMENTATION DETAIL** mechanisms freely, including anemic state surrogates, graph-wide loading, closure rebuild strategy, JPA entity shape, servlet request attributes, and migration executable naming.
3. Do not reintroduce **SUPERSEDED** representation/engine/cache contracts through compatibility code.
4. Preserve atomic registration/provisioning workflows, hierarchy direction and acyclicity, replacement grant semantics, DB-authoritative authorization resolution, fail-closed decisions, DB-side filtering/Object Authorization, deterministic pagination, and stable domain/API error categories.
5. Resolve M-001/M-005 before the Statement aggregate vertical slice. Resolve M-003 as an explicit product/specification scope decision before implementing Authorization Logs.
